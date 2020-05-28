package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager;

import com.atlassian.confluence.security.login.LoginInfo;
import com.atlassian.confluence.security.login.LoginManager;
import com.atlassian.confluence.status.service.SystemInformationService;
import com.atlassian.confluence.status.service.systeminfo.UsageInfo;
import com.atlassian.confluence.user.UserAccessor;
import com.atlassian.confluence.util.GeneralUtil;
import com.atlassian.confluence.util.UserChecker;
import com.atlassian.user.User;
import net.sf.hibernate.HibernateException;
import net.sf.hibernate.Session;
import net.sf.hibernate.SessionFactory;
import net.sf.hibernate.Transaction;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import javax.annotation.Nonnull;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.Thread.MIN_PRIORITY;
import static java.util.concurrent.Executors.defaultThreadFactory;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class ScheduledMetricEvaluatorImpl implements ScheduledMetricEvaluator, DisposableBean, InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(ScheduledMetricEvaluator.class);

    private static final String ATTACHMENT_SQL = "SELECT sum(LONGVAL) FROM CONTENTPROPERTIES cp JOIN CONTENT c ON cp.CONTENTID = c.CONTENTID WHERE c.CONTENTTYPE = 'ATTACHMENT' AND cp.PROPERTYNAME = 'FILESIZE'";
    private static final String ATTACHMENT_SQL_OLD = "SELECT sum(FILESIZE) FROM ATTACHMENTS";
    private static final String PAGE_SQL = "SELECT count(CONTENTID) FROM CONTENT WHERE CONTENTTYPE = 'PAGE' AND PREVVER IS NULL AND CONTENT_STATUS = 'current'";
    private static final String BLOGPOST_SQL = "SELECT count(CONTENTID) FROM CONTENT WHERE CONTENTTYPE = 'BLOGPOST' AND PREVVER IS NULL AND CONTENT_STATUS = 'current'";

    private static final String MISSED_ATTACHMENT_TABLE_VERSION = "5.7.0";

    private final ScrapingSettingsManager scrapingSettingsManager;
    private final SessionFactory sessionFactory;
    private final LoginManager loginManager;
    private final UserAccessor userAccessor;
    private final UserChecker userChecker;
    private final SystemInformationService systemInformationService;

    /**
     * Scheduled executor to grab metrics.
     */
    private final ScheduledExecutorService executorService;
    private final Lock lock;

    private final AtomicLong totalAttachmentSize;
    private final AtomicInteger totalPages;
    private final AtomicInteger totalBlogPosts;
    private final AtomicInteger totalUsers;
    private final AtomicInteger totalOneHourAgoActiveUsers;
    private final AtomicInteger totalTodayActiveUsers;
    private final AtomicInteger totalCurrentContent;
    private final AtomicInteger totalGlobalSpaces;
    private final AtomicInteger totalPersonalSpaces;

    private final AtomicLong lastExecutionTimestamp;

    private ScheduledFuture<?> scraper;

    public ScheduledMetricEvaluatorImpl(
            ScrapingSettingsManager scrapingSettingsManager,
            SessionFactory sessionFactory,
            LoginManager loginManager,
            UserAccessor userAccessor,
            UserChecker userChecker,
            SystemInformationService systemInformationService) {
        this.scrapingSettingsManager = scrapingSettingsManager;
        this.sessionFactory = sessionFactory;
        this.loginManager = loginManager;
        this.userAccessor = userAccessor;
        this.userChecker = userChecker;
        this.systemInformationService = systemInformationService;
        this.totalAttachmentSize = new AtomicLong(0);
        this.totalPages = new AtomicInteger(0);
        this.totalBlogPosts = new AtomicInteger(0);
        this.totalUsers = new AtomicInteger(0);
        this.totalOneHourAgoActiveUsers = new AtomicInteger(0);
        this.totalTodayActiveUsers = new AtomicInteger(0);
        this.totalCurrentContent = new AtomicInteger(0);
        this.totalGlobalSpaces = new AtomicInteger(0);
        this.totalPersonalSpaces = new AtomicInteger(0);
        this.lastExecutionTimestamp = new AtomicLong(-1);
        this.executorService = newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(@Nonnull Runnable r) {
                Thread thread = defaultThreadFactory().newThread(r);
                thread.setPriority(MIN_PRIORITY);
                return thread;
            }
        });
        this.lock = new ReentrantLock();
    }

    @Override
    public long getTotalAttachmentSize() {
        return totalAttachmentSize.get();
    }

    @Override
    public int getTotalPages() {
        return totalPages.get();
    }

    @Override
    public int getTotalBlogPosts() {
        return totalBlogPosts.get();
    }

    @Override
    public int getTotalUsers() {
        return totalUsers.get();
    }

    @Override
    public int getTotalOneHourAgoActiveUsers() {
        return totalOneHourAgoActiveUsers.get();
    }

    @Override
    public int getTotalTodayActiveUsers() {
        return totalTodayActiveUsers.get();
    }

    @Override
    public long getLastExecutionTimestamp() {
        return lastExecutionTimestamp.get();
    }

    @Override
    public int getTotalCurrentContent() {
        return totalCurrentContent.get();
    }

    @Override
    public int getTotalGlobalSpaces() {
        return totalGlobalSpaces.get();
    }

    @Override
    public int getTotalPersonalSpaces() {
        return totalPersonalSpaces.get();
    }

    @Override
    public void restartScraping(final int newDelay) {
        lock.lock();
        try{
            stopScraping();
            startScraping(newDelay);
        } finally {
            lock.unlock();
        }
    }

    private void stopScraping() {
        boolean success = scraper.cancel(true);
        if (!success){
            log.debug("Unable to cancel scraping, typically because it has already completed.");
        }
    }

    @Override
    public void afterPropertiesSet() {
        lock.lock();
        try {
            startScraping(scrapingSettingsManager.getDelay());
        } finally{
            lock.unlock();
        }
    }

    @Override
    public void destroy() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    private void startScraping(int delay) {
        if (delay <= 0) {
            return;
        }

        scraper = executorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                calculateTotalUsers();
                calculateTotalAttachmentSize();
                calculateSessions();
                calculateUsageInfo();
                lastExecutionTimestamp.set(System.currentTimeMillis());
            }
        }, 0, delay, TimeUnit.MINUTES);
    }

    private void calculateSessions() {
        int oneHourAgoActiveUserCount = 0;
        int todayActiveUserCount = 0;
        try {
            for (User user : userAccessor.getUsers()) {
                LoginInfo loginInfo = loginManager.getLoginInfo(user);
                if (loginInfo == null) {
                    continue;
                }

                Date lastSuccessfulLoginDate = loginInfo.getLastSuccessfulLoginDate();
                if (lastSuccessfulLoginDate == null) {
                    continue;
                }

                long lastSuccessfulLoginTs = lastSuccessfulLoginDate.getTime();
                long oneHourAgo = System.currentTimeMillis() - 3600 * 1000;
                long today = DateUtils.truncate(new Date(), Calendar.DAY_OF_MONTH).getTime();
                if (lastSuccessfulLoginTs >= oneHourAgo) {
                    oneHourAgoActiveUserCount++;
                }
                if (lastSuccessfulLoginTs >= today) {
                    todayActiveUserCount++;
                }
            }
        } catch (Exception ex) {
            log.error("Error calculate user sessions", ex);
        }
        totalOneHourAgoActiveUsers.set(oneHourAgoActiveUserCount);
        totalTodayActiveUsers.set(todayActiveUserCount);
    }

    private void calculateTotalUsers() {
        try {
            Integer numberOfRegisteredUsers = userChecker.getNumberOfRegisteredUsers();
            if (numberOfRegisteredUsers.intValue() != -1) {
                totalUsers.set(numberOfRegisteredUsers);
            } else {
                log.warn("userChecker.getNumberOfRegisteredUsers() returned -1");
            }
        } catch (Throwable th) {
            log.error("Cannot get list users with access", th);
        }
    }

    /**
     * Calculate usage information.
     */
    private void calculateUsageInfo() {
        try {
            UsageInfo usageInfo = systemInformationService.getUsageInfo();
            if (usageInfo != null) {
                totalCurrentContent.set(usageInfo.getCurrentContent());
                totalGlobalSpaces.set(usageInfo.getGlobalSpaces());
                totalPersonalSpaces.set(usageInfo.getPersonalSpaces());
            }
        } catch (Exception ex) {
            log.error("Error read usage info", ex);
        }
    }

    private void calculateTotalAttachmentSize() {
        Session session = null;
        Transaction transaction = null;
        try {
            session = sessionFactory.openSession();
            transaction = session.beginTransaction();
            try (Statement statement = session.connection().createStatement()) {
                try (ResultSet rs = statement.executeQuery(ATTACHMENT_SQL)) {
                    if (rs.next()) {
                        Long value = rs.getLong(1);
                        if (rs.wasNull() && MISSED_ATTACHMENT_TABLE_VERSION.compareTo(GeneralUtil.getVersionNumber()) > 0) {
                            try (ResultSet rs2 = statement.executeQuery(ATTACHMENT_SQL_OLD)) {
                                if (rs2.next()) {
                                    totalAttachmentSize.set(rs2.getLong(1));
                                }
                            }
                        } else {
                            totalAttachmentSize.set(value);
                        }
                    }
                }
                try (ResultSet rs = statement.executeQuery(PAGE_SQL)) {
                    if (rs.next()) {
                        totalPages.set(rs.getInt(1));
                    }
                }
                try (ResultSet rs = statement.executeQuery(BLOGPOST_SQL)) {
                    if (rs.next()) {
                        totalBlogPosts.set(rs.getInt(1));
                    }
                }
            }
        } catch (Throwable th) {
            if (transaction != null) {
                try {
                    transaction.rollback();
                } catch (HibernateException ex) {
                    log.error("Cannot rollback hibernate transaction", ex);
                }
            }
            log.error("Error execute SQL", th);
        } finally {
            if (transaction != null) {
                try {
                    transaction.commit();
                } catch (HibernateException ex) {
                    log.error("Error commit hibernate transaction", ex);
                }
            }

            if (session != null) {
                try {
                    session.close();
                } catch (HibernateException ex) {
                    log.error("Cannot close hibernate session", ex);
                }
            }
        }
    }
}
