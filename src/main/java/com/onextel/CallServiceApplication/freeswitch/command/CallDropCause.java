package com.onextel.CallServiceApplication.freeswitch.command;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public enum CallDropCause {

    NORMAL_CLEARING(16, "Normal call termination"),
    USER_BUSY(17, "The destination user is busy"),
    NO_USER_RESPONSE(18, "No response from the destination user"),
    NO_ANSWER(19, "No answer from the destination"),
    CALL_REJECTED(21, "The call was rejected by the destination"),
    NON_SELECTED_USER_CLEARING(22, "Non-selected user clearing"),
    DESTINATION_OUT_OF_ORDER(27, "Destination is out of order"),
    INVALID_NUMBER_FORMAT(28, "The number format is invalid"),
    FACILITY_REJECTED(29, "The call was rejected due to a facility issue"),
    REQUESTED_CIRCUIT_OR_CHANNEL_NOT_AVAILABLE(34, "Requested circuit or channel not available"),
    TEMPORARY_FAILURE(41, "Temporary failure in the network"),
    SWITCHING_EQUIPMENT_CONGESTION(42, "Switching equipment congestion"),
    NETWORK_OUT_OF_ORDER(47, "The network is out of order"),
    MISCELLANEOUS_ERROR(51, "A miscellaneous error occurred");

    private final int code;
    private final String description;

    // Static Map to hold string -> CallDropCause mappings
    private static final Map<String, CallDropCause> STRING_TO_CAUSE_MAP = new HashMap<>();

    static {
        for (CallDropCause cause : values()) {
            STRING_TO_CAUSE_MAP.put(cause.name(), cause);
        }
    }

    // Constructor to initialize code and description
    CallDropCause(int code, String description) {
        this.code = code;
        this.description = description;
    }


    @Override
    public String toString() {
        return String.format("%d: %s", code, description);
    }

    public static CallDropCause fromString(String cause) {
        CallDropCause result = STRING_TO_CAUSE_MAP.get(cause);
        if (result == null) {
            throw new IllegalArgumentException("Unknown cause: " + cause);
        }
        return result;
    }

    public static CallDropCause getByCode(int code) {
        for (CallDropCause cause : values()) {
            if (cause.getCode() == code) {
                return cause;
            }
        }
        throw new IllegalArgumentException("No cause found with code: " + code);
    }
}
