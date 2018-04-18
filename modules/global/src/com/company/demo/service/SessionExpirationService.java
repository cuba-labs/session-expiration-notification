package com.company.demo.service;

import java.util.List;
import java.util.UUID;

public interface SessionExpirationService {
    String NAME = "demo_SessionExpirationService";

    List<UUID> getExpiringSessions();
}