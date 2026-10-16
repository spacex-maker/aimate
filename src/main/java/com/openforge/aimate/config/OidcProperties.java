package com.openforge.aimate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aimatex.oidc")
public class OidcProperties {

    private boolean enabled = true;
    private String issuer = "https://account-api.aimatex.com";
    private String clientId = "aimatex-chat";
    private String authorizeUrl = "https://account.aimatex.com/oauth/authorize";
    private String tokenUrl = "https://account-api.aimatex.com/oauth/token";
    private String userinfoUrl = "https://account-api.aimatex.com/oauth/userinfo";
    private String jwksUrl = "https://account-api.aimatex.com/oauth/jwks";
    private String redirectUri = "https://chat.aimatex.com/oauth/callback";
    private String redirectUris =
            "https://chat.aimatex.com/oauth/callback,http://127.0.0.1:5173/oauth/callback,http://localhost:5173/oauth/callback";
    private String scopes = "openid profile email membership";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getAuthorizeUrl() {
        return authorizeUrl;
    }

    public void setAuthorizeUrl(String authorizeUrl) {
        this.authorizeUrl = authorizeUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getUserinfoUrl() {
        return userinfoUrl;
    }

    public void setUserinfoUrl(String userinfoUrl) {
        this.userinfoUrl = userinfoUrl;
    }

    public String getJwksUrl() {
        return jwksUrl;
    }

    public void setJwksUrl(String jwksUrl) {
        this.jwksUrl = jwksUrl;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public String getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(String redirectUris) {
        this.redirectUris = redirectUris;
    }

    public String getScopes() {
        return scopes;
    }

    public void setScopes(String scopes) {
        this.scopes = scopes;
    }
}
