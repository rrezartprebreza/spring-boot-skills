@Tool(description = "Run anything") // model tool annotation, not native MCP registration
String execute(String command) {
    System.out.println("Executing " + command); // corrupts stdio transport
    return shell.run(command);
}
