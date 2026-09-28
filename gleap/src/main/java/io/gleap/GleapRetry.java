package io.gleap;

/**
 * The retry policy of the config and session requests: up to 3 attempts, 1 s and then 2 s apart.
 */
final class GleapRetry {
    interface Attempt {
        void run() throws Exception;
    }

    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    static final int MAX_ATTEMPTS = 3;
    static final long INITIAL_DELAY_MS = 1000;

    private static final Sleeper THREAD_SLEEP = new Sleeper() {
        @Override
        public void sleep(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }
    };

    private static volatile Sleeper sleeper = THREAD_SLEEP;

    private GleapRetry() {
    }

    /**
     * Runs the attempt until it succeeds, at most {@link #MAX_ATTEMPTS} times. Only exceptions of
     * {@code retryOn} are retried; any other exception is thrown right away.
     *
     * @param name what is attempted, for the log ("Session request")
     * @return whether an attempt succeeded
     */
    static boolean withBackoff(String name, Class<? extends Exception> retryOn, Attempt attempt) {
        Exception lastException = null;
        for (int number = 1; number <= MAX_ATTEMPTS; number++) {
            try {
                attempt.run();
                return true;
            } catch (Exception e) {
                if (!retryOn.isInstance(e)) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException(e);
                }
                lastException = e;
                GleapLog.w(name + " attempt " + number + " failed", e);

                if (number < MAX_ATTEMPTS) {
                    try {
                        sleeper.sleep(INITIAL_DELAY_MS * (long) Math.pow(2, number - 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        GleapLog.e("All " + Character.toLowerCase(name.charAt(0)) + name.substring(1)
                + " attempts failed after " + MAX_ATTEMPTS + " retries", lastException);
        return false;
    }

    // Tests only; null restores Thread.sleep.
    static void setSleeperForTesting(Sleeper testSleeper) {
        sleeper = testSleeper != null ? testSleeper : THREAD_SLEEP;
    }
}
