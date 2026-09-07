package com.example.verification;

import com.example.auth.config.SecurityConfig;
import com.example.auth.filter.JwtAuthenticationFilter;
import com.example.auth.service.JwtService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SecurityChainTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class App {
        @Bean UserDetailsService users() {
            return name -> User.withUsername(name).password("unused").roles("USER").build();
        }
        @Bean JwtService jwtService() {
            var service = new JwtService();
            ReflectionTestUtils.setField(service, "secretKey",
                "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=");
            ReflectionTestUtils.setField(service, "accessTokenExpiration", 900000L);
            return service;
        }
        @Bean JwtAuthenticationFilter jwtFilter(JwtService service, UserDetailsService users) {
            return new JwtAuthenticationFilter(service, users);
        }
        @Bean Controller controller() { return new Controller(); }
    }
    @RestController static class Controller {
        @GetMapping({"/api/v1/orders", "/api/v1/admin/orders", "/api/v1/auth/ping"})
        String ok() { return "ok"; }
    }

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource(
            "test", java.util.Map.of("app.jwt.secret", "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=")));
        context.register(App.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }
    @AfterEach void cleanup() { if (context != null) context.close(); }

    @Test void anonymousProtectedRequestIs401() throws Exception {
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized())
            .andExpect(header().string("WWW-Authenticate", "Bearer"))
            .andExpect(jsonPath("$.status").value(401));
    }
    @Test void publicRouteAllowsAnonymousButRejectsInvalidToken() throws Exception {
        mvc.perform(get("/api/v1/auth/ping")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/ping").header("Authorization", "Bearer invalid"))
            .andExpect(status().isUnauthorized());
    }
    @Test void authenticatesAndEnforcesRolesWithoutLeakingContext() throws Exception {
        var user = context.getBean(UserDetailsService.class).loadUserByUsername("alice");
        String token = context.getBean(JwtService.class).generateAccessToken(user);
        mvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/orders").header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        mvc.perform(get("/api/v1/orders")).andExpect(status().isUnauthorized());
    }
}
