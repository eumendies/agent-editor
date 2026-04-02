package com.agent.editor.agent.v2.core.context;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 只对白名单上下文构造方法做返回值压缩，避免把特殊时序的方法误纳入统一增强。
 */
@Aspect
@Component
public class ContextMemoryCompressionAspect {

    @Around("@annotation(com.agent.editor.agent.v2.core.context.CompressContextMemory) && target(factory)")
    public Object compressReturnedContext(ProceedingJoinPoint joinPoint,
                                          MemoryCompressionCapableContextFactory factory) throws Throwable {
        Object result = joinPoint.proceed();
        if (!(result instanceof AgentRunContext context)) {
            return result;
        }
        return context.withMemory(factory.memoryCompressor().compressOrOriginal(context.getMemory()));
    }
}
