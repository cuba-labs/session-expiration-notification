package com.company.demo.service;

import com.haulmont.cuba.security.app.UserSessionsAPI;
import com.haulmont.cuba.security.global.UserSession;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service(SessionExpirationService.NAME)
public class SessionExpirationServiceBean implements SessionExpirationService {
    @Inject
    private UserSessionsAPI userSessionsAPI;

    @Override
    public List<UUID> getExpiringSessions() {
        // todo this check is only for test

        return userSessionsAPI.getUserSessionsStream()
                .filter(userSession -> !userSession.isSystem())
                .map(UserSession::getId)
                .collect(Collectors.toList());
    }
}