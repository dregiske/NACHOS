# PROJECT 1 OVERVIEW

## Members:

Audrey Elizabeth Fernandez - A17913980

Preity Singh -

Andre Giske - A17918324

### Group Contributions:

We all contributed on the Alarm class together, as well as the test suite. We implemented the Alarm
functionality using a priority queue called waitQueue. This queue holds a list of elements of the WaitingThread class.
This class holds a thread, and its wait time value. The test suite tests:

- The alarm wakes a thread at the correct time
- Return early on times less than or equal to 0
- Threads are put into the queue in ascending order

### Audrey Elizabeth Fernandez:

### Preity Singh:

### Andre Giske:

My individual contributions include the Future class implementation, join() / finish() functions inside the KThread class,
and the test suites for those classes. The join() function essentially sleeps itself until the thread it calls join on has finished its task.
The future class essentially initializes a new thread to complete some process concurrently with the current, and stores the value (accessable by get() function).
The test suite tests:

KThread Class

- The caller of join is slept upon join() call
- The caller of join doesn't block the thread it's calling join if, if it's already finished its task
- Threads cannot call join() on themselves
- Threads cannot call join() on the same thread more than once
- Threads may call join() on different threads
- Joining threads do not interfere with other joins

Future Class

- get() function correctly returns value from fork
- get() should block before it has completed its task
- get() should be idempotent, so calling multiple times shouldn't return different values
