package com.bugfarm.lunch.admin.menu;

/** No food type or item with this id. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String what, long id) {
        super(what + " " + id + " not found");
    }
}
