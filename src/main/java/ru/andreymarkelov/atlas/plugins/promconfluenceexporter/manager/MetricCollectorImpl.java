package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletException;

import com.atlassian.confluence.cluster.ClusterManager;
import com.atlassian.confluence.license.LicenseService;
import com.atlassian.confluence.license.exception.LicenseException;
import com.atlassian.core.task.ErrorQueuedTaskQueue;
import com.atlassian.core.task.MultiQueueTaskManager;
import com.atlassian.extras.api.confluence.ConfluenceLicense;
import io.prometheus.client.Collector;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.hotspot.DefaultExports;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import ru.andreymarkelov.atlas.plugins.promconfluenceexporter.util.ExceptionRunnable;

import static java.util.Collections.emptyList;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class MetricCollectorImpl extends Collector implements MetricCollector, DisposableBean, InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(MetricCollectorImpl.class);

    private final ClusterManager clusterManager;
    private final LicenseService licenseService;
    private final ScheduledMetricEvaluator scheduledMetricEvaluator;
    private final CollectorRegistry registry;
    private final MultiQueueTaskManager taskManager;

    public MetricCollectorImpl(
            ClusterManager clusterManager,
            LicenseService licenseService,
            ScheduledMetricEvaluator scheduledMetricEvaluator,
            MultiQueueTaskManager taskManager) {
        this.clusterManager = clusterManager;
        this.licenseService = licenseService;
        this.scheduledMetricEvaluator = scheduledMetricEvaluator;
        this.taskManager = taskManager;
        this.registry = CollectorRegistry.defaultRegistry;
    }

    private final Gauge maintenanceExpiryDaysGauge = Gauge.build()
            .name("confluence_maintenance_expiry_days_gauge")
            .help("Maintenance Expiry Days Gauge")
            .create();

    private final Gauge activeUsersGauge = Gauge.build()
            .name("confluence_active_users_gauge")
            .help("Active Users Gauge")
            .create();

    private final Gauge totalOneHourAgoActiveUsersGauge = Gauge.build()
            .name("confluence_one_hour_active_users_gauge")
            .help("Users Login One Hour Ago Gauge")
            .create();

    private final Gauge totalTodayActiveUsers = Gauge.build()
            .name("confluence_today_active_users_gauge")
            .help("Users Login Today Gauge")
            .create();

    private final Gauge allowedUsersGauge = Gauge.build()
            .name("confluence_allowed_users_gauge")
            .help("Allowed Users Gauge")
            .create();

    private final Gauge totalAttachmentSizeGauge = Gauge.build()
            .name("confluence_total_attachment_size_gauge")
            .help("Total Attachments Size Gauge")
            .create();

    private final Histogram requestDurationOnPath = Histogram.build()
            .name("confluence_request_duration_on_path")
            .help("Request duration on path")
            .labelNames("path")
            .create();

    private final Gauge totalCurrentContentGauge = Gauge.build()
            .name("confluence_current_contents_gauge")
            .help("Current Contents Gauge")
            .create();

    private final Gauge totalGlobalSpacesGauge = Gauge.build()
            .name("confluence_global_spaces_gauge")
            .help("Global Spaces Gauge")
            .create();

    private final Gauge totalPersonalSpacesGauge = Gauge.build()
            .name("confluence_personal_spaces_gauge")
            .help("Personal Spaces Gauge")
            .create();

    private final Gauge totalPagesGauge = Gauge.build()
            .name("confluence_pages_gauge")
            .help("Total Pages Gauge")
            .create();

    private final Gauge totalBlogPostsGauge = Gauge.build()
            .name("confluence_blogposts_gauge")
            .help("Total BlogPosts Gauge")
            .create();

    //--> Cluster

    private final Gauge totalClusterNodeGauge = Gauge.build()
            .name("confluence_total_cluster_nodes_gauge")
            .help("Total Cluster Nodes Gauge")
            .create();

    private final Counter clusterPanicCounter = Counter.build()
            .name("confluence_cluster_panic_count")
            .help("Cluster Panic Count")
            .create();

    //--> Labels

    private final Counter labelCreateCounter = Counter.build()
            .name("confluence_label_create_count")
            .help("Label Create Count")
            .labelNames("visibility", "prefix")
            .create();

    private final Counter labelAddCounter = Counter.build()
            .name("confluence_label_add_count")
            .help("Label Add Count")
            .labelNames("visibility", "prefix", "source", "spaceKey")
            .create();

    private final Counter labelRemoveCounter = Counter.build()
            .name("confluence_label_remove_count")
            .help("Label Remove Count")
            .labelNames("visibility", "prefix", "source", "spaceKey")
            .create();

    private final Counter labelDeleteCounter = Counter.build()
            .name("confluence_label_delete_count")
            .help("Label Delete Count")
            .labelNames("visibility", "prefix")
            .create();

    //--> Login/Logout

    private final Counter userLogoutCounter = Counter.build()
            .name("confluence_user_logout_count")
            .help("User Logout Count")
            .labelNames("username", "ip")
            .create();

    private final Counter userLoginCounter = Counter.build()
            .name("confluence_user_login_count")
            .help("User Login Count")
            .labelNames("username", "ip")
            .create();

    private final Counter userLoginFailedCounter = Counter.build()
            .name("confluence_user_failed_login_count")
            .help("User Failed Login Count")
            .labelNames("username", "ip")
            .create();

    //--> Space

    private final Counter spaceCreateCounter = Counter.build()
            .name("confluence_space_create_count")
            .help("Space Create Count")
            .labelNames("username")
            .create();

    private final Counter spaceDeleteCounter = Counter.build()
            .name("confluence_space_delete_count")
            .help("Space Delete Count")
            .labelNames("username")
            .create();

    //--> Mails

    private final Gauge totalMailQueueGauge = Gauge.build()
            .name("confluence_mail_queue_gauge")
            .help("Current Mail Queue Gauge")
            .create();

    private final Gauge totalMailQueueErrorsGauge = Gauge.build()
            .name("confluence_mail_queue_errors_gauge")
            .help("Current Mail Queue Errors Gauge")
            .create();

    //--> JMX

    private final Gauge jvmUptimeGauge = Gauge.build()
            .name("jvm_uptime_gauge")
            .help("JVM Uptime Gauge")
            .create();

    @Override
    public void requestDuration(String path, ExceptionRunnable runnable) throws IOException, ServletException {
        Histogram.Timer pathTimer = isNotBlank(path) ? requestDurationOnPath.labels(path).startTimer() : null;
        try {
            runnable.run();
        } finally {
            if (pathTimer != null) {
                pathTimer.observeDuration();
            }
        }
    }

    @Override
    public void clusterPanicCounter() {
        clusterPanicCounter.inc();
    }

    //--> Labels

    @Override
    public void labelCreateCounter(String visibility, String prefix) {
        labelCreateCounter.labels(visibility, prefix).inc();
    }

    @Override
    public void labelRemoveCounter(String visibility, String prefix, String source, String spaceKey) {
        labelRemoveCounter.labels(visibility, prefix, source, spaceKey).inc();
    }

    @Override
    public void labelAddCounter(String visibility, String prefix, String source, String spaceKey) {
        labelAddCounter.labels(visibility, prefix, source, spaceKey).inc();
    }

    @Override
    public void labelDeleteCounter(String visibility, String prefix) {
        labelDeleteCounter.labels(visibility, prefix).inc();
    }

    //--> Login/Logout

    @Override
    public void userLoginCounter(String username, String ip) {
        userLoginCounter.labels(username, ip).inc();
    }

    @Override
    public void userLogoutCounter(String username, String ip) {
        userLogoutCounter.labels(username, ip).inc();
    }

    @Override
    public void userLoginFailedCounter(String username, String ip) {
        userLoginFailedCounter.labels(username, ip).inc();
    }

    //--> Space


    @Override
    public void spaceCreateCounter(String username) {
        spaceCreateCounter.labels(username).inc();
    }

    @Override
    public void spaceDeleteCounter(String username) {
        spaceDeleteCounter.labels(username).inc();
    }

    //--> Collect

    private List<MetricFamilySamples> collectInternal() {
        // resolve cluster metrics
        totalClusterNodeGauge.set(clusterManager.getClusterInformation().getMemberCount());

        // license
        try {
            ConfluenceLicense confluenceLicense = licenseService.retrieve();
            maintenanceExpiryDaysGauge.set(confluenceLicense.getNumberOfDaysBeforeMaintenanceExpiry());
            allowedUsersGauge.set(confluenceLicense.getMaximumNumberOfUsers());
        } catch (LicenseException ex) {
            log.error("Cannot retrieve license", ex);
        }

        // users
        activeUsersGauge.set(scheduledMetricEvaluator.getTotalUsers());
        totalOneHourAgoActiveUsersGauge.set(scheduledMetricEvaluator.getTotalOneHourAgoActiveUsers());
        totalTodayActiveUsers.set(scheduledMetricEvaluator.getTotalTodayActiveUsers());

        // usage
        totalCurrentContentGauge.set(scheduledMetricEvaluator.getTotalCurrentContent());
        totalGlobalSpacesGauge.set(scheduledMetricEvaluator.getTotalGlobalSpaces());
        totalPersonalSpacesGauge.set(scheduledMetricEvaluator.getTotalPersonalSpaces());
        totalPagesGauge.set(scheduledMetricEvaluator.getTotalPages());
        totalBlogPostsGauge.set(scheduledMetricEvaluator.getTotalBlogPosts());

        // attachment size
        totalAttachmentSizeGauge.set(scheduledMetricEvaluator.getTotalAttachmentSize());

        // mail queue
        ErrorQueuedTaskQueue mailQueue = (ErrorQueuedTaskQueue) taskManager.getTaskQueue("mail");
        totalMailQueueGauge.set(mailQueue.size());
        totalMailQueueErrorsGauge.set(mailQueue.getErrorQueue().size());

        // jvm uptime
        jvmUptimeGauge.set(ManagementFactory.getRuntimeMXBean().getUptime());

        List<MetricFamilySamples> result = new ArrayList<>();
        result.addAll(clusterPanicCounter.collect());
        result.addAll(labelCreateCounter.collect());
        result.addAll(labelRemoveCounter.collect());
        result.addAll(labelAddCounter.collect());
        result.addAll(labelDeleteCounter.collect());
        result.addAll(userLoginCounter.collect());
        result.addAll(userLogoutCounter.collect());
        result.addAll(userLoginFailedCounter.collect());
        result.addAll(requestDurationOnPath.collect());
        result.addAll(totalClusterNodeGauge.collect());
        result.addAll(maintenanceExpiryDaysGauge.collect());
        result.addAll(allowedUsersGauge.collect());
        result.addAll(activeUsersGauge.collect());
        result.addAll(totalCurrentContentGauge.collect());
        result.addAll(totalGlobalSpacesGauge.collect());
        result.addAll(totalPersonalSpacesGauge.collect());
        result.addAll(totalOneHourAgoActiveUsersGauge.collect());
        result.addAll(totalTodayActiveUsers.collect());
        result.addAll(totalAttachmentSizeGauge.collect());
        result.addAll(totalPagesGauge.collect());
        result.addAll(totalBlogPostsGauge.collect());
        result.addAll(totalMailQueueGauge.collect());
        result.addAll(totalMailQueueErrorsGauge.collect());
        result.addAll(jvmUptimeGauge.collect());
        return result;
    }

    @Override
    public List<MetricFamilySamples> collect() {
        long start = System.currentTimeMillis();
        try {
            return collectInternal();
        } catch (Throwable throwable) {
            log.error("Error collect prometheus metrics", throwable);
            return emptyList();
        } finally {
            log.debug("Collect execution time is: {}ms", System.currentTimeMillis() - start);
        }
    }

    @Override
    public void destroy() {
        this.registry.unregister(this);
    }

    @Override
    public void afterPropertiesSet() {
        this.registry.register(this);
        DefaultExports.initialize();
    }

    @Override
    public CollectorRegistry getRegistry() {
        return registry;
    }
}
