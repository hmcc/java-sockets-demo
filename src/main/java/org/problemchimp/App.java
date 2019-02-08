package org.problemchimp;

import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.problemchimp.consumer.EchoConsumer;
import org.problemchimp.io.IORunnable;
import org.problemchimp.io.NIORunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connect to other running instances and send messages.
 */
public class App {

    public static int DEFAULT_PORT = 5000;
    public static String IO_PROPERTY = "org.problemchimp.io";
    public static int SPIN_SPEED = 10;

    private static Logger logger = LoggerFactory.getLogger(App.class);

    private Queue<byte[]> incoming = new ConcurrentLinkedQueue<byte[]>();
    private Queue<byte[]> outgoing = new ConcurrentLinkedQueue<byte[]>();
    private Set<Thread> threads = new HashSet<>();

    public App(int port, IORunnable io) {
	try {
	    io.init(port, incoming, outgoing);
	    EchoConsumer echo = new EchoConsumer(incoming);
	    StdinReader stdin = new StdinReader(outgoing);

	    threads.add(new Thread(io));
	    threads.add(new Thread(echo));
	    threads.add(new Thread(stdin));
	    threads.forEach(t -> t.start());

	} catch (Exception e) {
	    e.printStackTrace();
	}
    }

    protected static int getPort(String[] args) {
	int port = DEFAULT_PORT;
	if (args.length >= 1) {
	    try {
		port = Integer.parseInt(args[0]);
	    } catch (NumberFormatException e) {
		logger.warn("Invalid port " + args[0] + ": ignoring");
	    }
	}
	return port;
    }

    protected static IORunnable getIO() {
	IORunnable io = null;
	try {
	    String ioClassName = System.getProperty(IO_PROPERTY);
	    if (ioClassName != null) {
		Class<?> cls = Class.forName(ioClassName);
		if (cls.isAssignableFrom(IORunnable.class)) {
		    io = (IORunnable) cls.getDeclaredConstructor().newInstance();
		}
	    }

	} catch (Exception e) {
	    logger.warn(e.toString());
	}
	if (io == null) {
	    io = new NIORunnable();
	}
	return io;
    }

    public void shutdown() {
	threads.forEach(t -> t.interrupt());
    }

    public static void main(String[] args) {
	int port = getPort(args);
	IORunnable io = getIO();
	new App(port, io);
    }
}
