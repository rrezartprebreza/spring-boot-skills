// ❌ BAD — pre-Security-7 API (won't compile), session-based, hardcoded secret, missing method security

@Configuration
@EnableWebSecurity                                        // missing @EnableMethodSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {  // REMOVED — gone since Security 6, still gone in 7

    @Autowired                                            // field injection
    private JwtAuthenticationFilter jwtAuthFilter;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()                             // non-lambda DSL — removed in Security 7, won't compile
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)  // should be STATELESS for JWT
            .and()                                            // and() removed from HttpSecurity in Security 7
            .authorizeRequests()                          // removed — use authorizeHttpRequests
            .antMatchers("/api/**").authenticated()        // antMatchers removed in Security 7 — use requestMatchers
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
