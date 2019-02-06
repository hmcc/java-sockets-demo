package org.problemchimp.consumer;

import java.util.Queue;

import org.problemchimp.App;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Echo anything read from a queue to stdout.
 */
public final class EchoConsumer implements Runnable {

    private static Logger logger = LoggerFactory.getLogger(EchoConsumer.class);

    private Queue<byte[]> queue;

    public EchoConsumer(Queue<byte[]> incoming) {
	this.queue = incoming;
    }

    private void handleMessage(byte[] message) {
	System.out.println(new String(message));
    }

    /**
     * Read from the queue.
     */
    private void readAll() {
	byte[] message;
	while ((message = queue.poll()) != null) {
	    handleMessage(message);
	}
    }

    @Override
    public void run() {
	try {
	    while (true) {
		readAll();
		Thread.sleep(App.SPIN_SPEED);
	    }
	} catch (InterruptedException e) {
	    logger.debug("Interrupted: all done");
	    return;
	}
    }
}
