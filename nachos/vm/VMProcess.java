package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import java.util.Arrays;

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

		int coffPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			coffPages += coff.getSection(s).getLength();
		}
		numPages = coffPages + stackPages + 1;

		// check if theres enough virtual address space for numPages
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical pages");
			return false;
		}

		// create page table and make the entries invalid.
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
		super.unloadSections();
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

			if (!pageTable[vpn].valid)
				handlePageFault(vpn);

			if (!pageTable[vpn].valid)
				break;

			int ppn = pageTable[vpn].ppn;
			int physAddr = ppn * pageSize + pageOffset;

			int amount = Math.min(length, pageSize - pageOffset);
			System.arraycopy(memory, physAddr, data, offset, amount);

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

			if (!pageTable[vpn].valid)
				handlePageFault(vpn);

			if (!pageTable[vpn].valid)
				break;

			if (pageTable[vpn].readOnly)
				break;

			int ppn = pageTable[vpn].ppn;
			int physAddr = ppn * pageSize + pageOffset;

			int amount = Math.min(length, pageSize - pageOffset);
			System.arraycopy(data, offset, memory, physAddr, amount);

			vaddr += amount;
			offset += amount;
			length -= amount;
			transferred += amount;
		}

		return transferred;
	}

	private void handlePageFault(int vpn) {
        // ... the helper code ...
		int ppn = UserKernel.allocatePage();
		if (ppn == -1) {
			return;
		}

		byte[] memory = Machine.processor().getMemory();
		int paddr = ppn * pageSize;
		Arrays.fill(memory, paddr, paddr + pageSize, (byte) 0);

		boolean readOnly = false;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			int firstVPN = section.getFirstVPN();
			if (vpn >= firstVPN && vpn < firstVPN + section.getLength()) {
				section.loadPage(vpn - firstVPN, ppn);
				readOnly = section.isReadOnly();
				break;
			}
		}

		pageTable[vpn].ppn = ppn;
		pageTable[vpn].valid = true;
		pageTable[vpn].readOnly = readOnly;
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

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
