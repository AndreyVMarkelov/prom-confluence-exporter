package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager;

public interface ScrapingSettingsManager {
    int getDelay();
    void setDelay(int delay);
}
