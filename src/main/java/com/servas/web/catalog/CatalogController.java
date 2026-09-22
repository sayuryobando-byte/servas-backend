package com.servas.web.catalog;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.servas.application.catalog.CatalogService;
import com.servas.application.catalog.ServiceAdminService;
import com.servas.application.catalog.ServiceAdminService.CreateServiceCommand;
import com.servas.application.catalog.ServiceAdminService.UpdateServiceCommand;
import com.servas.domain.entity.Comuna;
import com.servas.domain.entity.Service;
import com.servas.domain.enumeration.Modality;
import com.servas.web.Api;
import com.servas.web.AuthResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "*")
@RestController
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final ServiceAdminService serviceAdminService;
    private final AuthResolver authResolver;

    @GetMapping("/comunas")
    public ResponseEntity<List<ComunaView>> comunas() {
        return ResponseEntity.ok(catalogService.comunas().stream()
            .map(CatalogController::view)
            .toList());
    }

    @GetMapping("/services")
    public ResponseEntity<List<ServiceView>> search(@RequestParam(required = false) UUID companyId,
                                                    @RequestParam(required = false) Integer comunaId,
                                                    @RequestParam(required = false) Modality modality) {
        return ResponseEntity.ok(catalogService.search(companyId, comunaId, modality).stream()
            .map(CatalogController::view)
            .toList());
    }

    @GetMapping("/services/{id}")
    public ResponseEntity<?> detail(@PathVariable UUID id) {
        return Api.resolve(catalogService.detail(id).map(CatalogController::view), HttpStatus.OK);
    }

    @PostMapping("/services")
    public ResponseEntity<?> create(@RequestHeader(name = "Authorization", required = false) String authorization,
                                    @RequestBody CreateServiceRequest request) {
        var outcome = authResolver.requireProvider(authorization)
            .flatMap(provider -> serviceAdminService.create(provider.getId(), request.toCommand()));
        return Api.resolve(outcome.map(CatalogController::view), HttpStatus.CREATED);
    }

    @PutMapping("/services/{id}")
    public ResponseEntity<?> update(@RequestHeader(name = "Authorization", required = false) String authorization,
                                    @PathVariable UUID id,
                                    @RequestBody UpdateServiceRequest request) {
        var outcome = authResolver.requireProvider(authorization)
            .flatMap(provider -> serviceAdminService.update(provider.getId(), id, request.toCommand()));
        return Api.resolve(outcome.map(CatalogController::view), HttpStatus.OK);
    }

    @PatchMapping("/services/{id}/status")
    public ResponseEntity<?> changeStatus(@RequestHeader(name = "Authorization", required = false) String authorization,
                                          @PathVariable UUID id,
                                          @RequestBody StatusRequest request) {
        var outcome = authResolver.requireProvider(authorization)
            .flatMap(provider -> serviceAdminService.changeStatus(provider.getId(), id,
                Boolean.TRUE.equals(request.isActive())));
        return Api.resolve(outcome.map(CatalogController::view), HttpStatus.OK);
    }

    private static ComunaView view(Comuna comuna) {
        return new ComunaView(comuna.getId(), comuna.getName());
    }

    private static ServiceView view(Service service) {
        return new ServiceView(service.getId(),
            service.getCompany().getId(),
            service.getCompany().getName(),
            service.getComuna() != null ? service.getComuna().getId() : null,
            service.getComuna() != null ? service.getComuna().getName() : null,
            service.getName(),
            service.getModality(),
            service.getCost(),
            service.getDurationMinutes(),
            service.getDescription(),
            service.getRecommendations(),
            service.getStartDate(),
            service.getEndDate(),
            service.isActive());
    }

    public record ComunaView(Integer id, String name) {
    }

    public record ServiceView(UUID id, UUID companyId, String companyName, Integer comunaId, String comunaName,
                              String name, Modality modality, BigDecimal cost, Integer durationMinutes,
                              String description, String recommendations, LocalDate startDate,
                              LocalDate endDate, boolean isActive) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CreateServiceRequest(
        @JsonProperty("company_id") UUID companyId,
        @JsonProperty("comuna_id") Integer comunaId,
        String name,
        Modality modality,
        BigDecimal cost,
        @JsonProperty("duration_minutes") Integer durationMinutes,
        String description,
        String recommendations,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate
    ) {

        public CreateServiceCommand toCommand() {
            return new CreateServiceCommand(companyId, comunaId, name, modality, cost, durationMinutes,
                description, recommendations, startDate, endDate);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record UpdateServiceRequest(
        @JsonProperty("comuna_id") Integer comunaId,
        String name,
        Modality modality,
        BigDecimal cost,
        @JsonProperty("duration_minutes") Integer durationMinutes,
        String description,
        String recommendations,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate,
        @JsonProperty("is_active") Boolean isActive
    ) {

        public UpdateServiceCommand toCommand() {
            return new UpdateServiceCommand(comunaId, name, modality, cost, durationMinutes, description,
                recommendations, startDate, endDate, isActive);
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record StatusRequest(
        @JsonProperty("is_active") Boolean isActive
    ) {
    }
}