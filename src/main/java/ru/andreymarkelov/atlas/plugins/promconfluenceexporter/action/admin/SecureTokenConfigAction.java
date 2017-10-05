package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.action.admin;

import com.atlassian.confluence.core.ConfluenceActionSupport;
import ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager.SecureTokenManager;

public class SecureTokenConfigAction extends ConfluenceActionSupport {
    private SecureTokenManager secureTokenManager;

    private String token;

    @Override
    public String doDefault() throws Exception {
        token = secureTokenManager.getToken();
        return INPUT;
    }

    @Override
    public String execute() throws Exception {
        secureTokenManager.setToken(token);
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
}
