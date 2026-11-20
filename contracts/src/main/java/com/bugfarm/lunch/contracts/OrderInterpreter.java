package com.bugfarm.lunch.contracts;

public interface OrderInterpreter {
    /** Interprets a chat message. Never throws; failures give UNCLEAR. */
    Interpretation interpret(String messageText);
}
