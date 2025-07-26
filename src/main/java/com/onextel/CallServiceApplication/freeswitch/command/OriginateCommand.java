package com.onextel.CallServiceApplication.freeswitch.command;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.onextel.CallServiceApplication.freeswitch.command.CustomVariables.EXPORT_AS_SIP_HEADER_PREFIX;

// originate <call_url> <exten>|&<application_name>(<app_args>) [<dialplan>] [<context>] [<cid_name>] [<cid_num>] [<timeout_sec>]
// originate {custom_variable1=value1,custom_variable2=value2}callUrl exten|&applicationName(appArgs) dialplan context cidName cidNum timeoutSec

@Setter
@Getter
public class OriginateCommand extends BaseCommand {
    private String callUrl;
    // Extension - optional extension or application name with args
    // but not both at same time
    private String extension;
    private String applicationName; // Application name (optional)
    private String applicationArguments; // Application arguments (comma-separated)
    private String dialplan;
    private String context;
    private String callerIdName;
    private String callerIdNumber;
    private int timeoutSec;
    private Map<String, String> customVariables; // Custom variables

    private static final String ORIGINATE_COMMAND_FORMAT_WITH_EXTENSION =
            "%s %s %s %s %s %s %d";
    private static final String ORIGINATE_COMMAND_FORMAT_WITH_APPLICATION =
            "%s &%s(%s) %s %s %s %s %d";

    private OriginateCommand(Builder builder) {
        super(Action.ORIGINATE);
        this.callUrl = builder.callUrl;
        this.extension = builder.extension;
        this.applicationName = builder.applicationName;
        this.applicationArguments = builder.applicationArguments;
        this.dialplan = builder.dialplan;
        this.context = builder.context;
        this.callerIdName = builder.callerIdName;
        this.callerIdNumber = builder.callerIdNumber;
        this.timeoutSec = builder.timeoutSec;
        this.customVariables = builder.customVariables;
        if (this.dialplan == null || this.dialplan.isBlank()) {
            this.dialplan = DEFAULT_DIALPLAN;
        }
        if (this.context == null || this.context.isBlank()) {
            this.context = DEFAULT_CONTEXT;
        }
        if (this.timeoutSec <= 0) {
            this.timeoutSec = DEFAULT_TIMEOUT_SECONDS;
        }
    }

    public OriginateCommand(String callUrl, String extension,
                            String applicationName, String applicationArguments,
                            String dialplan, String context,
                            String callerIdName, String callerIdNumber,
                            int timeoutSec, Map<String, String> customVariables) {
        super(Action.ORIGINATE);
        this.callUrl = callUrl;
        this.extension = extension;
        this.applicationName = applicationName;
        this.applicationArguments = applicationArguments;
        this.dialplan = dialplan;
        this.context = context;
        this.callerIdName = callerIdName;
        this.callerIdNumber = callerIdNumber;
        this.timeoutSec = timeoutSec;
        this.customVariables = customVariables;
        if (this.dialplan == null || this.dialplan.isBlank()) {
            this.dialplan = DEFAULT_DIALPLAN;
        }
        if (this.context == null || this.context.isBlank()) {
            this.context = DEFAULT_CONTEXT;
        }
        if (this.timeoutSec <= 0) {
            this.timeoutSec = DEFAULT_TIMEOUT_SECONDS;
        }
    }

    // Method to add a single custom variable
    public void addCustomVariable(String key, String value) {
        if (this.customVariables == null) {
            this.customVariables = new HashMap<>();
        }
        if (!key.startsWith(EXPORT_AS_SIP_HEADER_PREFIX)) {
            key = EXPORT_AS_SIP_HEADER_PREFIX + key;
        }
        this.customVariables.put(key, value);
    }

    // Method to remove a custom variable
    public void removeCustomVariable(String key) {
        if (this.customVariables != null) {
            this.customVariables.remove(key);
        }
    }

    // Method to remove all custom variables
    public void removeAllCustomVariables() {
        if (this.customVariables != null) {
            this.customVariables.clear();
        }
    }

    // Method to add multiple custom variables from another map
    public void addCustomVariables(Map<String, String> variables) {
        if (this.customVariables == null) {
            this.customVariables = new HashMap<>();
        }
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith(EXPORT_AS_SIP_HEADER_PREFIX)) {
                    key = EXPORT_AS_SIP_HEADER_PREFIX + key;
                }
                this.customVariables.put(key, entry.getValue());
            }
        }
    }

    @Override
    public String toPlainText() {
        StringBuilder commandBuilder = new StringBuilder();
        // Construct the originate command base,
        // notice space needed after command name
        commandBuilder.append("bgapi originate ");

        // Add custom variables if provided
        if (customVariables != null && !customVariables.isEmpty()) {
            String variablesString = customVariables.entrySet().stream()
                    .map(entry -> String.format("%s=%s", entry.getKey(), entry.getValue()))
                    .collect(Collectors.joining(","));
            commandBuilder.append("{").append(variablesString).append("}");
        }

        if (extension != null && !extension.isEmpty()) {
            // If extension is provided, use it (no application)
            commandBuilder.append(String.format(ORIGINATE_COMMAND_FORMAT_WITH_EXTENSION,
                    wrapIfContainsSpace(callUrl),
                    wrapIfContainsSpace(extension),
                    wrapIfContainsSpace(dialplan),
                    wrapIfContainsSpace(context),
                    wrapIfContainsSpace(callerIdName),
                    wrapIfContainsSpace(callerIdNumber),
                    timeoutSec
            ));
        } else if (applicationName != null && !applicationName.isEmpty()) {
            // If applicationName is provided (with appArgs)
            commandBuilder.append(String.format(ORIGINATE_COMMAND_FORMAT_WITH_APPLICATION,
                    wrapIfContainsSpace(callUrl),
                    wrapIfContainsSpace(applicationName),
                    wrapIfContainsSpace(applicationArguments),
                    wrapIfContainsSpace(dialplan),
                    wrapIfContainsSpace(context),
                    wrapIfContainsSpace(callerIdName),
                    wrapIfContainsSpace(callerIdNumber),
                    timeoutSec
            ));
        }
        return commandBuilder.toString().trim();
    }

    public static class Builder {
        private String callUrl;
        private String extension;
        private String applicationName;
        private String applicationArguments;
        private String dialplan = DEFAULT_DIALPLAN;
        private String context = DEFAULT_CONTEXT;
        private String callerIdName;
        private String callerIdNumber;
        private int timeoutSec = DEFAULT_TIMEOUT_SECONDS;
        private Map<String, String> customVariables = new HashMap<>();

        public OriginateCommand build() {
            validate();
            return new OriginateCommand(this);
        }

        public Builder callUrl(String callUrl) {
            this.callUrl = callUrl;
            return this;
        }

        public Builder extension(String extension) {
            this.extension = extension;
            return this;
        }

        public Builder applicationName(String applicationName) {
            if (!ApplicationType.isValid(applicationName)) {
                throw new IllegalArgumentException("Invalid application type: " + applicationName);
            }
            this.applicationName = applicationName;
            return this;
        }

        public Builder applicationArguments(String applicationArguments) {
            this.applicationArguments = applicationArguments;
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

        public Builder callerIdName(String callerIdName) {
            this.callerIdName = callerIdName;
            return this;
        }

        public Builder callerIdNumber(String callerIdNumber) {
            this.callerIdNumber = callerIdNumber;
            return this;
        }

        public Builder timeoutSec(int timeoutSec) {
            this.timeoutSec = timeoutSec;
            return this;
        }

        public Builder addCustomVariable(String key, String value) {
            if (this.customVariables == null) {
                this.customVariables = new HashMap<>();
            }
            if (!key.startsWith(EXPORT_AS_SIP_HEADER_PREFIX)) {
                key = EXPORT_AS_SIP_HEADER_PREFIX + key;
            }
            this.customVariables.put(key, value);
            return this;
        }

        public Builder customVariables(Map<String,String> customVariables) {
            if (customVariables != null) {
                for (Map.Entry<String, String> entry : customVariables.entrySet()) {
                    String key = entry.getKey();
                    if (!key.startsWith(EXPORT_AS_SIP_HEADER_PREFIX)) {
                        key = EXPORT_AS_SIP_HEADER_PREFIX + key;
                    }
                    this.customVariables.put(key, entry.getValue());
                }
            }
            return this;
        }

        private void validate() {
            if (callUrl == null || callUrl.isEmpty()) {
                throw new IllegalArgumentException("callUrl is required and cannot be null or empty");
            }

            if (extension != null && applicationName != null) {
                throw new IllegalArgumentException("cannot specify both 'extension' and 'applicationName'");
            }

            if (extension == null && applicationName == null) {
                throw new IllegalArgumentException("must specify either 'extension' or 'applicationName'");
            }

            if (applicationName != null && !ApplicationType.isValid(applicationName)) {
                throw new IllegalArgumentException("Invalid application type: " + applicationName);
            }

            if (timeoutSec <= 0) {
                throw new IllegalArgumentException("timeoutSec must be greater than 0");
            }

            if ((callerIdName != null && callerIdName.isEmpty()) || (callerIdNumber != null && callerIdNumber.isEmpty())) {
                throw new IllegalArgumentException("callerIdName and callerIdNumber cannot be empty if provided");
            }

            // Validate callerIdNumber format (numeric only)
            if (callerIdNumber != null && !callerIdNumber.matches("\\d+")) {
                throw new IllegalArgumentException("callerIdNumber must be numeric");
            }

            // Validate custom variables (if provided)
            if (customVariables != null) {
                for (Map.Entry<String, String> entry : customVariables.entrySet()) {
                    if (entry.getKey() == null || entry.getValue() == null) {
                        throw new IllegalArgumentException("Custom variables must have non-null keys and values");
                    }
                }
            }
        }
    }
}
