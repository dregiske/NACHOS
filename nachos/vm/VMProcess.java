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

			int ppn = UserKernel.allocatePage();
			if (ppn == -1) {
				UThread.finish();
				break;
			}

			byte[] memory = processor.getMemory();
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
