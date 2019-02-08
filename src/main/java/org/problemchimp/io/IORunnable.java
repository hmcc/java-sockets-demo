package org.problemchimp.io;

import java.io.IOException;
import java.util.Queue;

/**
 * Base interface for {@link Runnable}s for IO.
 */
public interface IORunnable extends Runnable {

    void init(int suggestedPort, Queue<byte[]> incoming, Queue<byte[]> outgoing) throws IOException;
}
