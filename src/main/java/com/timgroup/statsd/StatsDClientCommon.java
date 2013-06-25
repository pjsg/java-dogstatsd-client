package com.timgroup.statsd;

import java.text.NumberFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public abstract class StatsDClientCommon implements StatsDClient {

    protected static final StatsDClientErrorHandler NO_OP_HANDLER = new StatsDClientErrorHandler() {
        @Override public void handle(Exception e) { /* No-op */ }
    };

    private static final NumberFormat FORMAT;
    static {
        FORMAT = java.text.NumberFormat.getInstance();
        FORMAT.setGroupingUsed(false);
        FORMAT.setMaximumFractionDigits(6);
    }

    private final String prefix;
    protected final StatsDClientErrorHandler handler;
    private final String constantTagsRendered;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
        final ThreadFactory delegate = Executors.defaultThreadFactory();
        @Override public Thread newThread(Runnable r) {
            Thread result = delegate.newThread(r);
            result.setName("StatsD-" + result.getName());
            result.setDaemon(true);
            return result;
        }
    });

    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are passed to the specified
     * handler and then consumed, guaranteeing that failures in metrics will
     * not affect normal code execution.
     *
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param constantTags
     *     tags to be added to all content sent
     * @param errorHandler
     *     handler to use when an exception occurs during usage
     */
    public StatsDClientCommon(String prefix, String[] constantTags, StatsDClientErrorHandler errorHandler) {
        if(prefix != null && prefix.length() > 0) {
            this.prefix = String.format("%s.", prefix);
        } else {
            this.prefix = "";
        }
        this.handler = errorHandler;

        /* Empty list should be null for faster comparison */
        if(constantTags != null && constantTags.length == 0) {
            constantTags = null;
        }

        if(constantTags != null) {
            this.constantTagsRendered = tagString(constantTags, null);
        } else {
            this.constantTagsRendered = null;
        }
    }

    /**
     * Cleanly shut down this StatsD client. This method may throw an exception if
     * the socket cannot be closed.
     */
    @Override
    public void stop() {
        try {
            executor.shutdown();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
        catch (Exception e) {
            handler.handle(e);
        }
    }

    /**
     * Generate a suffix conveying the given tag list to the client
     */
    static String tagString(final String[] tags, final String tagPrefix) {
        StringBuilder sb;
        if(tagPrefix != null) {
            if(tags == null || tags.length == 0) {
                return tagPrefix;
            }
            sb = new StringBuilder(tagPrefix);
            sb.append(",");
        } else {
            if(tags == null || tags.length == 0) {
                return "";
            }
            sb = new StringBuilder("|#");
        }

        for(int n=tags.length - 1; n>=0; n--) {
            sb.append(tags[n]);
            if(n > 0) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    /**
     * Generate a suffix conveying the given tag list to the client
     */
    String tagString(final String[] tags) {
        return tagString(tags, constantTagsRendered);
    }

    /**
     * Adjusts the specified counter by a given delta.
     *
     * <p>This method is non-blocking and is guaranteed not to throw an exception.</p>
     *
     * @param aspect
     *     the name of the counter to adjust
     * @param delta
     *     the amount to adjust the counter by
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void count(String aspect, int delta, String... tags) {
        send(String.format("%s%s:%d|c%s", prefix, aspect, delta, tagString(tags)));
    }

    /**
     * Increments the specified counter by one.
     *
     * <p>This method is non-blocking and is guaranteed not to throw an exception.</p>
     *
     * @param aspect
     *     the name of the counter to increment
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void incrementCounter(String aspect, String... tags) {
        count(aspect, 1, tags);
    }

    /**
     * Convenience method equivalent to {@link #incrementCounter(String, String[])}.
     */
    @Override
    public void increment(String aspect, String... tags) {
        incrementCounter(aspect, tags);
    }

    /**
     * Decrements the specified counter by one.
     *
     * <p>This method is non-blocking and is guaranteed not to throw an exception.</p>
     *
     * @param aspect
     *     the name of the counter to decrement
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void decrementCounter(String aspect, String... tags) {
        count(aspect, -1, tags);
    }

    /**
     * Convenience method equivalent to {@link #decrementCounter(String, String[])}.
     */
    @Override
    public void decrement(String aspect, String... tags) {
        decrementCounter(aspect, tags);
    }

    /**
     * Records the latest fixed value for the specified named gauge.
     *
     * <p>This method is non-blocking and is guaranteed not to throw an exception.</p>
     *
     * @param aspect
     *     the name of the gauge
     * @param value
     *     the new reading of the gauge
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void recordGaugeValue(String aspect, double value, String... tags) {
        /* Intentionally using %s rather than %f here to avoid
         * padding with extra 0s to represent precision */
        send(String.format("%s%s:%s|g%s", prefix, aspect, FORMAT.format(value), tagString(tags)));
    }

    /**
     * Convenience method equivalent to {@link #recordGaugeValue(String, double, String[])}.
     */
    @Override
    public void gauge(String aspect, double value, String... tags) {
        recordGaugeValue(aspect, value, tags);
    }


    /**
     * Records the latest fixed value for the specified named gauge.
     *
     * <p>This method is non-blocking and is guaranteed not to throw an exception.</p>
     *
     * @param aspect
     *     the name of the gauge
     * @param value
     *     the new reading of the gauge
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void recordGaugeValue(String aspect, int value, String... tags) {
        send(String.format("%s%s:%d|g%s", prefix, aspect, value, tagString(tags)));
    }

    /**
     * Convenience method equivalent to {@link #recordGaugeValue(String, int, String[])}.
     */
    @Override
    public void gauge(String aspect, int value, String... tags) {
        recordGaugeValue(aspect, value, tags);
    }

    /**
     * Records an execution time in milliseconds for the specified named operation.
     *
     * <p>This method is non-blocking and is guaranteed not to throw an exception.</p>
     *
     * @param aspect
     *     the name of the timed operation
     * @param timeInMs
     *     the time in milliseconds
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void recordExecutionTime(String aspect, long timeInMs, String... tags) {
        recordHistogramValue(aspect, (timeInMs * 0.001), tags);
    }

    /**
     * Convenience method equivalent to {@link #recordExecutionTime(String, long, String[])}.
     */
    @Override
    public void time(String aspect, long value, String... tags) {
        recordExecutionTime(aspect, value, tags);
    }

    /**
     * Records a value for the specified named histogram.
     *
     * <p>This method is non-blocking and is guaranteed not to throw an exception.</p>
     *
     * @param aspect
     *     the name of the histogram
     * @param value
     *     the value to be incorporated in the histogram
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void recordHistogramValue(String aspect, double value, String... tags) {
        /* Intentionally using %s rather than %f here to avoid
         * padding with extra 0s to represent precision */
        send(String.format("%s%s:%s|h%s", prefix, aspect, FORMAT.format(value), tagString(tags)));
    }

    /**
     * Convenience method equivalent to {@link #recordHistogramValue(String, double, String[])}.
     */
    @Override
    public void histogram(String aspect, double value, String... tags) {
        recordHistogramValue(aspect, value, tags);
    }

    /**
     * Records a value for the specified named histogram.
     *
     * <p>This method is non-blocking and is guaranteed not to throw an exception.</p>
     *
     * @param aspect
     *     the name of the histogram
     * @param value
     *     the value to be incorporated in the histogram
     * @param tags
     *     array of tags to be added to the data
     */
    @Override
    public void recordHistogramValue(String aspect, int value, String... tags) {
        send(String.format("%s%s:%d|h%s", prefix, aspect, value, tagString(tags)));
    }

    /**
     * Convenience method equivalent to {@link #recordHistogramValue(String, int, String[])}.
     */
    @Override
    public void histogram(String aspect, int value, String... tags) {
        recordHistogramValue(aspect, value, tags);
    }

    private void send(final String message) {
        try {
            executor.execute(new Runnable() {
                @Override public void run() {
                    blockingSend(message);
                }
            });
        }
        catch (Exception e) {
            handler.handle(e);
        }
    }

    protected abstract void blockingSend(String message);
}
