package com.umdc.smartkin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/// Provides the clock services use to read the current time, so tests can fix it.
@Configuration
public class ClockConfig {

    /// The system clock in UTC.
    ///
    /// @return the clock
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
