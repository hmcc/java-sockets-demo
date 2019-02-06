clean:
	mvn clean

install: clean
	mvn install
	
test: clean
	mvn test

run: install
	java -jar target/java-sockets-demo-0.1-jar-with-dependencies.jar