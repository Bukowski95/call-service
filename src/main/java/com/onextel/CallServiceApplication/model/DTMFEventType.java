package com.onextel.CallServiceApplication.model;

public enum DTMFEventType {

    DIGIT_PRESSED,       // When a digit is initially pressed
    DIGIT_RELEASED,      // When a digit is released
    LONG_PRESS,          // When a digit is held for extended duration
    SEQUENCE_COMPLETED,  // When a complete DTMF sequence is entered
    SEQUENCE_TIMEOUT,    // When sequence input times out
    INVALID_SEQUENCE;    // When an invalid sequence is entered

    public boolean isTerminalEvent() {
        return this == SEQUENCE_COMPLETED ||
                this == SEQUENCE_TIMEOUT ||
                this == INVALID_SEQUENCE;
    }
}
