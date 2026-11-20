package com.bugfarm.lunch.orders.fakes;

import com.bugfarm.lunch.contracts.Interpretation;
import com.bugfarm.lunch.contracts.OrderInterpreter;
import java.util.ArrayList;
import java.util.List;

/** Returns a preset Interpretation and records every text it received. */
public class FakeOrderInterpreter implements OrderInterpreter {

    private Interpretation preset = Interpretation.unclear("preset not set");
    private final List<String> received = new ArrayList<>();

    public void willReturn(Interpretation interpretation) {
        this.preset = interpretation;
    }

    public List<String> received() {
        return received;
    }

    @Override
    public Interpretation interpret(String messageText) {
        received.add(messageText);
        return preset;
    }
}
