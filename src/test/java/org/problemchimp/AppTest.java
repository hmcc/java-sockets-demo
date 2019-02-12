package org.problemchimp;

import org.junit.Test;
import org.problemchimp.io.NIORunnable;

/**
 * Unit tests for {@link App}.
 */
public class AppTest {

    @Test
    public void testGetIO_default() {
	assert App.getIO() instanceof NIORunnable;
    }

    @Test
    public void testGetIO_invalid() {
	System.setProperty(App.IO_PROPERTY, "not valid");
	assert App.getIO() instanceof NIORunnable;
	System.clearProperty(App.IO_PROPERTY);
    }

    @Test
    public void testGetPort_default() {
	assert App.getPort(new String[] {}) == App.DEFAULT_PORT;
    }

    @Test
    public void testGetPort_valid() {
	int port = 1234;
	assert App.getPort(new String[] { Integer.toString(port) }) == port;
    }

    @Test
    public void testGetPort_invalid() {
	assert App.getPort(new String[] { "not valid" }) == App.DEFAULT_PORT;
    }
}
