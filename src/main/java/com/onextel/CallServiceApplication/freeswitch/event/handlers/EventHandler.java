package com.onextel.CallServiceApplication.freeswitch.event.handlers;


import com.onextel.CallServiceApplication.freeswitch.event.Event;

public abstract class EventHandler {

    public abstract void handleEvent(Event event);
}
