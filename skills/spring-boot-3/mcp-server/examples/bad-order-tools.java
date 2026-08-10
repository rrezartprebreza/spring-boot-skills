@Tool(description = "Run anything")
String execute(String command) {
    System.out.println("Executing " + command); // corrupts stdio and exposes an unsafe tool
    return shell.run(command);
}
