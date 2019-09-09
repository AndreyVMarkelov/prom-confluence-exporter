package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager;

import io.prometheus.client.Collector;
import io.prometheus.client.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

public class JmxMetricEvaluatorImpl implements JmxMetricEvaluator {
    private static final Logger log = LoggerFactory.getLogger(JmxMetricEvaluatorImpl.class);

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

    private final MBeanServer mBeanServer;

    public JmxMetricEvaluatorImpl() {
        this.mBeanServer = ManagementFactory.getPlatformMBeanServer();
    }

    @Override
    public List<Collector.MetricFamilySamples> metrics() {
        indexStatistics();
        systemStatistics();

        List<Collector.MetricFamilySamples> res = new ArrayList<>();
        res.addAll(indexStatFlushing.collect());
        res.addAll(indexStatLastDuration.collect());
        res.addAll(indexStatTaskQueueLength.collect());
        res.addAll(indexStatReIndexing.collect());
        res.addAll(systemStatDbLatency.collect());
        return res;
    }

    private void indexStatistics() {
        try {
            ObjectName objectName = new ObjectName("Confluence:name=IndexingStatistics");
            indexStatFlushing.set(getBoolean(mBeanServer.getAttribute(objectName, "Flushing")));
            indexStatLastDuration.set(getLong(mBeanServer.getAttribute(objectName, "LastElapsedMilliseconds")));
            indexStatTaskQueueLength.set(getInt(mBeanServer.getAttribute(objectName, "TaskQueueLength")));
            indexStatReIndexing.set(getBoolean(mBeanServer.getAttribute(objectName, "ReIndexing")));
        } catch (Exception ex) {
            log.error("Cannot load JMX index stats", ex);
        }
    }

    private void systemStatistics() {
        try {
            ObjectName objectName = new ObjectName("Confluence:name=SystemInformation");
            systemStatDbLatency.set(getLong(mBeanServer.getAttribute(objectName, "DatabaseExampleLatency")));
        } catch (Exception ex) {
            log.error("Cannot load JMX system stats", ex);
        }
    }

    private static double getBoolean(Object obj) {
        return obj != null ? ((Boolean) obj ? 1 : 0) : 0;
    }

    private static double getInt(Object obj) {
        return obj != null ? (Integer) obj : 0;
    }

    private static double getLong(Object obj) {
        return obj != null ? (Long) obj : 0L;
    }
}
