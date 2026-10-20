package com.minidigitalwallet.service;

/**
 * Abstraction over "who calls" (DIP): production reads SecurityContext,
 * tests inject a stub. Null = unauthenticated -> callers must reject with 401.
 */
public interface CallerProvider {

    String currentUserIdOrNull();
}
