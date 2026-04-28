package nachos.threads;

import nachos.machine.*;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {

    private static class Slot {
        Lock lock;
        Condition2 cv;
        int firstValue;
        int secondValue;

        Slot(int value) {
            lock = new Lock();
            cv = new Condition2(lock);
            firstValue = value;
        }
    }

    private HashMap<Integer, LinkedList<Slot>> pending;
    private Lock mapLock;

    /**
     * Allocate a new Rendezvous.
     */
    public Rendezvous() {
        pending = new HashMap<>();
        mapLock = new Lock();
    }

    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange(int tag, int value) {
        mapLock.acquire();
        if (!pending.containsKey(tag)) {
            pending.put(tag, new LinkedList<>());
        }
        LinkedList<Slot> queue = pending.get(tag);

        if (queue.isEmpty()) {
            Slot slot = new Slot(value);
            slot.lock.acquire();
            queue.add(slot);
            mapLock.release();
            slot.cv.sleep();
            int result = slot.secondValue;
            slot.lock.release();
            return result;
        } else {
            Slot slot = queue.removeFirst();
            slot.lock.acquire();
            mapLock.release();
            slot.secondValue = value;
            slot.cv.wake();
            int res = slot.firstValue;
            slot.lock.release();
            return res;
        }
    }

    public static void selfTest() {
        test1_blocksUntilMatched();
        test2_correctValues();
        test3_manyThreadsSameTag();
        test4_differentTagsIndependent();
        test5_differentInstancesIndependent();
    }

    // Test 1: a thread blocks in exchange until another thread joins it
    private static void test1_blocksUntilMatched() {
        System.out.println("Rendezvous Test 1: blocks until matched");
        Rendezvous r = new Rendezvous();
        int[] returned = {0};

        KThread tA = new KThread(new Runnable() {
            public void run() {
                r.exchange(0, 1);
                returned[0] = 1;
            }
        }).setName("test1-A");
        tA.fork();
        KThread.yield();

        Lib.assertTrue(returned[0] == 0, "thread A should still be blocked");

        KThread tB = new KThread(new Runnable() {
            public void run() { r.exchange(0, 2); }
        }).setName("test1-B");
        tB.fork();
        tA.join();
        tB.join();

        Lib.assertTrue(returned[0] == 1, "thread A should have returned after B arrived");
        System.out.println("Rendezvous Test 1: passed");
    }

    // Test 2: threads receive each other's values
    private static void test2_correctValues() {
        System.out.println("Rendezvous Test 2: correct values exchanged");
        Rendezvous r = new Rendezvous();
        int[] aGot = {-1};
        int[] bGot = {-1};

        KThread tA = new KThread(new Runnable() {
            public void run() { aGot[0] = r.exchange(0, 10); }
        }).setName("test2-A");
        KThread tB = new KThread(new Runnable() {
            public void run() { bGot[0] = r.exchange(0, 20); }
        }).setName("test2-B");

        tA.fork();
        tB.fork();
        tA.join();
        tB.join();

        Lib.assertTrue(aGot[0] == 20, "A should receive B's value (20)");
        Lib.assertTrue(bGot[0] == 10, "B should receive A's value (10)");
        System.out.println("Rendezvous Test 2: passed");
    }

    // Test 3: many threads on the same tag are correctly paired
    private static void test3_manyThreadsSameTag() {
        System.out.println("Rendezvous Test 3: many threads same tag");
        Rendezvous r = new Rendezvous();
        int n = 6; // must be even
        int[] sent = new int[n];
        int[] got  = new int[n];
        KThread[] threads = new KThread[n];

        for (int i = 0; i < n; i++) {
            final int id = i;
            sent[id] = id + 1;
            threads[id] = new KThread(new Runnable() {
                public void run() { got[id] = r.exchange(0, sent[id]); }
            }).setName("test3-" + id);
        }
        for (KThread t : threads) t.fork();
        for (KThread t : threads) t.join();

        // Every thread must have received a value different from its own
        for (int i = 0; i < n; i++)
            Lib.assertTrue(got[i] != sent[i], "thread " + i + " got its own value back");

        // The multiset of received values must equal the multiset of sent values
        int sentSum = 0, gotSum = 0;
        for (int i = 0; i < n; i++) { sentSum += sent[i]; gotSum += got[i]; }
        Lib.assertTrue(sentSum == gotSum, "received values don't match sent values");

        System.out.println("Rendezvous Test 3: passed");
    }

    // Test 4: threads on different tags do not interact
    private static void test4_differentTagsIndependent() {
        System.out.println("Rendezvous Test 4: different tags independent");
        Rendezvous r = new Rendezvous();
        int[] aGot = {-1}, bGot = {-1}, cGot = {-1}, dGot = {-1};

        // A and C share tag 0; B and D share tag 1
        KThread tA = new KThread(new Runnable() {
            public void run() { aGot[0] = r.exchange(0, 1); }
        }).setName("test4-A");
        KThread tB = new KThread(new Runnable() {
            public void run() { bGot[0] = r.exchange(1, 2); }
        }).setName("test4-B");
        KThread tC = new KThread(new Runnable() {
            public void run() { cGot[0] = r.exchange(0, 3); }
        }).setName("test4-C");
        KThread tD = new KThread(new Runnable() {
            public void run() { dGot[0] = r.exchange(1, 4); }
        }).setName("test4-D");

        tA.fork(); tB.fork(); tC.fork(); tD.fork();
        tA.join(); tB.join(); tC.join(); tD.join();

        // Tag 0 pair: A(1) <-> C(3)
        Lib.assertTrue(aGot[0] == 3, "A should get C's value (3), got " + aGot[0]);
        Lib.assertTrue(cGot[0] == 1, "C should get A's value (1), got " + cGot[0]);
        // Tag 1 pair: B(2) <-> D(4)
        Lib.assertTrue(bGot[0] == 4, "B should get D's value (4), got " + bGot[0]);
        Lib.assertTrue(dGot[0] == 2, "D should get B's value (2), got " + dGot[0]);
        System.out.println("Rendezvous Test 4: passed");
    }

    // Test 5: threads on different Rendezvous instances do not interact
    private static void test5_differentInstancesIndependent() {
        System.out.println("Rendezvous Test 5: different instances independent");
        Rendezvous r1 = new Rendezvous();
        Rendezvous r2 = new Rendezvous();
        int[] aGot = {-1}, bGot = {-1}, cGot = {-1}, dGot = {-1};

        // A and C are on r1; B and D are on r2 — must not pair cross-instance
        KThread tA = new KThread(new Runnable() {
            public void run() { aGot[0] = r1.exchange(0, 10); }
        }).setName("test5-A");
        KThread tB = new KThread(new Runnable() {
            public void run() { bGot[0] = r2.exchange(0, 20); }
        }).setName("test5-B");
        KThread tC = new KThread(new Runnable() {
            public void run() { cGot[0] = r1.exchange(0, 30); }
        }).setName("test5-C");
        KThread tD = new KThread(new Runnable() {
            public void run() { dGot[0] = r2.exchange(0, 40); }
        }).setName("test5-D");

        tA.fork(); tB.fork(); tC.fork(); tD.fork();
        tA.join(); tB.join(); tC.join(); tD.join();

        // r1 pair: A(10) <-> C(30)
        Lib.assertTrue(aGot[0] == 30, "A should get C's value (30), got " + aGot[0]);
        Lib.assertTrue(cGot[0] == 10, "C should get A's value (10), got " + cGot[0]);
        // r2 pair: B(20) <-> D(40)
        Lib.assertTrue(bGot[0] == 40, "B should get D's value (40), got " + bGot[0]);
        Lib.assertTrue(dGot[0] == 20, "D should get B's value (20), got " + dGot[0]);
        System.out.println("Rendezvous Test 5: passed");
    }
}
