package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager;

import io.prometheus.client.Collector;

public interface MetricCollector {
    Collector getCollector();

    void clusterPanicCounter();

    void labelCreateCounter(String visibility, String prefix);
    void labelAddCounter(String visibility, String prefix, String source, String spaceKey);
    void labelRemoveCounter(String visibility, String prefix, String source, String spaceKey);
    void labelDeleteCounter(String visibility, String prefix);

    void userLoginCounter(String username, String ip);
    void userLogoutCounter(String username, String ip);

    void spaceCreateCounter(String username);
    void spaceDeleteCounter(String username);
}
