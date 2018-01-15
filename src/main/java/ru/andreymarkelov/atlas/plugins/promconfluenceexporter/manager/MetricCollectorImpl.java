package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager;

import com.atlassian.confluence.cluster.ClusterManager;
import com.atlassian.confluence.license.LicenseWebFacade;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.status.service.SystemInformationService;
import com.atlassian.confluence.user.UserAccessor;
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

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class MetricCollectorImpl extends Collector implements MetricCollector, DisposableBean, InitializingBean {
    private static final Logger log = LoggerFactory.getLogger(MetricCollectorImpl.class);

    private final CollectorRegistry registry;
    private final PageManager pageManager;
    private final SpaceManager spaceManager;
    private final ClusterManager clusterManager;
    private final UserAccessor userAccessor;
    private final SystemInformationService systemInformationService;
    private final LicenseWebFacade webLicenseFacade;

    public MetricCollectorImpl(
            PageManager pageManager,
            SpaceManager spaceManager,
            ClusterManager clusterManager,
            UserAccessor userAccessor,
            SystemInformationService systemInformationService,
            LicenseWebFacade webLicenseFacade) {
        this.pageManager = pageManager;
        this.spaceManager = spaceManager;
        this.clusterManager = clusterManager;
        this.userAccessor = userAccessor;
        this.systemInformationService = systemInformationService;
        this.webLicenseFacade = webLicenseFacade;
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

    private final Gauge allowedUsersGauge = Gauge.build()
            .name("confluence_allowed_users_gauge")
            .help("Allowed Users Gauge")
            .create();

    private final Histogram requestDurationOnPath = Histogram.build()
            .name("confluence_request_duration_on_path")
            .help("Request duration on path")
            .labelNames("path")
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
        ConfluenceLicense confluenceLicense = webLicenseFacade.retrieve().right().getOrNull();
        if (confluenceLicense != null) {
            maintenanceExpiryDaysGauge.set(confluenceLicense.getNumberOfDaysBeforeMaintenanceExpiry());
            allowedUsersGauge.set(confluenceLicense.getMaximumNumberOfUsers());
        }

        // users
        activeUsersGauge.set(userAccessor.countUsersWithConfluenceAccess());

        List<MetricFamilySamples> result = new ArrayList<>();
        result.addAll(clusterPanicCounter.collect());
        result.addAll(labelCreateCounter.collect());
        result.addAll(labelRemoveCounter.collect());
        result.addAll(labelAddCounter.collect());
        result.addAll(labelDeleteCounter.collect());
        result.addAll(userLoginCounter.collect());
        result.addAll(userLogoutCounter.collect());
        result.addAll(requestDurationOnPath.collect());
        result.addAll(totalClusterNodeGauge.collect());
        result.addAll(maintenanceExpiryDaysGauge.collect());
        result.addAll(allowedUsersGauge.collect());
        result.addAll(activeUsersGauge.collect());
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
    public void destroy() throws Exception {
        this.registry.unregister(this);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.registry.register(this);
        DefaultExports.initialize();
    }

    @Override
    public CollectorRegistry getRegistry() {
        return registry;
    }
}
