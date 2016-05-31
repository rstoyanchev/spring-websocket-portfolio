package org.springframework.samples.portfolio.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;

public class PortfolioAuthentication extends UsernamePasswordAuthenticationToken {
    private final String token;
    private final String ip;

    public PortfolioAuthentication(Object principal, Object credentials, String token, String ip) {
        super(principal, credentials, Arrays.asList(new SimpleGrantedAuthority("ROLE_USER")));
        this.token = token;
        this.ip = ip;
    }

    public String getToken() {
        return token;
    }

    public String getIp() {
        return ip;
    }
}
