package com.wuya.mybatis.optimizer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncSqlAnalysisExecutor {
    private final ThreadPoolExecutor executor;

    public AsyncSqlAnalysisExecutor(int poolSize) {
        BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>(1000);
        this.executor = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                60L, TimeUnit.SECONDS,
                taskQueue,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public void submit(Runnable task) {
        executor.execute(task);
    }

    public void shutdown() {
        executor.shutdown();
    }
}