package com.onextel.CallServiceApplication.freeswitch.command;

// uuid_kill <uuid> [cause]
// uuid_kill 12345678-1234-1234-1234-123456789abc NORMAL_CLEARING

public class HangupCommand extends BaseCommand {

    private static final String UUID_KILL_COMMAND_FORMAT =
            "bgapi uuid_kill %s %s correlationId=%s";

    private final String uuid;
    private final String cause;
    private final String correlationId;

    public HangupCommand(String uuid, String cause, String correlationId) {
        super(Action.HANGUP);
        this.uuid = uuid;
        this.cause = cause;
        this.correlationId = correlationId;
    }

    @Override
    public String toPlainText() {
        return String.format(UUID_KILL_COMMAND_FORMAT,
                uuid, wrapIfContainsSpace(cause), correlationId);
    }
}