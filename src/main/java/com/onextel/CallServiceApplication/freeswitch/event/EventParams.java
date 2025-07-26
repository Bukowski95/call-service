package com.onextel.CallServiceApplication.freeswitch.event;

public class EventParams {
    // Common Event Parameters
    public static final String EVENT_NAME = "Event-Name";
    public static final String CORE_UUID = "Core-UUID";
    public static final String FREESWITCH_HOSTNAME = "FreeSWITCH-Hostname";
    public static final String FREESWITCH_SWITCHNAME = "FreeSWITCH-Switchname";
    public static final String FREESWITCH_IPV4 = "FreeSWITCH-IPv4";
    public static final String FREESWITCH_IPV6 = "FreeSWITCH-IPv6";
    public static final String EVENT_DATE_LOCAL = "Event-Date-Local";
    public static final String EVENT_DATE_GMT = "Event-Date-GMT";
    public static final String EVENT_DATE_TIMESTAMP = "Event-Date-Timestamp";
    public static final String EVENT_CALLING_FILE = "Event-Calling-File";
    public static final String EVENT_CALLING_FUNCTION = "Event-Calling-Function";
    public static final String EVENT_CALLING_LINE_NUMBER = "Event-Calling-Line-Number";
    public static final String EVENT_SEQUENCE = "Event-Sequence";
    public static final String EVENT_SUBCLASS = "Event-Subclass";


    // Heartbeat Event Parameters
    public static final String EVENT_INFO = "Event-Info";
    public static final String UP_TIME = "Up-Time";
    public static final String FREESWITCH_VERSION = "FreeSWITCH-Version";
    public static final String UPTIME_MSEC = "Uptime-msec";
    public static final String SESSION_COUNT = "Session-Count";
    public static final String MAX_SESSIONS = "Max-Sessions";
    public static final String SESSION_PER_SEC = "Session-Per-Sec";
    public static final String SESSION_PER_SEC_LAST = "Session-Per-Sec-Last";
    public static final String SESSION_PER_SEC_MAX = "Session-Per-Sec-Max";
    public static final String SESSION_PER_SEC_FIVE_MIN = "Session-Per-Sec-FiveMin";
    public static final String SESSION_SINCE_STARTUP = "Session-Since-Startup";
    public static final String SESSION_PEAK_MAX = "Session-Peak-Max";
    public static final String SESSION_PEAK_FIVE_MIN = "Session-Peak-FiveMin";
    public static final String IDLE_CPU = "Idle-CPU";

    // background job event parameters
    public static final String JOB_COMMAND = "Job-Command";
    public static final String JOB_COMMAND_ARG = "Job-Command-Arg";
    public static final String BODY = "_body";
    public static final String CONTENT_LENGTH = "Content-Length";

    // Channel Events Parameters
    public static final String CHANNEL_CALL_UUID = "Channel-Call-UUID";
    public static final String UNIQUE_ID = "Unique-ID";
    public static final String CHANNEL_NAME = "Channel-Name";
    public static final String CHANNEL_STATE = "Channel-State";
    public static final String CALL_DIRECTION = "Call-Direction";
    public static final String CALLER_DIRECTION = "Caller-Direction";
    public static final String CALLER_CHANNEL_NAME = "Caller-Channel-Name";
    public static final String CHANNEL_CALL_STATE = "Channel-Call-State";
    public static final String OTHER_LEG_UNIQUE_ID = "Other-Leg-Unique-ID";
    public static final String BRIDGE_A_UNIQUE_ID = "Bridge-A-Unique-ID";
    public static final String BRIDGE_B_UNIQUE_ID = "Bridge-B-Unique-ID";
    public static final String HANGUP_CAUSE = "Hangup-Cause";

    // Channel Execute
    public static final String APPLICATION = "Application";
    public static final String APPLICATION_DATA = "Application-Data";
    // DTMF Event Parameters
    public static final String DTMF_DIGIT = "DTMF-Digit";
    public static final String DTMF_DURATION = "DTMF-Duration";
    public static final String DTMF_SOURCE = "DTMF-Source";
}
