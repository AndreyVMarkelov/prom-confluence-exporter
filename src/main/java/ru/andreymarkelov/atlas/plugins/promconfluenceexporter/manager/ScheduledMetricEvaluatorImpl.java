package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager;

import com.atlassian.confluence.security.login.LoginManager;
import com.atlassian.confluence.user.UserAccessor;
import net.sf.hibernate.HibernateException;
import net.sf.hibernate.Session;
import net.sf.hibernate.SessionFactory;
import net.sf.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.sql.ResultSet;
import java.sql.Statement;
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

    private final PluginSettings pluginSettings;

    private static final String ATTACHMENT_SQL = "SELECT sum(LONGVAL) FROM contentproperties cp JOIN content c ON cp.contentid = c.contentid WHERE c.contenttype = 'ATTACHMENT' AND cp.propertyname = 'FILESIZE'";

    private final SessionFactory sessionFactory;
    private final LoginManager loginManager;
    private final UserAccessor userAccessor;

    private final AtomicLong totalAttachmentSize;
    private final AtomicInteger totalUsers;
    private final AtomicLong lastExecutionTimestamp;

    /**
     * Scheduled executor to grab metrics.
     */
    private ScheduledExecutorService executorService;

    private ScheduledFuture<?> scraper;
    private final Lock lock;

    public ScheduledMetricEvaluatorImpl(
            PluginSettingsFactory pluginSettingsFactory,
            SessionFactory sessionFactory,
            LoginManager loginManager,
            UserAccessor userAccessor) {
        this.pluginSettings = pluginSettingsFactory.createSettingsForKey("PLUGIN_PROMETHEUS_FOR_CONFLUENCE");
        this.sessionFactory = sessionFactory;
        this.loginManager = loginManager;
        this.userAccessor = userAccessor;
        this.totalAttachmentSize = new AtomicLong(0);
        this.totalUsers = new AtomicInteger(0);
        this.lastExecutionTimestamp = new AtomicLong(-1);

        this.executorService = newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
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
    public int getTotalUsers() {
        return totalUsers.get();
    }

    @Override
    public long getLastExecutionTimestamp() {
        return lastExecutionTimestamp.get();
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

    @Override
    public int getDelay() {
        String storedValue = (String) pluginSettings.get("delay");
        return storedValue != null ? Integer.parseInt(storedValue) : 1;
    }

    @Override
    public void setDelay(final int delay) {
        pluginSettings.put("delay", String.valueOf(delay));
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

    private void startScraping(int delay){
        scraper = executorService.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                calculateTotalUsers();
                calculateTotalAttachmentSize();
                lastExecutionTimestamp.set(System.currentTimeMillis());
                calculate24HourSessions();
                calculate1HourSessions();
            }
        }, 0, delay, TimeUnit.MINUTES);
    }

    private void calculate24HourSessions() {

    }

    private void calculate1HourSessions() {

    }

    private void calculateTotalUsers() {
        try {
            totalUsers.set(userAccessor.countLicenseConsumingUsers());
        } catch (Throwable th) {
            log.error("Cannot get list users with access", th);
        }
    }

    private void calculateTotalAttachmentSize() {
        Session session = null;
        Transaction transaction = null;
        try {
            session = sessionFactory.openSession();
            transaction = session.beginTransaction();
            try (Statement statement = session.connection().createStatement(); ResultSet rs = statement.executeQuery(ATTACHMENT_SQL)) {
                if (rs.next()) {
                    totalAttachmentSize.set(rs.getLong(1));
                }
            }
            transaction.commit();
        } catch (Throwable th) {
            if (transaction != null) {
                try {
                    transaction.rollback();
                } catch (HibernateException ex) {
                    log.error("Cannot rollback hibernate transaction", ex);
                }
            }
            log.error("Error get attachment size from SQL", th);
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
