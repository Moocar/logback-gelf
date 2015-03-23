package me.moocar.logbackgelf;

import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.net.DefaultSocketConnector;
import ch.qos.logback.core.net.SocketConnector;
import ch.qos.logback.core.util.CloseUtil;
import ch.qos.logback.core.util.Duration;

import javax.net.SocketFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.*;

/**
 * Created by anthony on 3/8/15.
 */
public class SocketEncoderAppender<E> extends OutputStreamAppender<E> implements SocketConnector.ExceptionHandler{

    /**
     * Default timeout when waiting for the remote server to accept our
     * connection.
     */
    private static final int DEFAULT_ACCEPT_CONNECTION_DELAY = 1000;

    /**
     * Default timeout for how long to wait when inserting an event into
     * the BlockingQueue.
     */
    private static final int DEFAULT_EVENT_DELAY_TIMEOUT = 100;

    /**
     * Default size of the deque used to hold logging events that are destined
     * for the remote peer.
     */
    public static final int DEFAULT_QUEUE_SIZE = 128;

    private int port;
    private String remoteHost;
    private InetAddress address;

    private int acceptConnectionTimeout = DEFAULT_ACCEPT_CONNECTION_DELAY;

    private String peerId;
    private SocketConnector connector;
    private Future<?> task;

    private int queueSize = DEFAULT_QUEUE_SIZE;
    private BlockingDeque<E> deque;
    private Duration eventDelayLimit = new Duration(DEFAULT_EVENT_DELAY_TIMEOUT);

    private volatile Socket socket;

    @Override
    public void start() {
        if (isStarted()) return;
        int errorCount = 0;
        if (port <= 0) {
            errorCount++;
            addError("No port was configured for appender"
                    + name
                    + " For more information, please visit http://logback.qos.ch/codes.html#socket_no_port");

        }

        if (remoteHost == null) {
            errorCount++;
            addError("No remote host was configured for appender"
                    + name
                    + " For more information, please visit http://logback.qos.ch/codes.html#socket_no_host");
        }

        if (errorCount == 0) {
            try {
                address = InetAddress.getByName(remoteHost);
            } catch (UnknownHostException ex) {
                addError("unknown host: " + remoteHost);
                errorCount++;
            }
        }

        final FutureTask<Boolean> connectionFuture = new FutureTask<Boolean>(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                return true;
            }
        });

        if (errorCount == 0) {
            deque = new LinkedBlockingDeque<E>(queueSize);
            peerId = "remote peer " + remoteHost + ":" + port + ": ";
            connector = createConnector(address, port, 0, 1000);
            task = getContext().getExecutorService().submit(new Runnable() {
                @Override
                public void run() {
                    connectSocketAndDispatchEvents(connectionFuture);
                }
            });
        }

        try {
            connectionFuture.get();
            super.start();
        } catch (InterruptedException e) {

        } catch (ExecutionException e) {

        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        addInfo("Stopping");
        if (!isStarted()) return;

//        try {
//            Thread.sleep(100);
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
        CloseUtil.closeQuietly(socket);
        task.cancel(true);
        super.stop();
    }

    private SocketConnector createConnector(InetAddress address, int port, int initialDelay, long retryDelay) {
        SocketConnector connector = newConnector(address, port, initialDelay, retryDelay);
        connector.setExceptionHandler(this);
        connector.setSocketFactory(getSocketFactory());
        return connector;
    }

    protected SocketConnector newConnector(InetAddress address, int port, long initialDelay, long retryDelay) {
        return new DefaultSocketConnector(address, port, initialDelay, retryDelay);
    }

    /**
     * Gets the default {@link SocketFactory} for the platform.
     * <p>
     * Subclasses may override to provide a custom socket factory.
     */
    protected SocketFactory getSocketFactory() {
        return SocketFactory.getDefault();
    }

    private void connectSocketAndDispatchEvents(FutureTask<Boolean> connectionFuture) {
        try {
            while (socketConnectionCouldBeEstablished()) {
                try {
                    socket.setSoTimeout(acceptConnectionTimeout);
                    setOutputStream(socket.getOutputStream());
                    connectionFuture.run();
                    addInfo(peerId + "connection established");
                    dispatchEvents();
                } catch (IOException ex) {
                    addInfo(peerId + "connection failed: " + ex);
                } finally {
                    CloseUtil.closeQuietly(socket);
                    socket = null;
                    super.closeOutputStream();
                    addInfo(peerId + "connection closed");
                }
            }
        } catch (InterruptedException ex) {
            assert true;    // ok... we'll exit now
        }
        addInfo("shutting down");
    }

    private void dispatchEvents() throws InterruptedException, IOException {
        while (true) {
            addInfo("waiting to dispatch");
            E event = deque.takeFirst();
            addInfo("got event" + event);
            super.subAppend(event);
            addInfo("adding null character");
            this.getOutputStream().write('\0');
        }
    }

    @Override
    protected void subAppend(E event) {
        if (event == null || !isStarted()) return;

        try {
            final boolean inserted = deque.offer(event, eventDelayLimit.getMilliseconds(), TimeUnit.MILLISECONDS);
            if (!inserted) {
                addInfo("Dropping event due to timeout limit of [" + eventDelayLimit + "] being exceeded");
            }
        } catch (InterruptedException e) {
            addError("Interrupted while appending event to SocketAppender", e);
        }
    }

    private boolean socketConnectionCouldBeEstablished() throws InterruptedException {
        return (socket = connector.call()) != null;
    }

    @Override
    public void connectionFailed(SocketConnector socketConnector, Exception ex) {
        if (ex instanceof InterruptedException) {
            addInfo("connector interrupted");
        } else if (ex instanceof ConnectException) {
            addInfo(peerId + "connection refused");
        } else {
            addInfo(peerId + ex);
        }
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public int getAcceptConnectionTimeout() {
        return acceptConnectionTimeout;
    }

    public void setAcceptConnectionTimeout(int acceptConnectionTimeout) {
        this.acceptConnectionTimeout = acceptConnectionTimeout;
    }
}
