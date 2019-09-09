package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager;

import io.prometheus.client.Collector;

import java.util.List;

public interface JmxMetricEvaluator {
    List<Collector.MetricFamilySamples> metrics();
}
