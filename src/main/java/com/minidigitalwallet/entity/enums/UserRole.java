package com.minidigitalwallet.entity.enums;

/**
 * Coarse-grained authorization role. USER is granted on registration;
 * ADMIN is granted manually in the database (see README) and gates /api/v1/admin/**.
 */
public enum UserRole {
    USER,
    ADMIN
}
