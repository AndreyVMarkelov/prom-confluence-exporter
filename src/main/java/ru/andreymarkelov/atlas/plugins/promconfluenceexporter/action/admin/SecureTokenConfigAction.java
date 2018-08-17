package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.action.admin;

import java.util.Date;

import com.atlassian.confluence.core.ConfluenceActionSupport;
import ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager.ScheduledMetricEvaluator;
import ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager.ScrapingSettingsManager;
import ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager.SecureTokenManager;

public class SecureTokenConfigAction extends ConfluenceActionSupport {
    private SecureTokenManager secureTokenManager;
    private ScrapingSettingsManager scrapingSettingsManager;
    private ScheduledMetricEvaluator scheduledMetricEvaluator;

    private final static String ERROR_INVALID_DELAY = "ru.andreymarkelov.atlas.plugins.promconfluenceexporter.admin.error.invalid.delay";
    private final static String NOT_YET_EXECUTED = "ru.andreymarkelov.atlas.plugins.promconfluenceexporter.admin.settings.notyetexecuted";

    private boolean saved = false;
    private String token;

    private int delay;
    private String lastExecutionTimestamp;

    @Override
    public String doDefault() {
        this.token = secureTokenManager.getToken();
        this.delay = scrapingSettingsManager.getDelay();
        long temp = scheduledMetricEvaluator.getLastExecutionTimestamp();
        this.lastExecutionTimestamp = (temp > 0) ? new Date(temp).toString() : getText(NOT_YET_EXECUTED);
        return INPUT;
    }

    @Override
    public void validate() {
        if (delay <= 0) {
            addFieldError("delay", getText(ERROR_INVALID_DELAY));
        }
    }

    @Override
    public String execute() {
        if(hasErrors()) {
            return ERROR;
        }
        secureTokenManager.setToken(token);
        scrapingSettingsManager.setDelay(delay);
        scheduledMetricEvaluator.restartScraping(delay);
        setSaved(true);

        return SUCCESS;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public void setSecureTokenManager(SecureTokenManager secureTokenManager) {
        this.secureTokenManager = secureTokenManager;
    }

    public void setScheduledMetricEvaluator(ScheduledMetricEvaluator scheduledMetricEvaluator){
        this.scheduledMetricEvaluator = scheduledMetricEvaluator;
    }

    public void setScrapingSettingsManager(ScrapingSettingsManager scrapingSettingsManager) {
        this.scrapingSettingsManager = scrapingSettingsManager;
    }

    public boolean isSaved() {
        return saved;
    }

    public void setSaved(final boolean saved) {
        this.saved = saved;
    }

    public int getDelay(){
        return delay;
    }

    public void setDelay(int delay){
        this.delay = delay;
    }

    public String getLastExecutionTimestamp() {
        return lastExecutionTimestamp;
    }
}
