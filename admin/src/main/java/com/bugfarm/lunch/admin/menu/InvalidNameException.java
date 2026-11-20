package com.bugfarm.lunch.admin.menu;

/** The name is blank after trimming, or too long. */
public class InvalidNameException extends RuntimeException {
    public InvalidNameException(String message) {
        super(message);
    }
}
