package com.atomist.concurrent;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.MDC;

public class MdcThreadPoolExecutor extends ThreadPoolExecutor {

    public static ExecutorService newFixedThreadPool(int nThreads, String name) {
        BasicThreadFactory factory = new BasicThreadFactory.Builder().namingPattern(name + "-%d")
                .daemon(true).priority(Thread.MAX_PRIORITY).build();
        return new MdcThreadPoolExecutor(nThreads, nThreads, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(), factory);
    }

    private MdcThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime,
            TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory factory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, factory);

    }

    private Map<String, String> getContextForTask() {
        return MDC.getCopyOfContextMap();
    }

    @Override
    public void execute(Runnable command) {
        super.execute(wrap(command, getContextForTask()));
    }

    public Runnable wrap(Runnable runnable, Map<String, String> context) {
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            if (context == null) {
                MDC.clear();
            }
            else {
                MDC.setContextMap(context);
            }
            try {
                runnable.run();
            }
            finally {
                if (previous == null) {
                    MDC.clear();
                }
                else {
                    MDC.setContextMap(previous);
                }
            }
        };
    }
}
