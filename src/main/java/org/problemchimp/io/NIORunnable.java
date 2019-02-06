package org.problemchimp.io;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.problemchimp.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link Runnable} for IO using java.nio.
 */
public final class NIORunnable implements Runnable, IORunnable {

    private static final Logger logger = LoggerFactory.getLogger(NIORunnable.class);

    private static final String HOSTNAME = "localhost";
    private static final int PORT_RANGE = 10;

    private Set<SocketChannel> connected = ConcurrentHashMap.newKeySet();
    private Queue<byte[]> incoming; // messages coming in from other channels
    private Queue<byte[]> outgoing; // messages waiting to be sent to other channels
    private Selector selector;
    private ServerSocketChannel thisChannel = null;

    /**
     * Accept an incoming connection and add it to the list of connected sockets.
     * 
     * @param key {@link SelectionKey} that is ready to accept.
     * @return true if the connection was accepted.
     */
    private boolean accept(SelectionKey key) {
	ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
	try {
	    logger.trace("Trying to accept as: " + serverChannel.getLocalAddress());
	    SocketChannel channel = serverChannel.accept();
	    if (channel == null) {
		logger.trace("Nothing to accept as " + serverChannel.getLocalAddress());
		return false;
	    }
	    logger.debug("Accepted connection from: " + channel.socket().getRemoteSocketAddress());

	    // Configure the connection as non-blocking, and register it as
	    // readable and writable
	    channel.configureBlocking(false);
	    channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

	    // Add it to the list
	    connected.add(channel);
	    return true;

	} catch (IOException e) {
	    logger.warn(e.toString());
	    return false;
	}
    }

    /**
     * Start listening for incoming connections.
     * 
     * @param basePort - the port at which to try listening. If this port is
     *                 unavailable, try up to PORT_RANGE more before giving up.
     * @return the listening port
     * @throws IOException if no ports could be found.
     */
    private int bind(int basePort) throws IOException {
	// Start listening
	selector = Selector.open();
	thisChannel = ServerSocketChannel.open();
	thisChannel.configureBlocking(false);

	// try up to PORT_RANGE ports
	for (int offset = 0; offset < PORT_RANGE; offset++) {
	    InetSocketAddress address = new InetSocketAddress(HOSTNAME, basePort + offset);
	    logger.debug("Trying to bind to " + address);

	    try {
		thisChannel.socket().bind(address);
		thisChannel.register(selector, SelectionKey.OP_ACCEPT);
		logger.info("Listening on " + address);
		return address.getPort();
	    } catch (IOException e) {
		logger.debug("Address " + address + " in use");
	    }
	}
	throw new IOException("Could not bind to any address in range " + basePort + " - " + (basePort + PORT_RANGE));
    }

    /**
     * Initiate a client connection.
     * 
     * @param port
     * @return true if connected
     */
    private boolean connect(int port) {
	InetSocketAddress address = new InetSocketAddress(HOSTNAME, port);
	logger.debug("Trying to connect to " + address);
	try {
	    SocketChannel channel = SocketChannel.open(address);
	    channel.configureBlocking(false);
	    channel.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
	    connected.add(channel);
	    logger.info("Connected to " + address);
	    return true;
	} catch (ConnectException e) {
	    logger.debug("Unable to connect to " + address + ": " + e);
	} catch (IOException e) {
	    logger.warn("Failed to connect to " + address + ": " + e);
	}
	return false;
    }

    /**
     * Attempt client connections on all ports from {@code basePort} to
     * {@code (basePort + PORT_RANGE)}, skipping {@code skipPort}.
     * 
     * @param basePort the port at which to start
     * @param skipPort the port to skip
     */
    private void connectAll(int basePort, int skipPort) {
	for (int currentPort = basePort; currentPort < basePort + PORT_RANGE; currentPort++) {
	    if (currentPort != skipPort) {
		connect(currentPort);
	    }
	}
    }

    /**
     * Close a single client connection.
     * 
     * @param channel the connection to close
     */
    private void close(SocketChannel channel) {
	if (channel == null) {
	    return;
	}
	connected.remove(channel);
	logger.debug("Connection closed: " + channel.socket().getRemoteSocketAddress());

	try {
	    channel.close();
	} catch (IOException e) {
	    logger.info("Error closing " + channel.socket().getRemoteSocketAddress() + " " + e.toString());
	}
    }

    /**
     * Close all known client connections, the server connection, and the selector.
     */
    private void closeAll() {
	connected.forEach(c -> close(c));
	if (thisChannel != null) {
	    logger.info("Shutting down " + thisChannel.socket().getLocalSocketAddress());
	    try {
		thisChannel.close();
	    } catch (IOException e) {
		logger.info("Error closing " + thisChannel.socket().getLocalSocketAddress() + ":" + e);
	    }
	}
	try {
	    if (selector != null) {
		selector.close();
	    }
	} catch (IOException e) {
	    logger.info("Error closing selector :" + e);
	}
    }

    /**
     * Start listening for incoming connections, and attempt to connect to other
     * running instances.
     * 
     * @param basePort Try to listen on ports between {@code basePort} and
     *                 {@code (basePort + PORT_RANGE)}
     * @param incoming {@link Queue} to handle incoming messages
     * @param outgoing {@link Queue} to poll for messages to be sent to other
     *                 instances
     */
    public void init(int basePort, Queue<byte[]> incoming, Queue<byte[]> outgoing) throws IOException {
	this.incoming = incoming;
	this.outgoing = outgoing;

	// If more than one instance is running on the same host, the listening socket
	// may bind to a different port, so re-fetch it
	int port = bind(basePort);

	// Connect to other services with ports other than our own
	connectAll(basePort, port);
    }

    /**
     * Read from a single channel.
     * 
     * @param key {@link SelectionKey} that is ready to read.
     * @return true if anything was read
     */
    private boolean read(SelectionKey key) {
	SocketChannel channel = (SocketChannel) key.channel();
	ByteBuffer buffer = ByteBuffer.allocate(1024);
	int numRead = -1;
	try {
	    logger.trace("Reading from " + channel.getRemoteAddress());
	    numRead = channel.read(buffer);
	    if (numRead == -1) {
		return false;
	    }

	    byte[] data = new byte[numRead];
	    System.arraycopy(buffer.array(), 0, data, 0, numRead);
	    logger.trace("Received " + numRead + " bytes");
	    incoming.add(data);

	} catch (IOException e) {
	    logger.warn(e.toString());
	    return false;
	}
	return true;
    }

    /**
     * Write to a single channel.
     * 
     * @param key     {@link SelectionKey} that is ready to write.
     * @param message the message to write
     * @return true if anything was written
     */
    private boolean write(SelectionKey key, final byte[] message) {
	SocketChannel channel = (SocketChannel) key.channel();
	ByteBuffer bufferedMessage = ByteBuffer.wrap(message);
	try {
	    logger.trace("Writing to " + channel.getRemoteAddress());
	    int written = channel.write(bufferedMessage);
	    return written > 0;
	} catch (IOException e) {
	    connected.remove(channel);
	}
	return false;
    }

    private void processSelections() throws IOException {
	if (this.selector.selectNow() <= 0) {
	    logger.trace("No channels are ready");
	    return;
	}

	List<byte[]> toSend = null;
	Iterator<SelectionKey> keyIterator = selector.selectedKeys().iterator();
	while (keyIterator.hasNext()) {

	    SelectionKey key = keyIterator.next();
	    logger.trace("Processing key " + key);

	    if (!key.isValid()) {
		logger.debug("Invalid key: " + key);
		continue;
	    }

	    if (key.isAcceptable()) {
		this.accept(key);
	    } else if (key.isReadable()) {
		this.read(key);
	    } else if (key.isWritable()) {
		// fetch the messages to send if this is the first writable key
		if (toSend == null) {
		    toSend = new ArrayList<>();
		    byte[] message;
		    while ((message = outgoing.poll()) != null) {
			toSend.add(message);
		    }
		}
		toSend.forEach(m -> write(key, m));
	    }

	    keyIterator.remove();
	}
    }

    @Override
    public void run() {
	Runtime.getRuntime().addShutdownHook(new Thread() {
	    public void run() {
		closeAll();
	    }
	});

	try {
	    while (true) {
		processSelections();
		Thread.sleep(App.SPIN_SPEED);
	    }
	} catch (InterruptedException e) {
	    logger.debug("Interrupted: all done");
	} catch (IOException e) {
	    logger.warn("IOException reading selector: all done", e);
	} finally {
	    closeAll();
	}
    }
}
