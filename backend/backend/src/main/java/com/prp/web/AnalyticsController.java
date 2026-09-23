package com.prp.web;

import com.prp.service.AnalyticsService;
import com.prp.web.dto.ReasonCountDto;
import com.prp.web.dto.SummaryDto;
import com.prp.web.dto.TrendPointDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public SummaryDto summary(@RequestParam(defaultValue = "14") int days) {
        return analytics.summary(clamp(days));
    }

    @GetMapping("/trend")
    public List<TrendPointDto> trend(@RequestParam(defaultValue = "14") int days) {
        return analytics.trend(clamp(days));
    }

    @GetMapping("/failure-reasons")
    public List<ReasonCountDto> failureReasons(@RequestParam(defaultValue = "14") int days) {
        return analytics.failureReasons(clamp(days));
    }

    private static int clamp(int days) {
        return Math.max(1, Math.min(days, 90));
    }
}
