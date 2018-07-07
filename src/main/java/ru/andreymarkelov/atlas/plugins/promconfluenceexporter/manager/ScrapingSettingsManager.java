package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager;

import java.util.List;

public interface ScrapingSettingsManager {
    int getDelay();
    void setDelay(int delay);

    List<String> getDurationPaths();
    void setDurationPaths(List<String> durationPaths);
}
