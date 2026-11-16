package com.conlact.conlact_backend.service;

public interface AccountPasswordChecker {

    void assertMatches(String email, String password);

    boolean matches(String email, String password);
}
