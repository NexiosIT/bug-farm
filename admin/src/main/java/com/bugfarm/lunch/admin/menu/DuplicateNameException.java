package com.bugfarm.lunch.admin.menu;

/** Another food type (or another item in the same food type) already has this name. */
public class DuplicateNameException extends RuntimeException {
    public DuplicateNameException(String name) {
        super("Name already exists: " + name);
    }
}
