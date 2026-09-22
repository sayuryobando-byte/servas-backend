package com.servas.web.report;

import com.servas.application.report.ReportService;
import com.servas.web.Api;
import com.servas.web.AuthResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private static final int TOP_DEMAND_LIMIT = 10;

    private final ReportService reportService;
    private final AuthResolver authResolver;

    @GetMapping("/reports/reservations")
    public ResponseEntity<?> reservations(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return secured(authorization, reportService::reservationsReport);
    }

    @GetMapping("/reports/occupancy")
    public ResponseEntity<?> occupancy(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return secured(authorization, reportService::occupancy);
    }

    @GetMapping("/reports/demand")
    public ResponseEntity<?> demand(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return secured(authorization, () -> reportService.topDemand(TOP_DEMAND_LIMIT));
    }

    private <T> ResponseEntity<?> secured(String authorization, java.util.function.Supplier<T> producer) {
        var provider = authResolver.requireProvider(authorization);
        if (provider.isFailure()) {
            return Api.resolve(provider, HttpStatus.OK);
        }
        return ResponseEntity.ok(producer.get());
    }
}