package com.onextel.CallServiceApplication.common.shutdown;

import com.onextel.CallServiceApplication.exception.ServiceShuttingDownException;
import com.onextel.CallServiceApplication.service.CallService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ShutdownGuard {
    @Autowired
    private CallService callService;

    public void check() {
        if (callService.isShuttingDown()) {
            throw new ServiceShuttingDownException("Service is currently shutting down");
        }
    }
}