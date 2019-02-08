package org.problemchimp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.UnknownHostException;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Read from stdin and push the messages read to a queue.
 */
public final class StdinReader implements Runnable {

    private static Logger logger = LoggerFactory.getLogger(StdinReader.class);

    private BufferedReader reader;
    private Queue<byte[]> queue;

    public StdinReader(Queue<byte[]> outgoing) throws UnknownHostException, IOException {
	this.queue = outgoing;
    }

    private void close() {
	try {
	    if (reader != null) {
		reader.close();
	    }
	} catch (IOException e) {
	    logger.info("IOException closing stdin " + e);
	}
    }

    private void read() throws IOException {
	if (reader != null && reader.ready()) {
	    logger.trace("Reader is ready!");
	    while (reader.ready()) {
		String messsage = reader.readLine();
		queue.add(messsage.getBytes());
		logger.trace("Put " + messsage + " on the queue");
	    }
	} else {
	    logger.trace("Reader is not ready");
	}
    }

    @Override
    public void run() {
	Runtime.getRuntime().addShutdownHook(new Thread() {
	    public void run() {
		close();
	    }
	});

	reader = new BufferedReader(new InputStreamReader(System.in));
	try {
	    while (true) {
		read();
		Thread.sleep(App.SPIN_SPEED);
	    }
	} catch (InterruptedException e) {
	    logger.debug("Interrupted: all done");
	    return;
	} catch (IOException e) {
	    logger.warn(e.toString(), e);
	    return;
	} finally {
	    close();
	}
    }
}
