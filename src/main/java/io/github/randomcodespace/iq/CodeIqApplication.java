package io.github.randomcodespace.iq;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class CodeIqApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeIqApplication.class, args);
    }
}
