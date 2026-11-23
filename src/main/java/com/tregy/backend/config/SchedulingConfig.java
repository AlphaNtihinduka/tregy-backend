package com.tregy.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Switches on @Scheduled. The only scheduled job today is the account-deletion purge (ADR 0009).
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
