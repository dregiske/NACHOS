# PROJECT 1 OVERVIEW

## Members:

Audrey Elizabeth Fernandez - A17913980

Preity Singh - A17981892

Andre Giske - A17918324

### Group Contributions:

We all contributed on the Alarm class together, as well as the test suite. We implemented the Alarm
functionality using a priority queue called waitQueue. This queue holds a list of elements of the WaitingThread class.
This class holds a thread, and its wait time value. The test suite tests:

- The alarm wakes a thread at the correct time
- Return early on times less than or equal to 0
- Threads are put into the queue in ascending order

### Audrey Elizabeth Fernandez:
I implemented the Rendezvous class and tests, which provides a mechanism for threads to meet and exchange values. My implementation uses a HashMap to map an Integer tag to a LinkedList, which functions as that tag's own independent "queue" of pending Slots. When a thread arrives and finds an empty queue, it creates a Slot with its value, adds itself to the queue and sleeps. When a thread arrives and winds a waiting Slot, wakes the  waiting thread and the threads exchange values. 

The test suite verifies:
- A thread calling exchange blocks until another thread arrives at the same tag to pair with it
- Each thread correctly recieves the other thread's value after the exchange is completed
- Many threads calling exchange on the same tag are correctly paired up (without any thread revieving its own value back)
- Threads exchanging on different tags operate independently, and never pair with each other
- Separate instances of Rendezvous operate independently so threads on different instances never interact on the same tag

### Preity Singh:
I implemented the Condition2 class, which provides condition variable functionality using interrupt disable and restore for atomicity, instead of than semaphores. The implementation uses a LinkedList<KThread> wait queue. In sleep(), interrupts are disabled before adding the current thread to the queue and releasing the lock, making sure that KThread.sleep() is called atomically. wake() removes and readies one waiting thread under disabled interrupts, and wakeAll() repeatedly calls wake() until the queue is empty.

The test cases verify:
- A thread calling sleep() blocks until another thread calls wake()
- wake() wakes at most one thread when multiple threads are waiting
- wakeAll() wakes all waiting threads
- A "lost wake" (calling wake() with no waiters) has no effect, and future threads that call sleep() still block

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
