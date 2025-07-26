package com.onextel.CallServiceApplication.dto;

import com.onextel.CallServiceApplication.freeswitch.command.ApplicationType;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class CallRequest {
    private String callUrl;
    private String extension;
    private String applicationName;
    private String applicationArguments;
    private String dialplan;
    private String context;
    private String callerIdName;
    private String callerIdNumber;
    private int timeoutSec;
    private Map<String, String> customVariables;

    // Validation method
    public void validate() {
        // Validate callUrl
        if (callUrl == null || callUrl.isEmpty()) {
            throw new IllegalArgumentException("callUrl is required and cannot be null or empty");
        }

        // Ensure either extension or applicationName is provided, but not both
        if (extension != null && applicationName != null) {
            throw new IllegalArgumentException("cannot specify both 'extension' and 'applicationName'");
        }
        if (extension == null && applicationName == null) {
            throw new IllegalArgumentException("must specify either 'extension' or 'applicationName'");
        }

        // Validate timeoutSec if it's non-positive
        if (timeoutSec <= 0) {
            throw new IllegalArgumentException("timeoutSec must be greater than 0");
        }

        // Validate callerIdName and callerIdNumber (if provided)
        if ((callerIdName != null && callerIdName.isEmpty()) || (callerIdNumber != null && callerIdNumber.isEmpty())) {
            throw new IllegalArgumentException("callerIdName and callerIdNumber cannot be empty if provided");
        }

        // Validate if applicationName is provided and valid
        if (applicationName != null && !ApplicationType.isValid(applicationName)) {
            throw new IllegalArgumentException("Invalid application type: " + applicationName);
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
