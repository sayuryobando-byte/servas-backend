package com.servas.web.schedule;

import com.servas.application.schedule.BlockedDateService;
import com.servas.application.schedule.BlockedDateService.BlockDateCommand;
import com.servas.domain.entity.BlockedDate;
import com.servas.web.Api;
import com.servas.web.AuthResolver;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BlockedDateController {

    private final BlockedDateService blockedDateService;
    private final AuthResolver authResolver;

    @PostMapping("/blocked-dates")
    public ResponseEntity<?> block(@RequestHeader(name = "Authorization", required = false) String authorization,
                                   @RequestBody BlockDateRequest request) {
        var outcome = authResolver.requireProvider(authorization)
            .flatMap(provider -> blockedDateService.block(provider.getId(), new BlockDateCommand(
                request.companyId(), request.serviceId(), request.blockDate(), request.reason())));
        return Api.resolve(outcome.map(BlockedDateController::view), HttpStatus.CREATED);
    }

    private static BlockedDateView view(BlockedDate blockedDate) {
        return new BlockedDateView(blockedDate.getId(),
            blockedDate.getCompany().getId(),
            blockedDate.getService() != null ? blockedDate.getService().getId() : null,
            blockedDate.getBlockDate(),
            blockedDate.getReason());
    }

    public record BlockedDateView(UUID id, UUID companyId, UUID serviceId, LocalDate blockDate, String reason) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record BlockDateRequest(UUID companyId, UUID serviceId, LocalDate blockDate, String reason) {
    }
}