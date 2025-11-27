package uj.wmii.pwj.exec;

import java.util.*;
import java.util.concurrent.*;

public class MyExecService implements ExecutorService {

    private final Queue<Runnable> taskQueue = new LinkedList<>();
    private final Thread workerThread;
    private volatile boolean isShutdown = false;
    private final Object monitor = new Object();

    private MyExecService() {
        workerThread = new Thread(this::workLoop);
        workerThread.setName("MyExecutor-Single-Thread");
        workerThread.start();
    }

    public static MyExecService newInstance() {
        return new MyExecService();
    }

    private void workLoop() {
        while (true) {
            Runnable task;
            synchronized (monitor) {
                while (taskQueue.isEmpty() && !isShutdown) {
                    try {
                        monitor.wait();
                    } catch (InterruptedException e) {
                        return;
                    }
                }

                if (isShutdown && taskQueue.isEmpty()) {
                    return;
                }

                task = taskQueue.poll();
            }

            if (task != null) {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    System.err.println("Exception in task: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void execute(Runnable command) {
        if (command == null) throw new NullPointerException();
        synchronized (monitor) {
            if (isShutdown) {
                throw new RejectedExecutionException("Executor is shut down");
            }
            taskQueue.add(command);
            monitor.notify();
        }
    }

    @Override
    public void shutdown() {
        synchronized (monitor) {
            isShutdown = true;
            monitor.notifyAll();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> pendingTasks;
        synchronized (monitor) {
            isShutdown = true;
            pendingTasks = new ArrayList<>(taskQueue);
            taskQueue.clear();
        }
        workerThread.interrupt();
        return pendingTasks;
    }

    @Override
    public boolean isShutdown() {
        return isShutdown;
    }

    @Override
    public boolean isTerminated() {
        return isShutdown && !workerThread.isAlive();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        workerThread.join(unit.toMillis(timeout));
        return !workerThread.isAlive();
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) throw new NullPointerException();
        FutureTask<T> ftask = new FutureTask<>(task);
        execute(ftask);
        return ftask;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (task == null) throw new NullPointerException();
        FutureTask<T> ftask = new FutureTask<>(task, result);
        execute(ftask);
        return ftask;
    }

    @Override
    public Future<?> submit(Runnable task) {
        if (task == null) throw new NullPointerException();
        FutureTask<Void> ftask = new FutureTask<>(task, null);
        execute(ftask);
        return ftask;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        List<Future<T>> futures = new ArrayList<>();
        for (Callable<T> task : tasks) {
            futures.add(submit(task));
        }
        for (Future<T> f : futures) {
            try {
                if (!f.isDone()) f.get();
            } catch (ExecutionException | CancellationException ignore) {

            }
        }
        return futures;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        long deadline = System.nanoTime() + nanos;
        List<Future<T>> futures = new ArrayList<>();

        for (Callable<T> task : tasks) {
            futures.add(submit(task));
        }

        for (Future<T> f : futures) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                f.cancel(true);
            } else {
                try {
                    f.get(remaining, TimeUnit.NANOSECONDS);
                } catch (TimeoutException | ExecutionException | CancellationException e) {
                    // pass
                }
            }
        }
        return futures;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        ExecutionException lastException = null;
        for (Callable<T> task : tasks) {
            try {
                return task.call();
            } catch (Exception e) {
                lastException = new ExecutionException(e);
            }
        }
        throw lastException != null ? lastException : new ExecutionException(new RuntimeException("No tasks to run"));
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long end = System.currentTimeMillis() + unit.toMillis(timeout);
        ExecutionException lastException = null;
        for (Callable<T> task : tasks) {
            if (System.currentTimeMillis() > end) break;
            try {
                return task.call();
            } catch (Exception e) {
                lastException = new ExecutionException(e);
            }
        }
        if (lastException != null) throw lastException;
        throw new TimeoutException();
    }
}