package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.action.admin;

import com.atlassian.confluence.core.ConfluenceActionSupport;
import ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager.ScheduledMetricEvaluator;
import ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager.SecureTokenManager;

import java.util.Date;
import java.util.Map;

public class SecureTokenConfigAction extends ConfluenceActionSupport {
    private SecureTokenManager secureTokenManager;
    private ScheduledMetricEvaluator scheduledMetricEvaluator;

    private final static String ERROR_INVALID_DELAY = "ru.andreymarkelov.atlas.plugins.promconfluenceexporter.admin.error.invalid.delay";
    private final static String NOT_YET_EXECUTED = "ru.andreymarkelov.atlas.plugins.promconfluenceexporter.admin.settings.notyetexecuted";

    private boolean saved = false;
    private String token;

    private int delay;
    private String lastExecutionTimestamp;

    @Override
    public String doDefault() throws Exception {
        token = secureTokenManager.getToken();
        delay = scheduledMetricEvaluator.getDelay();
        long temp = scheduledMetricEvaluator.getLastExecutionTimestamp();

        if(temp > 0){
            lastExecutionTimestamp = new Date(temp).toString();
        } else {
            lastExecutionTimestamp = getI18n().getText(NOT_YET_EXECUTED);
        }

        return INPUT;
    }

    @Override
    public void validate(){
        if(delay <= 0){
            addFieldError("delay", getI18n().getText(ERROR_INVALID_DELAY));
        }
    }

    @Override
    public String execute() throws Exception {
        if(hasErrors()){
            return ERROR;
        }
        secureTokenManager.setToken(token);
        scheduledMetricEvaluator.setDelay(delay);
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
