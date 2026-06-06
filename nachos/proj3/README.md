# PROJECT 3 OVERVIEW

## Members:

Audrey Elizabeth Fernandez - A17913980

Preity Singh - A17981892

Andre Giske - A17918324

### Audrey Elizabeth Fernandez

I contributed to the checkpoint and part 2

### Preity Singh
Checkpoint: Modified loadSections to mark all page table entries invalid upfront with no physical memory allocated. On first access, the CPU faults into handlePageFaultLocked, which grabs a free frame, loads the right COFF section or zero-fills for stack/args, marks the entry valid, and lets the instruction retry.
Part 3: Eviction now skips the swap write entirely for clean pages since they can just be reloaded from COFF or zero-filled again. I also kept swap slots alive after swap-in so a clean re-eviction doesn't need a new slot, added a getSwapSlot helper to reuse existing slots for dirty pages, and manually set dirty = true in writeVirtualMemory since kernel copies bypass the CPU's dirty tracking.
Part 4: Added pinnedCount and unpinCondition to the kernel and pinPage/unpinPage helpers that flag IPTEntry.pinned and wake waiting threads. Victim frames get pinned before dropping vmLock for the swap write so the clock hand skips them. In handlePageFaultLocked, newly allocated frames get pinned before releasing the lock, I/O happens outside the lock, then I reacquire, update the translation, and unpin, so multiple processes can fault in parallel during disk I/O.

### Andre Giske

I implemented much of part 2 of the project 3 assignment. This part consisted of demand paging with page replacement. Essentially, a page starts with the `loadSections` as an invalid entry in the page table. Pages are accessed using the faults, specifically the `handlePageFaultLocked` where pages are accessed. This function fills a frame from the correct source and updates its entry in the page table. When the RAM is full, the `evictPage` function is called, utilizing the clock mechanism to pick a page to evict. This function iterates through the `IPT` with the `clockHand` until it finds a page in the `IPT` to kick out, and gives that pages `ppn` as the result so the handler can do the rest. Then `unloadSections` was fixed to properly free the data upon exiting a process. It handles freeing the data both in the RAM and disk, ensuring the process is completely freed and avoids leaks.
