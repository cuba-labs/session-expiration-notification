package com.company.demo.web;

import com.company.demo.service.SessionExpirationService;
import com.haulmont.cuba.core.sys.AppContext;
import com.haulmont.cuba.core.sys.SecurityContext;
import com.haulmont.cuba.security.app.TrustedClientService;
import com.haulmont.cuba.security.global.LoginException;
import com.haulmont.cuba.security.global.UserSession;
import com.haulmont.cuba.web.App;
import com.haulmont.cuba.web.AppUI;
import com.haulmont.cuba.web.auth.WebAuthConfig;
import com.haulmont.cuba.web.security.events.AppStartedEvent;
import com.vaadin.server.VaadinSession;
import com.vaadin.ui.Notification;
import com.vaadin.ui.Notification.Type;
import org.slf4j.Logger;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component("demo_AppExpirationWatcher")
public class AppExpirationWatcher {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final List<VaadinSession> sessions = new ArrayList<>();
    @Inject
    private TrustedClientService trustedClientService;
    @Inject
    private SessionExpirationService sessionExpirationService;
    @Inject
    private WebAuthConfig webAuthConfig;
    @Inject
    private Logger log;

    public void register(VaadinSession session) {
        lock.writeLock().lock();
        try {
            sessions.add(session);
        } finally {
            lock.writeLock().unlock();
        }
    }

    // called by scheduler
    public synchronized void notifyExpiring() {
        // obtain system session with all permissions
        // check session timeout on behalf of this session
        // it is required due to session prolongation on request
        UserSession systemSession;
        try {
            systemSession = trustedClientService.getSystemSession(webAuthConfig.getTrustedClientPassword());
        } catch (LoginException e) {
            log.error("Unable to get system session");
            return;
        }

        List<UUID> expiringSessions;

        AppContext.setSecurityContext(new SecurityContext(systemSession));
        try {
            expiringSessions = sessionExpirationService.getExpiringSessions();
        } finally {
            AppContext.setSecurityContext(null);
        }

        ArrayList<VaadinSession> activeSessions;

        lock.readLock().lock();
        try {
            activeSessions = new ArrayList<>(sessions);
        } finally {
            lock.readLock().unlock();
        }

        List<VaadinSession> closedSessions = new ArrayList<>();
        for (VaadinSession session : activeSessions) {
            // obtain lock on session state
            session.accessSynchronously(() -> {
                if (session.getState() == VaadinSession.State.OPEN) {
                    // active app in this session
                    App app = App.getInstance();

                    // user is logged in and session is expiring
                    if (app.getConnection().isAuthenticated()
                            && expiringSessions.contains(app.getConnection().getSessionNN().getId())) {

                        // notify all opened web browser tabs
                        List<AppUI> appUIs = app.getAppUIs();
                        for (AppUI ui : appUIs) {
                            if (!ui.isClosing()) {
                                // work in context of UI
                                ui.accessSynchronously(() -> {
                                    new Notification("Your session is about to be closed", Type.TRAY_NOTIFICATION)
                                            .show(ui.getPage());
                                });
                            }
                        }
                    }
                } else {
                    closedSessions.add(session);
                }
            });
        }

        lock.writeLock().lock();
        try {
            activeSessions.removeAll(closedSessions);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @EventListener
    public void onAppStart(AppStartedEvent event) {
        register(VaadinSession.getCurrent());
    }
}