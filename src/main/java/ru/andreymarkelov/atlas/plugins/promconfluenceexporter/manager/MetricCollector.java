package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager;

import io.prometheus.client.CollectorRegistry;
import ru.andreymarkelov.atlas.plugins.promconfluenceexporter.util.ExceptionRunnable;

import javax.servlet.ServletException;
import java.io.IOException;

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
    void pluginEnabledEvent(String pluginKey);
    void pluginDisabledEvent(String pluginKey);
    void pluginInstalledEvent(String pluginKey);
    void pluginUninstalledEvent(String pluginKey);
    void requestDuration(String path, ExceptionRunnable runnable) throws IOException, ServletException;
}
