package com.knohub.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Previously initialized sample data; now disabled to keep database empty by default.
 */
@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    @Override
    public void run(String... args) {
        log.info("Sample data initialization is disabled; skipping.");
    }
}
