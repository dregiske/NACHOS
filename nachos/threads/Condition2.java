package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 * The current thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean status = Machine.interrupt().disable();

		waitQueue.add(KThread.currentThread());
		conditionLock.release();
		KThread.sleep();

		Machine.interrupt().restore(status);

		conditionLock.acquire();
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		boolean status = Machine.interrupt().disable();

		if (!waitQueue.isEmpty()) {
			KThread thread = waitQueue.removeFirst();
			if (!ThreadedKernel.alarm.cancel(thread)) {
				thread.ready();
			}
		}

		Machine.interrupt().restore(status);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		while (!waitQueue.isEmpty()) {
			wake();
		}
	}

        /**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses.  The current thread must hold the
	 * associated lock.  The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
    public void sleepFor(long timeout) {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		if (timeout <= 0) return;

		boolean intStatus = Machine.interrupt().disable();

		waitQueue.add(KThread.currentThread());
		conditionLock.release();
		ThreadedKernel.alarm.schedule(KThread.currentThread(), timeout);
		KThread.sleep();

		waitQueue.remove(KThread.currentThread());
		Machine.interrupt().restore(intStatus);

		conditionLock.acquire();
	}

        private Lock conditionLock;
		private LinkedList<KThread> waitQueue = new LinkedList<KThread>();

	public static void selfTest() {
		test1_sleepBlocks();
		test2_wakeOne();
		test3_wakeAll();
		test4_lostWake();
		test5_sleepForTimeout();
		test6_sleepForWokenEarly();
		test7_sleepForMultiple();
	}

	// Test 1: sleep actually blocks
	private static void test1_sleepBlocks() {
		System.out.println(" Test 1 ");
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		KThread t = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				System.out.println("child: sleeping");
				cv.sleep();
				System.out.println("child: woke up");
				lock.release();
			}
		}).setName("child");

		t.fork();
		KThread.yield();

		// child should be asleep now, this prints before "woke up"
		System.out.println("main: about to wake");
		lock.acquire();
		cv.wake();
		lock.release();
		t.join();
	}

	// Test 2: wake only wakes ONE thread
	private static void test2_wakeOne() {
		System.out.println(" Test 2 ");
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		KThread t1 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				cv.sleep();
				System.out.println("t1 woke up");
				lock.release();
			}
		}).setName("t1");

		KThread t2 = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				cv.sleep();
				System.out.println("t2 woke up");
				lock.release();
			}
		}).setName("t2");

		t1.fork();
		t2.fork();
		KThread.yield();
		KThread.yield();

		lock.acquire();
		System.out.println("main: calling wake once");
		cv.wake();
		// only one should print
		cv.wake(); // wake the other so test can finish
		lock.release();
		t1.join();
		t2.join();
	}

	// Test 3: wakeAll wakes everyone
	private static void test3_wakeAll() {
		System.out.println(" Test 3 ");
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		for (int i = 0; i < 3; i++) {
			final int id = i;
			KThread t = new KThread(new Runnable() {
				public void run() {
					lock.acquire();
					cv.sleep();
					System.out.println("thread " + id + " woke up");
					lock.release();
				}
			});
			t.setName("thread" + i);
			t.fork();
		}

		KThread.yield();
		KThread.yield();
		KThread.yield();

		lock.acquire();
		System.out.println("main: calling wakeAll");
		cv.wakeAll();
		lock.release();

		// yield to let them all finish
		for (int i = 0; i < 5; i++)
			KThread.yield();
	}

	// Test 5: sleepFor returns after timeout if no wake() is called
	private static void test5_sleepForTimeout() {
		System.out.println("Test 5: sleepFor times out after x ticks");
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		long[] elapsed = {0};

		KThread t = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				long start = Machine.timer().getTime();
				cv.sleepFor(2000);
				elapsed[0] = Machine.timer().getTime() - start;
				lock.release();
			}
		}).setName("sleepFor-timeout");

		t.fork();
		t.join();

		Lib.assertTrue(elapsed[0] >= 2000);
		System.out.println("PASSED: woke after " + elapsed[0] + " ticks (>= 2000)");
	}

	// Test 6: sleepFor returns early when wake() is called before timeout
	private static void test6_sleepForWokenEarly() {
		System.out.println("Test 6: sleepFor woken early by wake()");
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		long[] elapsed = {0};

		KThread sleeper = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				long start = Machine.timer().getTime();
				cv.sleepFor(10000);
				elapsed[0] = Machine.timer().getTime() - start;
				lock.release();
			}
		}).setName("sleeper");

		KThread waker = new KThread(new Runnable() {
			public void run() {
				ThreadedKernel.alarm.waitUntil(500);
				lock.acquire();
				cv.wake();
				lock.release();
			}
		}).setName("waker");

		sleeper.fork();
		waker.fork();
		sleeper.join();
		waker.join();

		Lib.assertTrue(elapsed[0] < 10000);
		System.out.println("PASSED: woke after " + elapsed[0] + " ticks (< 10000)");
	}

	// Test 7: multiple threads with different timeouts all woken by wakeAll
	private static void test7_sleepForMultiple() {
		System.out.println("Test 7: sleepFor multiple threads woken by wakeAll");
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
		int[] wokeCount = {0};

		long[] timeouts = {1000, 2000, 3000};
		KThread[] threads = new KThread[3];

		for (int i = 0; i < 3; i++) {
			final long timeout = timeouts[i];
			final int id = i;
			threads[i] = new KThread(new Runnable() {
				public void run() {
					lock.acquire();
					cv.sleepFor(timeout);
					wokeCount[0]++;
					System.out.println("thread " + id + " woke (timeout=" + timeout + ")");
					lock.release();
				}
			}).setName("t" + i);
			threads[i].fork();
		}

		KThread.yield();
		KThread.yield();
		KThread.yield();

		lock.acquire();
		cv.wakeAll();
		lock.release();

		for (KThread t : threads) t.join();

		Lib.assertTrue(wokeCount[0] == 3);
		System.out.println("PASSED: all 3 threads woke");
	}

	// Test 4: wake with no waiters is lost
	private static void test4_lostWake() {
		System.out.println(" Test 4 ");
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);

		// wake with nobody waiting
		lock.acquire();
		cv.wake();
		lock.release();

		// now sleep — should still block
		KThread t = new KThread(new Runnable() {
			public void run() {
				lock.acquire();
				System.out.println("child: sleeping (should block)");
				cv.sleep();
				System.out.println("child: woke up");
				lock.release();
			}
		}).setName("child");

		t.fork();
		KThread.yield();

		System.out.println("main: child is blocked, waking now");
		lock.acquire();
		cv.wake();
		lock.release();
		t.join();
	}
}
