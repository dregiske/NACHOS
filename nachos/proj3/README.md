# PROJECT 3 OVERVIEW

## Members:

Audrey Elizabeth Fernandez - A17913980

Preity Singh - A17981892

Andre Giske - A17918324

### Audrey Elizabeth Fernandez

### Preity Singh

### Andre Giske

I implemented much of part 2 of the project 3 assignment. This part consisted of demand paging with page replacement. Essentially, a page starts with the `loadSections` as an invalid entry in the page table. Pages are accessed using the faults, specifically the `handlePageFaultLocked` where pages are accessed. This function fills a frame from the correct source and updates its entry in the page table. When the RAM is full, the `evictPage` function is called, utilizing the clock mechanism to pick a page to evict. This function iterates through the `IPT` with the `clockHand` until it finds a page in the `IPT` to kick out, and gives that pages `ppn` as the result so the handler can do the rest. Then `unloadSections` was fixed to properly free the data upon exiting a process. It handles freeing the data both in the RAM and disk, ensuring the process is completely freed and avoids leaks.
