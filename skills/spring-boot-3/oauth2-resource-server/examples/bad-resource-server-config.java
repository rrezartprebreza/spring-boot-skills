// ❌ BAD — wrong role check, unnecessary UserDetailsService, session-based

@Configuration
@EnableWebSecurity
public class ResourceServerConfig extends WebSecurityConfigurerAdapter {  // deprecated

    @Autowired                                            // field injection
    private UserDetailsService userDetailsService;         // not needed for JWT resource server

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()                             // old API, not lambda DSL
            .authorizeRequests()                          // deprecated
            .antMatchers("/api/**")
            .hasRole("ADMIN")                             // hasRole expects ROLE_ prefix, not SCOPE_
            .and()
            .oauth2ResourceServer()                       // old chaining API
            .jwt();                                       // no custom converter — roles won't map correctly

        // Session is not explicitly STATELESS — defaults to IF_REQUIRED
        // This means the server creates HTTP sessions, defeating JWT's purpose
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(
            "http://auth-server/.well-known/jwks.json")   // hardcoded URL, should use issuer-uri
            .build();
    }

    // UserDetailsService is unnecessary for resource servers —
    // the JWT token contains all claims needed for authentication
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByEmail(username)  // extra DB call per request!
            .map(u -> User.withUsername(u.getEmail()).password(u.getPassword()).roles("USER").build())
            .orElseThrow();
    }
}
