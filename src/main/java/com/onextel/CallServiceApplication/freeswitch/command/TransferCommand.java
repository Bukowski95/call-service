package com.onextel.CallServiceApplication.freeswitch.command;

import lombok.Getter;
import lombok.Setter;

// -USAGE: <uuid> [-bleg|-both] <dest-exten> [<dialplan>] [<context>]
// <uuid>: The UUID of the call to be transferred (required).
// [-bleg|-both]: Optional flag to specify whether you want to transfer
//              only the "blind leg" (-bleg), or both legs of the call (-both).
// <dest-exten>: The destination extension to which the call should be transferred (required).
// [<dialplan>]: Optional dialplan to use for the transfer
//              (if omitted, the default dialplan will be used).
// [<context>]: Optional context to use for the transfer
//              (if omitted, the default context will be used).

@Setter
@Getter
public class TransferCommand extends BaseCommand {

    private static final String UUID_TRANSFER_COMMAND_FORMAT =
            "bgapi uuid_transfer ";

    private final String uuid;
    private final String destination;
    private String leg;   // -bleg or -both
    private String dialplan;
    private String context;
    private String correlationId;

    private TransferCommand(Builder builder) {
        super(Action.TRANSFER);
        this.uuid = builder.uuid;
        this.destination = builder.destination;
        this.leg = builder.leg;
        this.dialplan = builder.dialplan;
        this.context = builder.context;
        this.correlationId = builder.correlationId;
    }

    public TransferCommand(String uuid, String destination) {
        super(Action.TRANSFER);
        this.uuid = uuid;
        this.destination = destination;
    }

    public TransferCommand(String uuid, String destination,
                           String leg, String correlationId) {
        super(Action.TRANSFER);
        this.uuid = uuid;
        this.destination = destination;
        this.leg = leg;
        this.correlationId = correlationId;
    }

    public TransferCommand(String uuid, String destination,
                           String leg, String dialplan, String context,
                           String correlationId) {
        super(Action.TRANSFER);
        this.uuid = uuid;
        this.destination = destination;
        this.leg = leg;
        this.dialplan = dialplan;
        this.context = context;
        this.correlationId = correlationId;
    }

    @Override
    public String toPlainText() {
        StringBuilder transferCommand = new StringBuilder(UUID_TRANSFER_COMMAND_FORMAT);
        transferCommand.append(uuid);
        // Add the bleg/both flag if provided (ensure it has a '-' before it)
        if (leg != null && (leg.equals("bleg") || leg.equals("both"))) {
            transferCommand.append(" -").append(leg);
        }
        transferCommand.append(" ").append(destination);
        if (dialplan != null) {
            transferCommand.append(" ").append(dialplan);
        }
        if (context != null) {
            transferCommand.append(" ").append(context);
        }
        if (correlationId != null) {
            transferCommand.append(" ")
                    .append("correlationId=")
                    .append(correlationId);
        }
        return transferCommand.toString().trim();
    }

    // Static builder class
    public static class Builder {
        private final String uuid;
        private final String destination;
        private String leg;
        private String dialplan = BaseCommand.DEFAULT_DIALPLAN;
        private String context = BaseCommand.DEFAULT_CONTEXT;
        private String correlationId;

        // Constructor with the required parameters
        public Builder(String uuid, String destination) {
            if (uuid == null || destination == null) {
                throw new IllegalArgumentException("UUID and destination extension are required");
            }
            this.uuid = uuid;
            this.destination = destination;
        }

        public Builder leg(String leg) {
            if (leg != null && !leg.equals("bleg") && !leg.equals("both")) {
                throw new IllegalArgumentException("Invalid leg option: must be 'bleg' or 'both'");
            }
            this.leg = leg;
            return this;
        }

        public Builder dialplan(String dialplan) {
            this.dialplan = dialplan;
            return this;
        }

        public Builder context(String context) {
            this.context = context;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        // Method to build the UuidTransferCommand object
        public TransferCommand build() {
            return new TransferCommand(this);
        }
    }

}
