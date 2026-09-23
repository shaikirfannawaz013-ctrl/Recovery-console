package com.prp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.ZoneId;

@Configuration
public class TimeConfig {
    /** Business time zone used for payday and "best hour" retry timing. */
    @Bean
    public ZoneId businessZone(AppProperties props) {
        return ZoneId.of(props.timezone());
    }
}
