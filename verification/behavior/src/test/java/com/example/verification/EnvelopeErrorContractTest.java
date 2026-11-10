package com.example.verification;

import com.example.common.exception.GlobalExceptionHandler;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The rest-api-conventions envelope handler must keep client errors as 4xx, not collapse them into 500. */
class EnvelopeErrorContractTest {
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new Controller())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void mapsDomainNotFoundAndValidation() throws Exception {
        mvc.perform(get("/orders/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        mvc.perform(post("/orders").contentType("application/json").content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
    }

    @Test void keepsFrameworkClientErrorsAs4xx() throws Exception {
        mvc.perform(post("/orders").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/orders")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/orders/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/no-such-endpoint")).andExpect(status().isNotFound());
    }

    @Test void mapsMethodSecurityDenialTo403() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test void hidesUnexpectedDetails() throws Exception {
        mvc.perform(get("/boom")).andExpect(status().isInternalServerError())
                .andExpect(content().string(not(containsString("database-password"))));
    }

    record Request(@NotBlank String name) {}

    @RestController static class Controller {
        @GetMapping("/orders/{id}") String find(@PathVariable UUID id) { throw new EntityNotFoundException("Order " + id); }
        @PostMapping("/orders") String create(@Valid @RequestBody Request request) { return request.name(); }
        @GetMapping("/admin") String admin() { throw new AccessDeniedException("denied"); }
        @GetMapping("/boom") String boom() { throw new IllegalStateException("database-password"); }
    }
}
