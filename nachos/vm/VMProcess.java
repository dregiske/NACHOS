package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.Arrays;
import java.util.HashMap;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		pageTable = new TranslationEntry[numPages];

		for (int i = 0; i < numPages; i++) {
			pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		VMKernel.vmLock.acquire();

		// Free every physical frame this process currently holds, clearing the
		// inverted page table so the clock no longer considers it ours.
		for (int i = 0; i < numPages; i++) {
			if (pageTable[i] != null && pageTable[i].valid) {
				int ppn = pageTable[i].ppn;
				VMKernel.clearPage(ppn);
				UserKernel.freePage(ppn);
				pageTable[i].valid = false;
				pageTable[i].ppn = -1;
			}
		}

		// Free every swap slot this process allocated.
		for (int slot : swapTable.values())
			VMKernel.freeSwapSlot(slot);
		swapTable.clear();

		VMKernel.vmLock.release();

		coff.close();
	}

	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		int transferred = 0;

		while (length > 0) {
			int vpn = vaddr / pageSize;
			int pageOffset = vaddr % pageSize;

			if (vpn < 0 || vpn >= pageTable.length || pageTable[vpn] == null)
				break;

			VMKernel.vmLock.acquire();

			if (!pageTable[vpn].valid)
				handlePageFaultLocked(vpn);

			if (!pageTable[vpn].valid) {
				VMKernel.vmLock.release();
				break;
			}

			int ppn = pageTable[vpn].ppn;
			int physAddr = ppn * pageSize + pageOffset;

			int amount = Math.min(length, pageSize - pageOffset);
			System.arraycopy(memory, physAddr, data, offset, amount);

			VMKernel.vmLock.release();

			vaddr += amount;
			offset += amount;
			length -= amount;
			transferred += amount;
		}

		return transferred;
	}

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();
		int transferred = 0;

		while (length > 0) {
			int vpn = vaddr / pageSize;
			int pageOffset = vaddr % pageSize;

			if (vpn < 0 || vpn >= pageTable.length || pageTable[vpn] == null)
				break;


			VMKernel.vmLock.acquire();

			if (!pageTable[vpn].valid)
				handlePageFaultLocked(vpn);

			if (!pageTable[vpn].valid || pageTable[vpn].readOnly) {
				VMKernel.vmLock.release();
				break;
			}

			int ppn = pageTable[vpn].ppn;
			int physAddr = ppn * pageSize + pageOffset;

			int amount = Math.min(length, pageSize - pageOffset);
			System.arraycopy(data, offset, memory, physAddr, amount);
			pageTable[vpn].dirty = true;  // mark page dirty after writing

			VMKernel.vmLock.release();

			vaddr += amount;
			offset += amount;
			length -= amount;
			transferred += amount;
		}

		return transferred;
	}

	/**
	 * Fault in the page for <tt>vpn</tt>, acquiring the global VM lock for the
	 * duration. Used by the exception handler, which holds no lock.
	 */
	private void handlePageFault(int vpn) {
		VMKernel.vmLock.acquire();
		handlePageFaultLocked(vpn);
		VMKernel.vmLock.release();
	}

	/**
	 * Fault in the page for <tt>vpn</tt>. The caller must already hold
	 * {@link VMKernel#vmLock}; readVirtualMemory/writeVirtualMemory call this
	 * directly so the lock can also cover the subsequent copy, keeping the page
	 * from being evicted between fault-in and use.
	 */
	private void handlePageFaultLocked(int vpn) {
		// Obtain a physical frame: take a free one if available, otherwise evict.
		int ppn = UserKernel.allocatePage();
		if (ppn == -1)
			ppn = VMKernel.evictPage();

		VMKernel.registerPage(ppn, this, pageTable[vpn]);
		VMKernel.pinPage(ppn);

		byte[] memory = Machine.processor().getMemory();
		int paddr = ppn * pageSize;

		Integer slot = swapTable.get(vpn);
		if (slot != null) {
			// Previously evicted: restore from swap outside the lock.
			VMKernel.vmLock.release();
			VMKernel.swapIn(slot, ppn);
			VMKernel.vmLock.acquire();
		} else {
			// Identify the COFF section under the lock (metadata only, no I/O).
			CoffSection coffSection = null;
			int coffPageIndex = -1;
			for (int s = 0; s < coff.getNumSections(); s++) {
				CoffSection section = coff.getSection(s);
				int firstVPN = section.getFirstVPN();
				if (vpn >= firstVPN && vpn < firstVPN + section.getLength()) {
					coffSection = section;
					coffPageIndex = vpn - firstVPN;
					pageTable[vpn].readOnly = section.isReadOnly();
					break;
				}
			}
			if (coffSection != null) {
				// Load from COFF file outside the lock.
				VMKernel.vmLock.release();
				coffSection.loadPage(coffPageIndex, ppn);
				VMKernel.vmLock.acquire();
			} else {
				// Zero-fill: pure memory op, stays under the lock.
				Arrays.fill(memory, paddr, paddr + pageSize, (byte) 0);
			}
		}

		pageTable[vpn].ppn = ppn;
		pageTable[vpn].valid = true;
		pageTable[vpn].used = true;
		pageTable[vpn].dirty = false;

		VMKernel.unpinPage(ppn);
    }

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {

		case Processor.exceptionPageFault:
			int vaddr = processor.readRegister(Processor.regBadVAddr);
			int vpn = Processor.pageFromAddress(vaddr);
			handlePageFault(vpn);
			break;

		default:
			super.handleException(cause);
			break;
		}
	}

	/**
	 * Record that this process's virtual page <tt>vpn</tt> has been evicted to
	 * swap slot <tt>slot</tt>. Called by {@link VMKernel#evictPage} when this
	 * process owns the victim frame. Caller holds the global VM lock.
	 */
	public void recordSwap(int vpn, int slot) {
		swapTable.put(vpn, slot);
	}

	/**
	 * Return the swap slot for virtual page <tt>vpn</tt>, or -1 if page
	 * has never been evicted. Caller holds the global VM lock.
	 */
	public int getSwapSlot(int vpn) {
		Integer slot = swapTable.get(vpn);
		return (slot != null) ? slot : -1;
	}

	/** Maps each evicted virtual page to its slot in the global swap file. */
	private HashMap<Integer, Integer> swapTable = new HashMap<Integer, Integer>();

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
