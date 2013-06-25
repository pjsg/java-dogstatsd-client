package com.timgroup.statsd;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

public class NonBlockingStatsDClientTCP extends StatsDClientCommon {
    protected boolean socketConnected = false;
    protected Socket clientTCPSocket;
    protected PrintWriter writer;
    private InetSocketAddress statsdAddress;

    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. The new client will
     * attempt to open a connection to the StatsD server immediately upon
     * instantiation, and may throw an exception if that a connection cannot
     * be established. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are consumed, guaranteeing
     * that failures in metrics will not affect normal code execution.
     *
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param hostname
     *     the host name of the targeted StatsD server
     * @param port
     *     the port of the targeted StatsD server
     * @throws StatsDClientException
     *     if the client could not be started
     */
    public NonBlockingStatsDClientTCP(String prefix, String hostname, int port) throws StatsDClientException {
        this(prefix, hostname, port, null, NO_OP_HANDLER);
    }

    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. The new client will
     * attempt to open a connection to the StatsD server immediately upon
     * instantiation, and may throw an exception if that a connection cannot
     * be established. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are consumed, guaranteeing
     * that failures in metrics will not affect normal code execution.
     *
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param hostname
     *     the host name of the targeted StatsD server
     * @param port
     *     the port of the targeted StatsD server
     * @param constantTags
     *     tags to be added to all content sent
     * @throws StatsDClientException
     *     if the client could not be started
     */
    public NonBlockingStatsDClientTCP(String prefix, String hostname, int port, String[] constantTags) throws StatsDClientException {
        this(prefix, hostname, port, constantTags, NO_OP_HANDLER);
    }

    /**
     * Create a new StatsD client communicating with a StatsD instance on the
     * specified host and port. All messages send via this client will have
     * their keys prefixed with the specified string. The new client will
     * attempt to open a connection to the StatsD server immediately upon
     * instantiation, and may throw an exception if that a connection cannot
     * be established. Once a client has been instantiated in this way, all
     * exceptions thrown during subsequent usage are passed to the specified
     * handler and then consumed, guaranteeing that failures in metrics will
     * not affect normal code execution.
     *
     * @param prefix
     *     the prefix to apply to keys sent via this client
     * @param hostname
     *     the host name of the targeted StatsD server
     * @param port
     *     the port of the targeted StatsD server
     * @param constantTags
     *     tags to be added to all content sent
     * @param errorHandler
     *     handler to use when an exception occurs during usage
     * @throws StatsDClientException
     *     if the client could not be started
     */
    public NonBlockingStatsDClientTCP(String prefix, String hostname, int port, String[] constantTags, StatsDClientErrorHandler errorHandler) throws StatsDClientException {
        super(prefix, constantTags, errorHandler);

        try {
            statsdAddress = new InetSocketAddress(hostname, port);
        } catch (Exception e) {
            throw new StatsDClientException("Failed to initialize StatsD client", e);
        }
    }

    protected void establishConnection() throws IOException {
        if (clientTCPSocket != null) {
            clientTCPSocket.close();
        }
        clientTCPSocket = new Socket();
        clientTCPSocket.connect(statsdAddress);

        writer = new PrintWriter(clientTCPSocket.getOutputStream());

        socketConnected = true;
    }

    protected void blockingSend(String message) {
        try {
            if (!socketConnected) {
                establishConnection();
            }
            writer.append(message);
            writer.append("\n");
            writer.flush();
        } catch (Exception e) {
            socketConnected = false;
            handler.handle(e);
        }
    }

    @Override
    public void stop() {
        try {
            super.stop();
        } finally {
            if (clientTCPSocket != null) {
                try {
                    clientTCPSocket.close();
                    clientTCPSocket = null;
                } catch (IOException e) {
                    handler.handle(e);
                }
            }
        }
    }
}
