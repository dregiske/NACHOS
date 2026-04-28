package nachos.threads;

import nachos.machine.*;
import java.util.PriorityQueue;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

	private PriorityQueue<WaitingThread> waitQueue;

	private static class WaitingThread implements Comparable<WaitingThread> {
		KThread thread;
		long wakeTime;

		WaitingThread(KThread thread, long wakeTime) {
			this.thread = thread;
			this.wakeTime = wakeTime;
		}

		public int compareTo(WaitingThread other) {
			return Long.compare(this.wakeTime, other.wakeTime);
		}
	}

	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		waitQueue = new PriorityQueue<>();
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		boolean intStatus = Machine.interrupt().disable();
		while(!waitQueue.isEmpty() &&
			  waitQueue.peek().wakeTime <= Machine.timer().getTime()){
			WaitingThread front = waitQueue.poll();
			front.thread.ready();
		}
		Machine.interrupt().restore(intStatus);
		KThread.currentThread().yield();
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		if (x <= 0) return;
		boolean intStatus = Machine.interrupt().disable();
		long wakeTime = Machine.timer().getTime() + x;
		this.waitQueue.add(new WaitingThread(KThread.currentThread(), wakeTime));
		KThread.sleep();
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Schedule <i>thread</i> to wake up after <i>x</i> ticks without sleeping.
	 * Interrupts must already be disabled by the caller.
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 */
	public void schedule(KThread thread, long x) {
		Lib.assertTrue(Machine.interrupt().disabled());
		long wakeTime = Machine.timer().getTime() + x;
		waitQueue.add(new WaitingThread(thread, wakeTime));
	}

	/**
	 * Removes the thread from the waitQueue pq and wakes immediately.
	 * 
	 * @param thread the thread to cancel
	 * @return true for valid thread, otherwise false
	 */
	public boolean cancel(KThread thread) {
		boolean intStatus = Machine.interrupt().disable();

		WaitingThread found = null;
		for (WaitingThread wt : waitQueue) {
			if (wt.thread == thread) {
				found = wt;
				break;
			}
		}

		if (found != null) {
			waitQueue.remove(found);
			found.thread.ready();
			Machine.interrupt().restore(intStatus);
			return true;
		}

		Machine.interrupt().restore(intStatus);
		return false;
	}

    /**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true.  If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * @param thread the thread whose timer should be cancelled.
	 */
	public static void selfTest() {
		testAlarm1_waitsDuration();
		testAlarm2_zeroNegativeNoWait();
		testAlarm3_multipleOrdering();
	}

	// Test 1: thread waits at least x ticks
	private static void testAlarm1_waitsDuration() {
		System.out.println("Alarm Test 1: thread waits for requested duration");
		long start = Machine.timer().getTime();
		ThreadedKernel.alarm.waitUntil(1000);
		long elapsed = Machine.timer().getTime() - start;
		Lib.assertTrue(elapsed >= 1000);
		System.out.println("PASSED: elapsed = " + elapsed);
	}

	// Test 2: waitUntil(0) and waitUntil(negative) return immediately
	private static void testAlarm2_zeroNegativeNoWait() {
		System.out.println("Alarm Test 2: waitUntil(0) and waitUntil(-x) do not block");
		long start = Machine.timer().getTime();
		ThreadedKernel.alarm.waitUntil(0);
		ThreadedKernel.alarm.waitUntil(-500);
		long elapsed = Machine.timer().getTime() - start;
		Lib.assertTrue(elapsed == 0);
		System.out.println("PASSED: no wait for non-positive durations");
	}

	// Test 3: multiple threads wake in ascending order of their delay
	private static void testAlarm3_multipleOrdering() {
		System.out.println("Alarm Test 3: multiple threads wake in time order");
		int[] wakeOrder = new int[3];
		int[] idx = {0};

		KThread t1 = new KThread(new Runnable() {
			public void run() {
				ThreadedKernel.alarm.waitUntil(2000);
				wakeOrder[idx[0]++] = 2000;
			}
		}).setName("t1-2000");

		KThread t2 = new KThread(new Runnable() {
			public void run() {
				ThreadedKernel.alarm.waitUntil(500);
				wakeOrder[idx[0]++] = 500;
			}
		}).setName("t2-500");

		KThread t3 = new KThread(new Runnable() {
			public void run() {
				ThreadedKernel.alarm.waitUntil(1000);
				wakeOrder[idx[0]++] = 1000;
			}
		}).setName("t3-1000");

		t1.fork(); t2.fork(); t3.fork();
		t1.join(); t2.join(); t3.join();

		Lib.assertTrue(wakeOrder[0] == 500);
		Lib.assertTrue(wakeOrder[1] == 1000);
		Lib.assertTrue(wakeOrder[2] == 2000);
		System.out.println("PASSED: wake order was 500, 1000, 2000");
	}
}
