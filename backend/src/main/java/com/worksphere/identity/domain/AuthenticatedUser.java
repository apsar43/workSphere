package com.worksphere.identity.domain;

import java.util.UUID;

public record AuthenticatedUser(
    UUID id, String email, String firstName, String lastName, String displayName) {}
