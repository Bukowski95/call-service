package com.onextel.CallServiceApplication.freeswitch.command;

public class CustomVariables {

    public static final String ORIGINATION_UUID = "origination_uuid";
    public static final String ORIGINATION_RETRIES = "originate_retries";
    public static final String ORIGINATION_RETRY_SLEEP_MS = "originate_retry_sleep_ms";
    public static final String ORIGINATION_CALLER_ID_NAME = "origination_caller_id_name";
    public static final String ORIGINATION_CALLER_ID_NUMBER = "origination_caller_id_number";
    public static final String ORIGINATION_TIMEOUT = "originate_timeout";

    public static final String GROUP_CONFIRM_KEY = "group_confirm_key";
    public static final String GROUP_CONFIRM_FILE = "group_confirm_file";
    public static final String FORKED_DIAL = "forked_dial";
    public static final String FAIL_ON_SINGLE_REJECT = "fail_on_single_reject";
    public static final String IGNORE_EARLY_MEDIA = "ignore_early_media";
    public static final String RETURN_RING_READY = "return_ring_ready";
    public static final String SIP_AUTO_ANSWER = "sip_auto_answer";

    public static final String EXPORT_VARS = "export_vars";
    public static final String EXPORT_AS_SIP_HEADER_PREFIX = "sip_h_X-";
    public static final String EXPORT_NO_LOACL_PREFIX = "nolocal:";
    public static final String API_EXPORT_PREFIX = "api_on_answer_ex";
    public static final String CORRELATION_ID = "correlation_id";
    public static final String ONEXTEL_CALL_ID = "onextel_call_id";
    public static final String ONEXTEL_CALL_SERVICE_ID = "onextel_call_service_id";

    private CustomVariables() {
        throw new UnsupportedOperationException("Cannot instantiate a constants class");
    }

    public static String getVariable(String name) {
        return "variable_" + name;
    }

    public static String getVariableWithSipHeader(String name) {
        return"variable_sip_h_X-" + name;
    }
}
