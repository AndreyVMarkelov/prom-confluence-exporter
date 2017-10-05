package ru.andreymarkelov.atlas.plugins.promconfluenceexporter.manager;

public interface SecureTokenManager {
    String getToken();
    void setToken(String token);
}
