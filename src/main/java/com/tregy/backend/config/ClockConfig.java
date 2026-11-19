package com.tregy.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

// One injectable Clock, so token expiry, invite expiry, the weekly points window and the
// daily-completion rule are testable with a fixed or movable clock.
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
