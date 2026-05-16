# PROJECT 2 OVERVIEW

## Members:

Audrey Elizabeth Fernandez - A17913980

Preity Singh - A17981892

Andre Giske - A17918324

### Audrey Elizabeth Fernandez

I implemented multiprogramming support, enabling multiple user processes to run concurrently in separate virtual address spaces. My implementation allocates physical pages from a global free page pool in `UserKernel`, where each process assigns them in `loadSections()`, and returns them in `unloadSections()`. Each process gets a `TranslationEntry[]` page table mapping its virtual page numbers to physical page numbers, with read only protection for `.text` sections. I also rewrote `readVirtualMemory` and `writeVirtualMemory` to walk the page table one page at a time, correctly handling transfers that span page boundaries.

### Preity Singh

I implemented the file system syscalls: `creat`, `open`, `read`, `write`, `close`, and `unlink`. Each process maintains a table of up to 16 open file descriptors. The syscall handlers validate all arguments like null pointers, out of bounds file descriptors, and exceeding the descriptor limit all return -1 without crashing. File operations delegate to the underlying `FileSystem` through `OpenFile` objects, and `close` properly nulls the descriptor slot so it can be reused.

### Andre Giske

I implemented the process management syscalls (`exec`, `join`, `exit`, `halt`) and the named pipe extra credit. For process management, each `UserProcess` is assigned a unique PID via an atomic counter, and a static `activeProcesses` counter tracks how many processes are live. `handleHalt` only succeeds for the root process. `handleExit` closes all open files, frees pages via `unloadSections`, decrements `activeProcesses`, and either terminates the machine or calls the finish thread function. The `exited` flag is set only in the syscall dispatcher for normal `exit()` calls, not for crashes, so `handleJoin` can correctly return 1 for clean exits and 0 for abnormal ones. `handleExec` reads the filename and argument strings from user memory, spawns a child `UserProcess`, records the parent/child relationship, and starts the child's thread. `handleJoin` searches the caller's children list for the target pid, joins its thread, writes the exit status to user memory if a valid address was provided, and removes the child from the list so a second join correctly returns -1.

For named pipes I added a `Pipe` inner class implementing a circular byte buffer with a `Lock` and 2 condition variables for blocking producer consumer coordination. A `PipeFile` wrapper extends `OpenFile` to delegate reads and writes to the pipe and manage a reference count so the pipe is removed from the global `pipeTable` when all descriptors referencing it are closed. Filenames beginning with `/pipe/` are intercepted in `handleCreate` and `handleOpen`, with all other names falling through to normal filesystem handling.

The test suite (inside `nachos/test/`) tests:

- `test_filesys.coff` — creat, write, read, close, and unlink round trip correctly
- `test_exec.coff` — exec spawns a child successfully, join waits for it and returns 1
- `test_bulletproof.coff` — null pointer arguments to open/creat/unlink, invalid file descriptors, and joins on non child pids all return -1
