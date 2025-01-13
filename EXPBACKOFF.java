import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EXPBACKOFF {
    
    // The Backoff lock implementation
    static class Backoff {
        private static final int MIN_DELAY = 100; // Minimum delay in milliseconds
        private static final int MAX_DELAY = 10000; // Maximum delay in milliseconds
        private final AtomicBoolean state = new AtomicBoolean(false);
        private final Random random = new Random();

        public void lock() {
            int delay = MIN_DELAY;
            while (true) {
                while (state.get()) {} // Spin-wait while the lock is held
                if (!state.getAndSet(true)) {
                    return; // Successfully acquired the lock
                }
                // Backoff strategy
                try {
                    Thread.sleep(random.nextInt(delay)); // Sleep for a random time
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // Restore the interrupted status
                }
                if (delay < MAX_DELAY) {
                    delay *= 2; // Exponentially increase the delay
                }
            }
        }

        public void unlock() {
            state.set(false); // Release the lock
        }
    }

    // Test scenarios for the Backoff lock
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


    // TESTS high contention with increased pressure on the critical section
    //how lock performs when many threads are trying to access and modify a shared resource concurrently.
    private static void testIncreasedContention(int numThreads) {
        Backoff lock = new Backoff();
                AtomicInteger counter = new AtomicInteger(0); // Shared counter

        long startTime = System.currentTimeMillis();

        Runnable task = () -> {
            for (int i = 0; i < NUM_ITERATIONS; i++) {
                lock.lock();
                try {
                     //more work inside the critical section (the increment operation)
                     // Increment the shared counter in the critical section
                     //which introduces more contention as the number of threads increases.
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
    private static Backoff backoff = new Backoff();

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
                    backoff.lock(); // Acquire the backoff lock
                    try {
                        Integer item = queue.poll(); // Direct poll
                        if (item == null) break; // Exit if no more items
                        // Do nothing with the dequeued item, just remove it
                    } finally {
                        backoff.unlock(); // Release the lock
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
                    backoff.lock(); // Acquire the backoff lock
                    try {
                        Integer item = queue.poll(); // Direct poll
                        if (item == null) break; // Exit if no more items
                        // Do nothing with the dequeued item, just remove it
                    } finally {
                        backoff.unlock(); // Release the lock
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
