package com.ott.common.web.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Aspect
@Component
@Slf4j
public class PerformanceLoggingAspect {

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object logController(ProceedingJoinPoint joinPoint) throws Throwable {
        return logExecution(joinPoint, "CONTROLLER");
    }

    @Around("within(@org.springframework.stereotype.Service *)")
    public Object logService(ProceedingJoinPoint joinPoint) throws Throwable {
        return logExecution(joinPoint, "SERVICE");
    }

    private Object logExecution(ProceedingJoinPoint joinPoint, String layer) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        try {
            return joinPoint.proceed();
        } finally {
            stopWatch.stop();
            log.info("[{}] {}.{}() — {}ms",
                    layer,
                    joinPoint.getTarget().getClass().getSimpleName(),
                    joinPoint.getSignature().getName(),
                    stopWatch.getTotalTimeMillis());
        }
    }
}
