package com.bugfarm.lunch.orders.fakes;

import com.bugfarm.lunch.contracts.OrderingWindow;
import java.time.Instant;
import java.time.LocalTime;

/** Open/closed switch; cut-off 10:00. */
public class FakeOrderingWindow implements OrderingWindow {

    private boolean open = true;

    public void setOpen(boolean open) {
        this.open = open;
    }

    @Override
    public boolean isOpen(Instant now) {
        return open;
    }

    @Override
    public LocalTime cutOff() {
        return LocalTime.of(10, 0);
    }
}
