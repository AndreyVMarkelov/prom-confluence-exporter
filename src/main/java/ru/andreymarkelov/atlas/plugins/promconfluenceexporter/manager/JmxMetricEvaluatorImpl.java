package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager;

import io.prometheus.client.Collector;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public class JmxMetricEvaluatorImpl implements JmxMetricEvaluator {
    private static final Logger log = LoggerFactory.getLogger(JmxMetricEvaluatorImpl.class);

    private int lastRequestErrorCount = 0;

    // IndexingStatistics

    private final Gauge indexStatFlushing = Gauge.build()
        .name("confluence_jmx_indexstat_flushing")
        .help("Indicate whether the cache is currently flushing")
        .create();

    private final Gauge indexStatLastDuration = Gauge.build()
            .name("confluence_jmx_indexstat_last_duration")
            .help("Time taken during last indexing (ms)")
            .create();

    private final Gauge indexStatTaskQueueLength = Gauge.build()
            .name("confluence_jmx_indexstat_task_queue")
            .help("Shows number of tasks in the queue")
            .create();

    private final Gauge indexStatReIndexing = Gauge.build()
            .name("confluence_jmx_indexstat_reindexing")
            .help("Indicates whether Confluence is currently reindexing")
            .create();

    // SystemInformation

    private final Gauge systemStatDbLatency = Gauge.build()
            .name("confluence_jmx_systemstat_db_latency")
            .help("Shows the latency of an example query performed against the database")
            .create();

    // RequestStatistics

    private final Gauge requestAvgExecTimeForLastTenRequests = Gauge.build()
            .name("confluence_jmx_request_avg_exectime_of_ten_requests")
            .help("Shows the latency of an example query performed against the database")
            .create();

    private final Gauge requestCurrentNumberOfRequestsBeingServed = Gauge.build()
            .name("confluence_jmx_request_current_served_number")
            .help("Number of requests being served at this instant")
            .create();

    private final Counter requestErrorCount = Counter.build()
            .name("confluence_jmx_request_errorpage_count")
            .help("Number of times the Confluence error page was served")
            .create();

    private final Gauge requestNumberInLastTenSeconds = Gauge.build()
            .name("confluence_jmx_request_num_in_last_ten_seconds")
            .help("The number of requests in the last ten seconds")
            .create();

    private final MBeanServer mBeanServer;

    public JmxMetricEvaluatorImpl() {
        this.mBeanServer = ManagementFactory.getPlatformMBeanServer();
    }

    @Override
    public List<Collector.MetricFamilySamples> metrics() {
        indexStatistics();
        systemStatistics();
        requestStatistics();
        cacheStatistics();

        List<Collector.MetricFamilySamples> res = new ArrayList<>();
        // index
        res.addAll(indexStatFlushing.collect());
        res.addAll(indexStatLastDuration.collect());
        res.addAll(indexStatTaskQueueLength.collect());
        res.addAll(indexStatReIndexing.collect());
        // system
        res.addAll(systemStatDbLatency.collect());
        // requests
        res.addAll(requestAvgExecTimeForLastTenRequests.collect());
        res.addAll(requestCurrentNumberOfRequestsBeingServed.collect());
        res.addAll(requestErrorCount.collect());
        res.addAll(requestNumberInLastTenSeconds.collect());
        return res;
    }

    private void indexStatistics() {
        try {
            ObjectName objectName = new ObjectName("Confluence:name=IndexingStatistics");
            indexStatFlushing.set(getBoolean(mBeanServer.getAttribute(objectName, "Flushing")));
            indexStatLastDuration.set(getLong(mBeanServer.getAttribute(objectName, "LastElapsedMilliseconds")));
            indexStatTaskQueueLength.set(getInt(mBeanServer.getAttribute(objectName, "TaskQueueLength")));
            indexStatReIndexing.set(getBoolean(mBeanServer.getAttribute(objectName, "ReIndexing")));
        } catch (InstanceNotFoundException ex) {
            log.warn("JMX stat not found in JVM", ex);
        } catch (Exception ex) {
            log.error("Cannot load JMX index stats", ex);
        }
    }

    private void systemStatistics() {
        try {
            ObjectName objectName = new ObjectName("Confluence:name=SystemInformation");
            systemStatDbLatency.set(getLong(mBeanServer.getAttribute(objectName, "DatabaseExampleLatency")));
        } catch (InstanceNotFoundException ex) {
            log.warn("JMX stat not found in JVM", ex);
        } catch (Exception ex) {
            log.error("Cannot load JMX system stats", ex);
        }
    }

    private void requestStatistics() {
        try {
            ObjectName objectName = new ObjectName("Confluence:name=RequestMetrics");
            requestAvgExecTimeForLastTenRequests.set(getInt(mBeanServer.getAttribute(objectName, "AverageExecutionTimeForLastTenRequests")));
            requestCurrentNumberOfRequestsBeingServed.set(getInt(mBeanServer.getAttribute(objectName, "CurrentNumberOfRequestsBeingServed")));
            requestNumberInLastTenSeconds.set(getInt(mBeanServer.getAttribute(objectName, "NumberOfRequestsInLastTenSeconds")));

            int currentRequestErrorCount = getInt(mBeanServer.getAttribute(objectName, "ErrorCount"));
            requestErrorCount.inc(currentRequestErrorCount - lastRequestErrorCount);
            lastRequestErrorCount = currentRequestErrorCount;
        } catch (InstanceNotFoundException ex) {
            log.warn("JMX stat not found in JVM", ex);
        } catch (Exception ex) {
            log.error("Cannot load JMX request stats", ex);
        }
    }

    private void cacheStatistics() {
        try {
            ObjectName objectName = new ObjectName("Confluence:name=CacheStatistics");
        } catch (Exception ex) {
            log.error("Cannot load JMX cache stats", ex);
        }
    }

    private static double getBoolean(Object obj) {
        return obj != null ? ((Boolean) obj ? 1 : 0) : 0;
    }

    private static int getInt(Object obj) {
        return obj != null ? (Integer) obj : 0;
    }

    private static long getLong(Object obj) {
        return obj != null ? (Long) obj : 0L;
    }
}
