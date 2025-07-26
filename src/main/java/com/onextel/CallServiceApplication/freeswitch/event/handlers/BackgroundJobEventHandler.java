package com.onextel.CallServiceApplication.freeswitch.event.handlers;

import com.onextel.CallServiceApplication.common.StringUtils;
import com.onextel.CallServiceApplication.freeswitch.event.Event;
import com.onextel.CallServiceApplication.freeswitch.event.EventParams;
import com.onextel.CallServiceApplication.freeswitch.event.EventUtils;
import com.onextel.CallServiceApplication.service.CommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This class is responsible for processing response event
 * (BACKGROUND_JOB) for bgapi command sent to FreeSwitch.
 * e.g.
 * "Event-Name":"BACKGROUND_JOB",
 * "Core-UUID":"c7f8db4c-0894-495d-a7e6-91c89db72185",
 * "FreeSWITCH-Hostname":"OL03LTW-GNR0104",
 * "FreeSWITCH-Switchname":"OL03LTW-GNR0104",
 * "FreeSWITCH-IPv4":"172.22.22.86",
 * "FreeSWITCH-IPv6":"::1",
 * "Event-Date-Local":"2025-03-25 13:50:18",
 * "Event-Date-GMT":"Tue, 25 Mar 2025 08:20:18 GMT",
 * "Event-Date-Timestamp":"1742890818554277",
 * "Event-Calling-File":"mod_commands.c",
 * "Event-Calling-Function":"bgapi_exec",
 * "Event-Calling-Line-Number":"5396",
 * "Event-Sequence":"4033",
 * "Job-UUID":"9d570c41-b3d1-4945-bbfb-7d2389407e5b",
 * "Job-Command":"originate",
 * "Job-Command-Arg":"{correlation_id=1234567890}sofia/external/1003@onextel.com
 *                      &bridge(sofia/external/1001@onextel.com) default public
 *                      'OneXTel Media Inc' 9876543210 30",
 * "Content-Length":"41",
 * "_body":"+OK 504a5b51-26f9-42de-894d-7e62a8cab329
*/
public class BackgroundJobEventHandler extends EventHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(
            BackgroundJobEventHandler.class);

    private final CommandService commandService;

    public BackgroundJobEventHandler(CommandService commandService) {
        this.commandService = commandService;
    }

    @Override
    public void handleEvent(Event event) {
        LOGGER.info("BackgroundJob - Processing event: {} eventSequence {}",
                event.getEventType(), event.getEventSequence());
        try {
            String jobCommand = event.getStringParam(EventParams.JOB_COMMAND);
            String jobArgs = event.getStringParam(EventParams.JOB_COMMAND_ARG);
            String fullCommand = jobCommand + " " + jobArgs;

            String correlationId = EventUtils.extractCorrelationId(fullCommand);
            String responseBody = event.getStringParam(EventParams.BODY);

            if (!StringUtils.isNullOrBlank(correlationId)) {
                commandService.completeCommand(correlationId, responseBody);
            } else {
                LOGGER.warn("No correlation ID found in command: {}", fullCommand);
            }

            LOGGER.debug("Processed BACKGROUND_JOB: correlationId={}, response={}",
                    correlationId, responseBody);
        } catch (Exception e) {
            LOGGER.error("Error processing FreeSWITCH event:{}", event, e);
        }
    }
}
