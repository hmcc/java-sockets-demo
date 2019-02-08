package org.problemchimp;

import org.problemchimp.io.NIORunnable;

import junit.framework.TestCase;

/**
 * Unit tests for {@link App}.
 */
public class AppTest extends TestCase {

    public void testGetIO_default() {
	assert App.getIO() instanceof NIORunnable;
    }

    public void testGetIO_invalid() {
	System.setProperty(App.IO_PROPERTY, "not valid");
	assert App.getIO() instanceof NIORunnable;
	System.clearProperty(App.IO_PROPERTY);
    }

    public void testGetPort_default() {
	assert App.getPort(new String[] {}) == App.DEFAULT_PORT;
    }

    public void testGetPort_valid() {
	int port = 1234;
	assert App.getPort(new String[] { Integer.toString(port) }) == port;
    }

    public void testGetPort_invalid() {
	assert App.getPort(new String[] { "not valid" }) == App.DEFAULT_PORT;
    }
}
