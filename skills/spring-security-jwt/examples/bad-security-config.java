// ❌ BAD — deprecated API, session-based, hardcoded secret, missing method security

@Configuration
@EnableWebSecurity                                        // missing @EnableMethodSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {  // deprecated since Spring Security 5.7

    @Autowired                                            // field injection
    private JwtAuthenticationFilter jwtAuthFilter;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()                             // old chaining API, not lambda DSL
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)  // should be STATELESS for JWT
            .and()
            .authorizeRequests()                          // deprecated — use authorizeHttpRequests
            .antMatchers("/api/**").authenticated()        // antMatchers deprecated — use requestMatchers
            .and()
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();               // default strength 10, should use 12
    }

    // secret hardcoded as string literal — should be in environment variable
    private static final String SECRET = "mySecretKey12345678901234567890123456";
}
