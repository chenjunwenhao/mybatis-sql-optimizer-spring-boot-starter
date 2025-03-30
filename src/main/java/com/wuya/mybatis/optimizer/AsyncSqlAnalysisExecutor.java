package com.wuya.mybatis.optimizer;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 异步SQL分析执行器类
 * 使用线程池来执行异步SQL分析任务
 */
public class AsyncSqlAnalysisExecutor {
    
    private final ThreadPoolExecutor executor;

    /**
     * 构造函数，初始化线程池
     *
     * @param poolSize       线程池大小，决定了同时可以执行的线程数量
     * @param asyncQueueSize
     */
    public AsyncSqlAnalysisExecutor(int poolSize, int asyncQueueSize) {
        // 创建阻塞队列，用于存储等待执行的任务
        BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>(asyncQueueSize);
        // 初始化线程池
        // 核心线程数和最大线程数都设置为poolSize，保持线程池大小恒定
        // 空闲线程存活时间为60秒，若无任务执行则自动终止
        // 使用AbortPolicy处理超出线程池的任务，直接抛出异常
        this.executor = new ThreadPoolExecutor(
                poolSize,
                poolSize,
                60L, TimeUnit.SECONDS,
                taskQueue,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /**
     * 提交任务到线程池执行
     * @param task 要执行的任务，实现Runnable接口的对象
     */
    public void submit(Runnable task) {
        executor.execute(task);
    }

    /**
     * 关闭线程池
     * 停止接收新任务，并等待所有已提交的任务完成执行后关闭
     */
    public void shutdown() {
        executor.shutdown();
    }
}
