package uj.wmii.pwj.exec;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestNew {
    @Test
    void testExecute() {
        MyExecService s = MyExecService.newInstance();
        TestRunnableNew r = new TestRunnableNew();
        s.execute(r);
        doSleep(50);
        assertTrue(r.wasRun);
        s.shutdown();
    }

    @Test
    void testSubmitRunnable() throws Exception {
        MyExecService s = MyExecService.newInstance();
        TestRunnableNew r = new TestRunnableNew();
        Future<?> f = s.submit(r);
        assertNull(f.get()); // Runnable returns null
        assertTrue(r.wasRun);
        s.shutdown();
    }

    @Test
    void testSubmitRunnableWithResult() throws Exception {
        MyExecService s = MyExecService.newInstance();
        TestRunnableNew r = new TestRunnableNew();
        String expected = "DONE";
        Future<String> f = s.submit(r, expected);
        assertEquals(expected, f.get());
        assertTrue(r.wasRun);
        s.shutdown();
    }

    @Test
    void testSubmitCallable() throws Exception {
        MyExecService s = MyExecService.newInstance();
        Future<Integer> f = s.submit(() -> 123);
        assertEquals(123, f.get());
        s.shutdown();
    }

    @Test
    void testShutdownAndAwait() throws InterruptedException {
        MyExecService s = MyExecService.newInstance();
        s.execute(new TestRunnableNew());
        assertFalse(s.isShutdown());
        s.shutdown();
        assertTrue(s.isShutdown());
        boolean terminated = s.awaitTermination(1, TimeUnit.SECONDS);
        assertTrue(terminated);
        assertTrue(s.isTerminated());
    }

    @Test
    void testShutdownNow() {
        MyExecService s = MyExecService.newInstance();
        s.execute(() -> doSleep(200));
        s.execute(new TestRunnableNew());
        s.execute(new TestRunnableNew());
        List<Runnable> notExecuted = s.shutdownNow();
        assertEquals(2, notExecuted.size());
        assertTrue(s.isShutdown());
        assertThrows(RejectedExecutionException.class, () -> s.execute(new TestRunnableNew()));
    }

    @Test
    void testExceptionInTaskDoesNotKillThread() throws InterruptedException {
        MyExecService s = MyExecService.newInstance();
        s.execute(() -> { throw new RuntimeException("Boom"); });
        TestRunnableNew r = new TestRunnableNew();
        s.execute(r);
        doSleep(50);
        assertTrue(r.wasRun, "Executor powinien kontynuować prace po bledzie w zad");
        s.shutdown();
    }

    @Test
    void testInvokeAll() throws InterruptedException, ExecutionException {
        MyExecService s = MyExecService.newInstance();
        List<Callable<String>> tasks = Arrays.asList(
                () -> "A",
                () -> "B",
                () -> "C"
        );

        List<Future<String>> futures = s.invokeAll(tasks);
        assertEquals(3, futures.size());
        assertEquals("A", futures.get(0).get());
        assertEquals("B", futures.get(1).get());
        assertEquals("C", futures.get(2).get());
        s.shutdown();
    }

    @Test
    void testInvokeAny() throws InterruptedException, ExecutionException {
        MyExecService s = MyExecService.newInstance();
        List<Callable<String>> tasks = Arrays.asList(
                () -> { throw new RuntimeException("Fail 1"); },
                () -> "Success",
                () -> { throw new RuntimeException("Fail 2"); }
        );
        String result = s.invokeAny(tasks);
        assertEquals("Success", result);
        s.shutdown();
    }

    static void doSleep(int milis) {
        try {
            Thread.sleep(milis);
        } catch (InterruptedException e) {
            // pass
        }
    }
}

class TestRunnableNew implements Runnable {
    volatile boolean wasRun = false;
    @Override
    public void run() {
        wasRun = true;
    }
}