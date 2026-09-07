// BAD: misspelled keys go unnoticed, units are unclear, and the token has a default.
class ClientSettings {
    @Value("${app.client.timout:2000}")
    String timeout;

    @Value("${app.client.token:development-secret}")
    String token;
}
