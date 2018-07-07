package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager;

import java.io.IOException;
import javax.servlet.ServletException;

import io.prometheus.client.CollectorRegistry;
import ru.andreymarkelov.atlas.plugins.promconfluenceexporter.util.ExceptionRunnable;

public interface MetricCollector {
    CollectorRegistry getRegistry();
    void clusterPanicCounter();
    void labelCreateCounter(String visibility, String prefix);
    void labelAddCounter(String visibility, String prefix, String source, String spaceKey);
    void labelRemoveCounter(String visibility, String prefix, String source, String spaceKey);
    void labelDeleteCounter(String visibility, String prefix);
    void userLoginCounter(String username, String ip);
    void userLogoutCounter(String username, String ip);
    void userLoginFailedCounter(String username, String ip);
    void spaceCreateCounter(String username);
    void spaceDeleteCounter(String username);
    void requestDuration(String path, ExceptionRunnable runnable) throws IOException, ServletException;
}
