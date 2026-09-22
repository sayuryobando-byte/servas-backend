package com.servas.web.catalog;

import com.servas.application.catalog.CompanyService;
import com.servas.application.catalog.CompanyService.CreateCompanyCommand;
import com.servas.application.catalog.CompanyService.UpdateCompanyCommand;
import com.servas.web.Api;
import com.servas.web.AuthResolver;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final AuthResolver authResolver;

    @GetMapping("/me")
    public ResponseEntity<?> getMyCompany(@RequestHeader(name = "Authorization", required = false) String authorization) {
        var outcome = authResolver.requireProvider(authorization)
            .flatMap(provider -> companyService.findByProviderId(provider.getId()));
        return Api.resolve(outcome.map(CompanyController::view), HttpStatus.OK);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> create(@RequestHeader(name = "Authorization", required = false) String authorization,
                                    @RequestParam String nit,
                                    @RequestParam String name,
                                    @RequestParam(required = false) String description,
                                    @RequestParam String address,
                                    @RequestParam(name = "social_media", required = false) String socialMedia,
                                    @RequestPart(required = false) MultipartFile logo) {
        var outcome = authResolver.requireProvider(authorization)
            .flatMap(provider -> companyService.create(provider.getId(),
                new CreateCompanyCommand(nit, name, description, address, socialMedia, null)));
        return Api.resolve(outcome.map(CompanyController::view), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> update(@RequestHeader(name = "Authorization", required = false) String authorization,
                                    @PathVariable UUID id,
                                    @RequestParam(required = false) String name,
                                    @RequestParam(required = false) String description,
                                    @RequestParam(required = false) String address,
                                    @RequestParam(name = "social_media", required = false) String socialMedia,
                                    @RequestPart(required = false) MultipartFile logo) {
        var outcome = authResolver.requireProvider(authorization)
            .flatMap(provider -> companyService.update(provider.getId(), id,
                new UpdateCompanyCommand(name, description, address, socialMedia, null)));
        return Api.resolve(outcome.map(CompanyController::view), HttpStatus.OK);
    }

    private static CompanyView view(com.servas.domain.entity.Company company) {
        return new CompanyView(company.getId(), company.getNit(), company.getName(), company.getDescription(),
            company.getAddress(), company.getSocialMedia(), company.getLogoUrl());
    }

    public record CompanyView(UUID id, String nit, String name, String description, String address,
                              String socialMedia, String logoUrl) {
    }
}