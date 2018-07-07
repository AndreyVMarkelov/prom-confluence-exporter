package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager;

import java.util.List;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;

import static org.apache.commons.lang3.math.NumberUtils.toInt;

public class ScrapingSettingsManagerImpl implements ScrapingSettingsManager {
    private static final int DEFAULT_SCRAPE_DELAY = 1;

    private final PluginSettings pluginSettings;

    public ScrapingSettingsManagerImpl(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettings = pluginSettingsFactory.createSettingsForKey("PLUGIN_PROMETHEUS_FOR_CONFLUENCE");
    }

    @Override
    public int getDelay() {
        Object storedValue = getPluginSettings().get("delay");
        return storedValue != null ? toInt(storedValue.toString(), DEFAULT_SCRAPE_DELAY) : DEFAULT_SCRAPE_DELAY;
    }

    @Override
    public void setDelay(int delay) {
        getPluginSettings().put("delay", String.valueOf(delay));
    }

    @Override
    public List<String> getDurationPaths() {
        return null;
    }

    @Override
    public void setDurationPaths(List<String> durationPaths) {

    }

    private synchronized PluginSettings getPluginSettings() {
        return pluginSettings;
    }
}
