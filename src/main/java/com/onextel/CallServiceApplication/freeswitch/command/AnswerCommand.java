package com.onextel.CallServiceApplication.freeswitch.command;

public class AnswerCommand extends BaseCommand {

    private static final String UUID_ANSWER_COMMAND_FORMAT =
            "bgapi uuid_answer %s correlation_id=%s";
    private final String uuid;
    private final String correlationId;

    public AnswerCommand(String uuid, String correlationId) {
        super(Action.ANSWER);
        this.uuid = uuid;
        this.correlationId = correlationId;
    }

    @Override
    public String toPlainText() {
        return String.format(UUID_ANSWER_COMMAND_FORMAT, uuid, correlationId);
    }
}
