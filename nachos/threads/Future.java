package nachos.threads;

import nachos.machine.*;
import java.util.function.IntSupplier;

/**
 * A <i>Future</i> is a convenient mechanism for using asynchonous
 * operations.
 */
public class Future {
    private final Lock lock;
    private final Condition2 cv;
    private boolean done;
    private int result;

    /**
     * Instantiate a new <i>Future</i>.  The <i>Future</i> will invoke
     * the supplied <i>function</i> asynchronously in a KThread.  In
     * particular, the constructor should not block as a consequence
     * of invoking <i>function</i>.
     */
    public Future(IntSupplier function) {
        lock = new Lock();
        cv = new Condition2(lock);
        done = false;

        new KThread(() -> {
            int value = function.getAsInt();
            lock.acquire();
            result = value;
            done = true;
            cv.wakeAll();
            lock.release();
        }).setName("Future-worker").fork();
    }

    /**
     * Return the result of invoking the <i>function</i> passed in to
     * the <i>Future</i> when it was created.  If the function has not
     * completed when <i>get</i> is invoked, then the caller is
     * blocked.  If the function has completed, then <i>get</i>
     * returns the result of the function.  Note that <i>get</i> may
     * be called any number of times (potentially by multiple
     * threads), and it should always return the same value.
     */
    public int get() {
        lock.acquire();
        while (!done)
            cv.sleep();
        int value = result;
        lock.release();
        return value;
    }

    public static void selfTest() {
        test1_basicResult();
        test2_blocksUntilDone();
        test3_multipleGetSameValue();
        test4_multipleThreadsGet();
    }

    // Test 1: get() returns the value produced by the function
    private static void test1_basicResult() {
        System.out.println("Future Test 1: basic result");
        Future f = new Future(() -> 42);
        int val = f.get();
        Lib.assertTrue(val == 42, "expected 42, got " + val);
        System.out.println("Future Test 1: passed");
    }

    // Test 2: get() blocks if called before the worker finishes
    private static void test2_blocksUntilDone() {
        System.out.println("Future Test 2: blocks until done");
        boolean[] getReturned = { false };

        // Worker sleeps 2000 ticks before returning, ensuring get() will have to wait
        Future f = new Future(() -> {
            ThreadedKernel.alarm.waitUntil(2000);
            return 7;
        });

        KThread getter = new KThread(() -> {
            int val = f.get();
            getReturned[0] = true;
            Lib.assertTrue(val == 7, "expected 7, got " + val);
        }).setName("test2-getter");

        getter.fork();
        KThread.yield(); // let getter run and block on get()

        Lib.assertTrue(!getReturned[0], "get() should still be blocked");

        getter.join(); // waits for alarm to fire and worker to finish
        Lib.assertTrue(getReturned[0], "get() should have returned after worker finished");
        System.out.println("Future Test 2: passed");
    }

    // Test 3: calling get() multiple times always returns the same value
    private static void test3_multipleGetSameValue() {
        System.out.println("Future Test 3: multiple get() calls return same value");
        Future f = new Future(() -> 99);
        for (int i = 0; i < 5; i++) {
            int val = f.get();
            Lib.assertTrue(val == 99, "call " + i + ": expected 99, got " + val);
        }
        System.out.println("Future Test 3: passed");
    }

    // Test 4: multiple threads calling get() simultaneously all receive the same value
    private static void test4_multipleThreadsGet() {
        System.out.println("Future Test 4: multiple threads on get()");
        int n = 4;
        int[] results = new int[n];

        // Worker sleeps so all getter threads are likely blocked before it finishes
        Future f = new Future(() -> {
            ThreadedKernel.alarm.waitUntil(2000);
            return 55;
        });

        KThread[] getters = new KThread[n];
        for (int i = 0; i < n; i++) {
            final int id = i;
            getters[id] = new KThread(() -> {
                results[id] = f.get();
            }).setName("test4-getter-" + id);
        }
        for (KThread t : getters) t.fork();
        for (KThread t : getters) t.join();

        for (int i = 0; i < n; i++)
            Lib.assertTrue(results[i] == 55, "getter " + i + ": expected 55, got " + results[i]);

        System.out.println("Future Test 4: passed");
    }
}
