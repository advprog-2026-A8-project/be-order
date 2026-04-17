package id.ac.ui.cs.advprog.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void shouldReturnStructuredBusinessError() throws Exception {
        mockMvc.perform(post("/test/business-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BUSINESS_ERROR"))
                .andExpect(jsonPath("$.message").value("boom"))
                .andExpect(jsonPath("$.path").value("/test/business-error"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void shouldReturnStructuredValidationError() throws Exception {
        mockMvc.perform(post("/test/validation-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(new ValidationPayload(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.path").value("/test/validation-error"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @RestController
    static class ThrowingController {
        @PostMapping("/test/business-error")
        public ResponseEntity<Void> businessError() {
            throw new IllegalArgumentException("boom");
        }

        @PostMapping("/test/validation-error")
        public ResponseEntity<Void> validationError(@Valid @RequestBody ValidationPayload payload) {
            return ResponseEntity.ok().build();
        }
    }

    static class ValidationPayload {
        @NotBlank(message = "name is required")
        private final String name;

        ValidationPayload(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
