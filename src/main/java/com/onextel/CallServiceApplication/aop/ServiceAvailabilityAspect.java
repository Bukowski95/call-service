package com.onextel.CallServiceApplication.aop;

import com.onextel.CallServiceApplication.exception.ServiceShuttingDownException;
import com.onextel.CallServiceApplication.service.CallService;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ServiceAvailabilityAspect {

    private final CallService callService;

    public ServiceAvailabilityAspect(CallService callService) {
        this.callService = callService;
    }

    @Before("@within(com.onextel.CallServiceApplication.aop.RequireCallServiceUp.RequireCallServiceUp) || @annotation(com.onextel.CallServiceApplication.aop.RequireCallServiceUp.RequireCallServiceUp)")
    public void checkServiceAvailability() {
        if (callService.isShuttingDown()) {
            throw new ServiceShuttingDownException("Service is currently shutting down");
        }
    }
}
