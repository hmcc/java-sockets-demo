# java-sockets-demo

Simple demonstration of sockets programming with java.nio.

The application acts as both client and server, which means that there is no
single point of failure if run as part of a distributed system.

### Build and run

1. Start a first instance with `make run`
2. Start a second instance
3. Type something in the first instance's window
4. The second instance should print the received data
