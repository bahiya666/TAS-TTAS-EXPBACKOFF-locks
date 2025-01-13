import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class TTASLock {
    AtomicBoolean state = new AtomicBoolean(false);

    void lock() {
        while (true) {
            // Spin-wait while the lock is held
            while (state.get()) {}
            // Try to acquire the lock
            if (!state.getAndSet(true)) {
                return; // Successfully acquired the lock
            }
        }
    }

    void unlock() {
        state.set(false); // Release the lock
    }
}

public class TTASLockTest {
    private static final int NUM_ITERATIONS = 100000; // Total iterations for each thread

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
        TTASLock lock = new TTASLock();
        AtomicInteger counter = new AtomicInteger(0); // Shared counter
        long startTime = System.currentTimeMillis();

        Runnable task = () -> {
            for (int i = 0; i < NUM_ITERATIONS; i++) {
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
    private static TTASLock ttasLock = new TTASLock();

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
                    ttasLock.lock(); // Acquire the TTAS lock
                    try {
                        Integer item = queue.poll(); // Direct poll
                        if (item == null) break; // Exit if no more items
                        // Do nothing with the dequeued item, just remove it
                    } finally {
                        ttasLock.unlock(); // Release the lock
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
    
        for (int run = 0; run < 10; run++) { // Run the test 10 times for averaging
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            long startTime = System.currentTimeMillis();
    
            Runnable task = () -> {
                while (true) {
                    ttasLock.lock(); // Acquire the TTAS lock
                    try {
                        Integer item = queue.poll(); // Direct poll
                        if (item == null) break; // Exit if no more items
                        // Do nothing with the dequeued item, just remove it
                    } finally {
                        ttasLock.unlock(); // Release the lock
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
        System.out.println("Shared Queue Test - Number of Threads: " + numThreads + " - Average Time taken: " + (totalDuration / 10) + " ms");
    }
    
}
