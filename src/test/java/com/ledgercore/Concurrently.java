package com.ledgercore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Runs test tasks on several threads at once and waits for all of them. */
public final class Concurrently {

    private Concurrently() {
    }

    /** One thread per task, all released at the same instant. */
    public static <T> List<T> run(List<Callable<T>> tasks) throws Exception {
        return run(tasks, tasks.size());
    }

    /**
     * At most maxThreads run at once. When every task gets its own thread they are held at a starting line and
     * released together; with more tasks than threads they simply flow through the pool.
     */
    public static <T> List<T> run(List<Callable<T>> tasks, int maxThreads) throws Exception {
        if (tasks.isEmpty()) {
            return List.of();
        }
        int threads = Math.min(maxThreads, tasks.size());
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            boolean gated = threads == tasks.size();
            CountDownLatch ready = new CountDownLatch(gated ? threads : 0);
            CountDownLatch go = new CountDownLatch(gated ? 1 : 0);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            if (!ready.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The threads did not all start in time");
            }
            go.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(120, TimeUnit.SECONDS));   // a deadlock shows up here as a timeout
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}