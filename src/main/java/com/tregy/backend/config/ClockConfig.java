package com.tregy.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// One injectable Clock, so token expiry, the weekly emergency-pass window and the
// SRS due dates are testable with a fixed or movable clock.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
