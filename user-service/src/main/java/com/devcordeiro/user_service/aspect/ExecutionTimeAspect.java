package com.devcordeiro.user_service.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
@Slf4j
public class ExecutionTimeAspect {

    @Pointcut("execution(* com.devcordeiro.user_service.controller..*(..))")
    public void controllerMethods() {}

    @Around("controllerMethods()")
    public Object measureExecutionTime(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            long end = System.nanoTime();
            long elapedNs = end - start;
            long durationMs = TimeUnit.NANOSECONDS.toMillis(elapedNs);
            String signature = pjp.getSignature().toShortString();
            log.info("Controller method {} executed in {} ms", signature, durationMs);
        }
    }
}
