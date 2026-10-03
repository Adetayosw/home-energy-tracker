package com.adetayo.user_service.exception;

public class DuplicateEmailException extends RuntimeException {

    private final String email;

    public DuplicateEmailException(String email) {
        super("A user with email " + email + " already exists");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
