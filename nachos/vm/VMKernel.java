package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.LinkedList;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		// One global swap file for the lifetime of the kernel. Units are pages.
		swapFile = ThreadedKernel.fileSystem.open(swapFileName, true);
		Lib.assertTrue(swapFile != null, "could not create swap file");
		freeSwapSlots = new LinkedList<Integer>();
		swapSize = 0;
		swapLock = new Lock();

		// Global inverted page table: one slot per physical frame. Lets eviction
		// go PPN -> (owning process, its page-table entry).
		ipt = new IPTEntry[Machine.processor().getNumPhysPages()];
		clockHand = 0;
		vmLock = new Lock();
		pinnedCount = 0;
		unpinCondition = new Condition(vmLock);
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		// Drop the swap file; it should not outlive the kernel.
		if (swapFile != null) {
			swapFile.close();
			ThreadedKernel.fileSystem.remove(swapFileName);
			swapFile = null;
		}
		super.terminate();
	}

	/**
	 * Reserve a free page-sized slot in the swap file, growing the file if no
	 * previously freed slot is available.
	 *
	 * @return the slot number (its byte offset in the file is slot * pageSize).
	 */
	public static int allocateSwapSlot() {
		swapLock.acquire();
		int slot = freeSwapSlots.isEmpty() ? swapSize++ : freeSwapSlots.removeFirst();
		swapLock.release();
		return slot;
	}

	/**
	 * Return a swap slot to the free list so a future page can reuse it.
	 */
	public static void freeSwapSlot(int slot) {
		swapLock.acquire();
		freeSwapSlots.add(slot);
		swapLock.release();
	}

	/**
	 * Page out: copy the contents of physical frame <tt>ppn</tt> to swap slot
	 * <tt>slot</tt>.
	 */
	public static void swapOut(int ppn, int slot) {
		byte[] memory = Machine.processor().getMemory();
		int written = swapFile.write(slot * pageSize, memory, ppn * pageSize, pageSize);
		Lib.assertTrue(written == pageSize, "swap out write failed");
	}

	/**
	 * Page in: copy the contents of swap slot <tt>slot</tt> into physical frame
	 * <tt>ppn</tt>.
	 */
	public static void swapIn(int slot, int ppn) {
		byte[] memory = Machine.processor().getMemory();
		int read = swapFile.read(slot * pageSize, memory, ppn * pageSize, pageSize);
		Lib.assertTrue(read == pageSize, "swap in read failed");
	}

	/**
	 * One entry per physical frame. Records which process owns the frame and a
	 * reference to that process's page-table entry for the resident page.
	 */
	public static class IPTEntry {
		VMProcess process;
		TranslationEntry entry;
		boolean pinned;

		IPTEntry(VMProcess process, TranslationEntry entry) {
			this.process = process;
			this.entry = entry;
			this.pinned = false;
		}
	}

	/**
	 * Record that physical frame <tt>ppn</tt> now holds <tt>entry</tt> belonging
	 * to <tt>process</tt>. Caller must hold {@link #vmLock}.
	 */
	public static void registerPage(int ppn, VMProcess process, TranslationEntry entry) {
		ipt[ppn] = new IPTEntry(process, entry);
	}

	/**
	 * Clear the inverted-page-table entry for physical frame <tt>ppn</tt> (e.g.
	 * when its owning process terminates). Caller must hold {@link #vmLock}.
	 */
	public static void clearPage(int ppn) {
		ipt[ppn] = null;
	}

	/** Pin frame <tt>ppn</tt> so the clock algorithm skips it. Caller holds vmLock. */
	public static void pinPage(int ppn) {
		ipt[ppn].pinned = true;
		pinnedCount++;
	}

	/** Unpin frame <tt>ppn</tt> and wake any thread blocked in evictPage. Caller holds vmLock. */
	public static void unpinPage(int ppn) {
		ipt[ppn].pinned = false;
		pinnedCount--;
		unpinCondition.wake();
	}

	/**
	 * Select a victim frame with the clock algorithm, evict its page to swap, and
	 * return the now-free physical page number. Caller must hold {@link #vmLock}.
	 *
	 * The clock hand sweeps the inverted page table: a page whose used bit is set
	 * gets a second chance (the bit is cleared and the hand advances); the first
	 * page found with a clear used bit is evicted.
	 *
	 * @return the freed physical page number.
	 */
	public static int evictPage() {
		// Block if every physical frame is currently pinned 
		while (pinnedCount == ipt.length)
			unpinCondition.sleep();

		while (true) {
			IPTEntry e = ipt[clockHand];
			if (e == null || e.pinned || e.entry.used) {
				if (e != null && e.entry.used)
					e.entry.used = false;
				clockHand = (clockHand + 1) % ipt.length;
				continue;
			}
			break;
		}

		int victimPpn = clockHand;
		IPTEntry victim = ipt[victimPpn];
		clockHand = (clockHand + 1) % ipt.length;

		// Pin the victim and invalidate its TE before releasing the lock.
		// Keep ipt[victimPpn] alive so the pinned flag is visible to the clock.
		pinPage(victimPpn);
		victim.entry.valid = false;
		victim.entry.ppn = -1;

		if (victim.entry.dirty) {
			int existingSlot = victim.process.getSwapSlot(victim.entry.vpn);
			int slot = (existingSlot >= 0) ? existingSlot : allocateSwapSlot();
			vmLock.release();
			swapOut(victimPpn, slot);
			vmLock.acquire();
			victim.process.recordSwap(victim.entry.vpn, slot);
		}

		// Unpin first (unpinPage reads ipt[ppn]), then clear the IPT entry.
		unpinPage(victimPpn);
		ipt[victimPpn] = null;

		return victimPpn;
	}

	/** The single global swap file, divided into page-sized slots. */
	private static OpenFile swapFile;

	/** Free list of reusable swap slots (gaps left by terminated processes). */
	private static LinkedList<Integer> freeSwapSlots;

	/** Number of slots ever handed out; next slot to grow into. */
	private static int swapSize;

	/** Protects the swap-slot free list. */
	private static Lock swapLock;

	/** Inverted page table: indexed by physical page number. */
	private static IPTEntry[] ipt;

	/** Current position of the clock algorithm's hand (a physical frame index). */
	private static int clockHand;

	/** Coarse global lock protecting the IPT, clock hand, and page-fault path. */
	static Lock vmLock;

	/** Number of physical frames currently pinned for in-flight I/O. */
	static int pinnedCount;

	/** Woken when a frame is unpinned, allowing a blocked evictPage to proceed. */
	static Condition unpinCondition;

	private static final String swapFileName = "nachos.swap";

	private static final int pageSize = Processor.pageSize;

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';
}
