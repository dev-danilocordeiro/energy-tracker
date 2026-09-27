package com.devcordeiro.device_service.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Pointcut("execution(* com.devcordeiro.device_service.service..*(..))")
    public void serviceMethods() {}

    // Arguments and results carry personal data (e-mail, address), so they only go to DEBUG
    @Before("serviceMethods()")
    public void logBefore(JoinPoint joinPoint) {
        log.info("Called service method: {}", joinPoint.getSignature().getName());
        log.debug("Service method {} arguments: {}",
                joinPoint.getSignature().getName(), joinPoint.getArgs());
    }

    @AfterReturning(pointcut = "serviceMethods()", returning = "result")
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        log.info("Service method {} returned", joinPoint.getSignature().getName());
        log.debug("Service method {} returned: {}",
                joinPoint.getSignature().getName(), result);
    }
}
