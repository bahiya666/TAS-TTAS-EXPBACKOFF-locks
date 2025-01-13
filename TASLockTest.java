import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
public class TASLockTest {

    // The TAS Lock implementation
    static class TASLock {
        private final AtomicBoolean state = new AtomicBoolean(false);

        public void lock() {
            while (state.getAndSet(true)) {
                // Busy-wait (spinning)
            }
        }

        public void unlock() {
            state.set(false);
        }
    }

    public static void main(String[] args) {
        int[] threadCounts = { 2, 5, 10, 20, 40}; // Different numbers of threads to test

        for (int numThreads : threadCounts) {
            testIncreasedContention(numThreads);
        }

        for (int numThreads : threadCounts) {
            testsharedqueue(numThreads);
        }
    }

   
    private static void testIncreasedContention(int numThreads) {
        TASLock lock = new TASLock();
        AtomicInteger counter = new AtomicInteger(0); // Shared counter
        long startTime = System.currentTimeMillis();

        Runnable task = () -> {
            for (int i = 0; i < 100000; i++) {
                lock.lock();
                try {
                    // Increment the shared counter in the critical section
                    counter.incrementAndGet();
                } finally {
                    lock.unlock();
                }
            }
        };

        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            threads[i] = new Thread(task);
            threads[i].start();
        }

        for (int i = 0; i < numThreads; i++) {
            try {
                threads[i].join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Increased Contention - Number of Threads: " + numThreads + " - Time taken: " + (endTime - startTime) + " ms");
    }

    private static final int QUEUE_SIZE = 100000;  // Larger queue size
    private static ArrayBlockingQueue<Integer> queue = new ArrayBlockingQueue<>(QUEUE_SIZE);
    private static TASLock tasLock = new TASLock();


    //multiple threads concurrently access a shared queue . 
    //it fills the queue with a large number of items
    //heaving multiple threads dequeue those items while acquiring and releasing the lock
    private static void testsharedqueue(int numThreads) {
        // Warm-up phase
        for (int warmUpRun = 0; warmUpRun < 5; warmUpRun++) {
            queue.clear();
            for (int i = 0; i < QUEUE_SIZE; i++) {
                queue.offer(i);
            }
            ExecutorService warmUpExecutor = Executors.newFixedThreadPool(numThreads);
            Runnable warmUpTask = () -> {
                while (true) {
                    tasLock.lock(); // Acquire the TAS lock
                    try {
                        Integer item = queue.poll(); // Direct poll
                        if (item == null) break; // Exit if no more items
                        // Do nothing with the dequeued item, just remove it
                    } finally {
                        tasLock.unlock(); // Release the lock
                    }
                }
            };
            for (int i = 0; i < numThreads; i++) {
                warmUpExecutor.submit(warmUpTask);
            }
            warmUpExecutor.shutdown();
            while (!warmUpExecutor.isTerminated()) {}
        }
    
        // Actual test
        queue.clear();
        for (int i = 0; i < QUEUE_SIZE; i++) {
            queue.offer(i);
        }
    
        long totalDuration = 0; // Variable to accumulate total time for averaging
    
        for (int run = 0; run < 5; run++) { // Run the test 5 times for averaging
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            long startTime = System.currentTimeMillis();
    
            Runnable task = () -> {
                while (true) {
                    tasLock.lock(); // Acquire the TAS lock
                    try {
                        Integer item = queue.poll(); // Direct poll
                        if (item == null) break; // Exit if no more items
                        // Do nothing with the dequeued item, just remove it
                    } finally {
                        tasLock.unlock(); // Release the lock
                    }
                }
            };
    
            for (int i = 0; i < numThreads; i++) {
                executor.submit(task);
            }
    
            executor.shutdown();
            while (!executor.isTerminated()) {}
    
            long endTime = System.currentTimeMillis();
            totalDuration += (endTime - startTime);
        }
    
        // Calculate average time
        System.out.println("Shared Queue Test - Number of Threads: " + numThreads + " - Average Time taken: " + (totalDuration / 5) + " ms");
    }
    
   
}
