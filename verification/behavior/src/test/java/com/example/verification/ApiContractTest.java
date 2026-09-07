package com.example.verification;

import com.example.common.api.ApiResponse;
import com.example.common.api.PageResponse;
import com.example.common.exception.ProblemDetailExceptionHandler;
import com.example.order.exception.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ApiContractTest {
    private MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new Controller())
            .setControllerAdvice(new ProblemDetailExceptionHandler()).build();
    }

    @Test void mapsNotFound() throws Exception {
        mvc.perform(get("/orders/missing")).andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
            .andExpect(jsonPath("$.errorCode").value("ORDER_NOT_FOUND"));
    }
    @Test void mapsInventoryAndBusinessRule() throws Exception {
        mvc.perform(get("/orders/inventory")).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_INVENTORY"));
        mvc.perform(get("/orders/rule")).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }
    @Test void hidesUnexpectedDetails() throws Exception {
        mvc.perform(get("/orders/failure")).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
            .andExpect(content().string(not(containsString("database-password"))));
    }
    @Test void validatesAndPreservesFrameworkErrors() throws Exception {
        mvc.perform(post("/orders").contentType("application/json").content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.violations[0].field").value("name"));
        mvc.perform(post("/orders").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/orders")).andExpect(status().isMethodNotAllowed());
    }
    @Test void pageContractDoesNotExposePageImplInternals() throws Exception {
        mvc.perform(get("/page")).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0]").value("order"))
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(2))
            .andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.totalPages").value(2))
            .andExpect(jsonPath("$.data.last").value(false))
            .andExpect(jsonPath("$.data.pageable").doesNotExist())
            .andExpect(jsonPath("$.data.sort").doesNotExist());
    }

    record Request(@NotBlank String name) {}
    @RestController static class Controller {
        @GetMapping("/orders/{kind}") String failure(@PathVariable String kind) {
            throw switch (kind) {
                case "missing" -> new OrderNotFoundException(UUID.randomUUID());
                case "inventory" -> new InsufficientInventoryException(UUID.randomUUID(), 2, 1);
                case "rule" -> new BusinessRuleViolationException("Rule failed");
                default -> new IllegalStateException("database-password");
            };
        }
        @PostMapping("/orders") String create(@Valid @RequestBody Request request) { return request.name(); }
        @GetMapping("/page") ApiResponse<PageResponse<String>> page() {
            return ApiResponse.ok(PageResponse.from(new PageImpl<>(List.of("order"), PageRequest.of(0,2),3)));
        }
    }
}
