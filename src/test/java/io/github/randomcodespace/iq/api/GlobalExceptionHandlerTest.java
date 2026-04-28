package io.github.randomcodespace.iq.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new ExplodingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uncaughtRuntimeException_returns500_envelope_noStackTrace() throws Exception {
        mvc.perform(get("/explode/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An internal error occurred."))
                .andExpect(jsonPath("$.request_id").exists())
                // Body must NOT leak stack frames or class names.
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at io.github"))))
                .andExpect(content().string(not(containsString("ExplodingController"))));
    }

    @Test
    void illegalArgumentException_returns400_withMessage() throws Exception {
        mvc.perform(get("/explode/illegal").param("why", "missing-required-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.message").value("missing-required-param"))
                .andExpect(jsonPath("$.request_id").exists());
    }

    @Test
    void responseStatusException_passesStatusThrough() throws Exception {
        mvc.perform(get("/explode/notfound"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("nope"))
                .andExpect(jsonPath("$.request_id").exists());
    }

    @RestController
    static class ExplodingController {

        @GetMapping("/explode/runtime")
        public String runtime() {
            throw new RuntimeException("internal db pool drained at /Users/secret/path");
        }

        @GetMapping("/explode/illegal")
        public String illegal(@RequestParam String why) {
            throw new IllegalArgumentException(why);
        }

        @GetMapping("/explode/notfound")
        public String notfound() {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "nope");
        }
    }
}
