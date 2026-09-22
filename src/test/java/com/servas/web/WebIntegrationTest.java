package com.servas.web;

import com.servas.common.constant.Errors;
import com.servas.support.IntegrationTestBase;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebIntegrationTest extends IntegrationTestBase {

    private static final String BIRTH_DATE = "1990-01-01";
    private static final String PASSWORD = "Pass1234!";

    @Autowired
    private ObjectMapper om;

    @Value("${local.server.port}")
    private int port;

    private RestClient rest;

    @Test
    void fullJourneyAcrossEveryController() throws Exception {
        rest = RestClient.builder()
            .baseUrl("http://127.0.0.1:" + port)
            .requestFactory(new JdkClientHttpRequestFactory())
            .build();

        var email = unique("web") + "@servas.test";
        var document = "DOC-" + unique("wd");

        var registered = postJson("/auth/register", registerBody(email, document, PASSWORD));
        assertStatus(registered, 201);
        assertThat(body(registered).path("email").stringValue()).isEqualTo(email);

        var duplicate = postJson("/auth/register", registerBody(email, document, PASSWORD));
        assertStatus(duplicate, 409);
        assertCode(duplicate, Errors.EMAIL_TAKEN.code());

        var unverifiedLogin = postJson("/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
        assertStatus(unverifiedLogin, 403);
        assertCode(unverifiedLogin, Errors.EMAIL_NOT_VERIFIED.code());

        var verified = postJson("/auth/verify-email",
            "{\"email\":\"" + email + "\",\"otp_code\":\"" + otpOf(email) + "\"}");
        assertStatus(verified, 200);

        var login = postJson("/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}");
        assertStatus(login, 200);
        var token = body(login).path("token").stringValue();

        var badLogin = postJson("/auth/login", "{\"email\":\"" + email + "\",\"password\":\"Mala123!\"}");
        assertStatus(badLogin, 401);
        assertCode(badLogin, Errors.INVALID_CREDENTIALS.code());

        assertStatus(postJson("/auth/verify-email/resend", "{\"email\":\"" + email + "\"}"), 200);
        assertStatus(postJson("/auth/forgot-password", "{\"email\":\"" + email + "\"}"), 200);

        var company = postMultipart("/companies", "Bearer " + token);
        assertStatus(company, 201);
        var companyId = body(company).path("id").stringValue();

        var companyUpdate = putMultipart("/companies/" + companyId, "Bearer " + token);
        assertStatus(companyUpdate, 200);
        assertThat(body(companyUpdate).path("name").stringValue()).isEqualTo("Empresa Actualizada");

        var createdService = postJson("/services", serviceBody(companyId), "Bearer " + token);
        assertStatus(createdService, 201);
        var serviceId = body(createdService).path("id").stringValue();

        var noAuth = postJson("/services", serviceBody(companyId));
        assertStatus(noAuth, 401);
        assertCode(noAuth, Errors.AUTH_REQUIRED.code());

        var brokenToken = postJson("/services", serviceBody(companyId), "Bearer token-roto");
        assertStatus(brokenToken, 401);
        assertCode(brokenToken, Errors.TOKEN_INVALID.code());

        var schedules = "[{\"start_time\":\"09:00\",\"end_time\":\"13:00\",\"day_of_week\":1},"
            + "{\"start_time\":\"09:00\",\"end_time\":\"13:00\",\"day_of_week\":2},"
            + "{\"start_time\":\"09:00\",\"end_time\":\"13:00\",\"day_of_week\":3},"
            + "{\"start_time\":\"09:00\",\"end_time\":\"13:00\",\"day_of_week\":4},"
            + "{\"start_time\":\"09:00\",\"end_time\":\"13:00\",\"day_of_week\":5},"
            + "{\"start_time\":\"09:00\",\"end_time\":\"13:00\",\"day_of_week\":6},"
            + "{\"start_time\":\"09:00\",\"end_time\":\"13:00\",\"day_of_week\":7}]";
        assertStatus(postJson("/services/" + serviceId + "/schedules", schedules, "Bearer " + token), 201);
        assertStatus(putJson("/services/" + serviceId + "/schedules",
            "[{\"start_time\":\"08:00\",\"end_time\":\"11:00\",\"day_of_week\":6},"
                + "{\"start_time\":\"09:00\",\"end_time\":\"13:00\",\"day_of_week\":2}]", "Bearer " + token), 200);

        var date = LocalDate.now().plusWeeks(3).with(TemporalAdjusters.next(DayOfWeek.TUESDAY)).toString();
        var availability = getJson("/services/" + serviceId + "/availability?date=" + date);
        assertStatus(availability, 200);
        assertThat(body(availability).size()).isEqualTo(4);

        var clientDoc = "DOC-C-" + unique("wc");
        var reservation = postJson("/reservations", reservationBody(serviceId, date, clientDoc));
        assertStatus(reservation, 201);
        var reservationId = body(reservation).path("id").stringValue();

        var detail = getJson("/reservations/" + reservationId);
        assertStatus(detail, 200);
        assertThat(body(detail).path("status").stringValue()).isEqualTo("CONFIRMED");

        var ghost = getJson("/reservations/" + UUID.randomUUID());
        assertStatus(ghost, 404);
        assertCode(ghost, Errors.RESERVATION_NOT_FOUND.code());

        assertStatus(putJson("/reservations/" + reservationId,
            "{\"reservation_date\":\"" + date + "\",\"start_time\":\"10:00\",\"end_time\":\"11:00\"}"), 200);
        var modified = getJson("/reservations/" + reservationId);
        assertThat(body(modified).path("startTime").stringValue()).isEqualTo("10:00");

        assertStatus(patchJson("/reservations/" + reservationId + "/cancel",
            "{\"cancelled_by\":\"CLIENT\",\"cancellation_reason\":\"Cambio de plan\"}"), 200);
        var cancelled = getJson("/reservations/" + reservationId);
        assertThat(body(cancelled).path("status").stringValue()).isEqualTo("CANCELLED");

        var afterCancel = getJson("/services/" + serviceId + "/availability?date=" + date);
        assertThat(body(afterCancel).size()).isEqualTo(4);

        assertStatus(postJson("/blocked-dates",
            "{\"company_id\":\"" + companyId + "\",\"block_date\":\"" + date + "\",\"reason\":\"Feriado\"}",
            "Bearer " + token), 201);
        var blockedByService = postJson("/blocked-dates",
            "{\"company_id\":\"" + companyId + "\",\"service_id\":\"" + serviceId
                + "\",\"block_date\":\"" + date + "\",\"reason\":\"Mantenimiento\"}",
            "Bearer " + token);
        assertStatus(blockedByService, 201);
        assertThat(body(blockedByService).path("serviceId").stringValue()).isEqualTo(serviceId);
        var afterBlock = getJson("/services/" + serviceId + "/availability?date=" + date);
        assertThat(body(afterBlock)).isEmpty();

        var blockedReservation = postJson("/reservations", reservationBody(serviceId, date, "DOC-" + unique("wz")));
        assertStatus(blockedReservation, 400);
        assertCode(blockedReservation, Errors.DATE_BLOCKED.code());

        var byDocument = getJson("/reservations/client/" + clientDoc);
        assertStatus(byDocument, 200);
        assertThat(body(byDocument).size()).isEqualTo(1);

        var agenda = getJson("/provider/reservations?status=CONFIRMED", "Bearer " + token);
        assertStatus(agenda, 200);
        assertThat(body(agenda).isEmpty()).isTrue();

        assertStatus(getJson("/services?company_id=" + companyId), 200);
        assertStatus(getJson("/services/" + serviceId), 200);
        assertStatus(getJson("/comunas"), 200);

        assertStatus(putJson("/services/" + serviceId, "{\"name\":\"Nuevo nombre\"}", "Bearer " + token), 200);
        var toggled = patchJson("/services/" + serviceId + "/status", "{\"is_active\":false}", "Bearer " + token);
        assertStatus(toggled, 200);
        var toggledDetail = getJson("/services/" + serviceId);
        assertThat(body(toggledDetail).path("is_active").asBoolean()).isFalse();

        assertStatus(getJson("/reports/reservations", "Bearer " + token), 200);
        assertStatus(getJson("/reports/occupancy", "Bearer " + token), 200);
        assertStatus(getJson("/reports/demand", "Bearer " + token), 200);
        var reportNoAuth = getJson("/reports/reservations");
        assertStatus(reportNoAuth, 401);
        assertCode(reportNoAuth, Errors.AUTH_REQUIRED.code());

        assertStatus(postAuthLogout(""), 200);
        assertStatus(postAuthLogout(token), 200);
    }

    private JsonNode postJson(String path, String body) throws Exception {
        return send(rest.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private JsonNode postJson(String path, String body, String authorization) throws Exception {
        return send(rest.post().uri(path).header("Authorization", authorization)
            .contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private JsonNode putJson(String path, String body) throws Exception {
        return send(rest.put().uri(path).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private JsonNode putJson(String path, String body, String authorization) throws Exception {
        return send(rest.put().uri(path).header("Authorization", authorization)
            .contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private JsonNode patchJson(String path, String body) throws Exception {
        return send(rest.patch().uri(path).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private JsonNode patchJson(String path, String body, String authorization) throws Exception {
        return send(rest.patch().uri(path).header("Authorization", authorization)
            .contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private JsonNode getJson(String path) throws Exception {
        return send(rest.get().uri(path));
    }

    private JsonNode getJson(String path, String authorization) throws Exception {
        return send(rest.get().uri(path).header("Authorization", authorization));
    }

    private JsonNode postAuthLogout(String token) throws Exception {
        return send(rest.post().uri("/auth/logout").header("Authorization", "Bearer " + token));
    }

    private JsonNode postMultipart(String path, String authorization) throws Exception {
        var builder = new MultipartBodyBuilder();
        builder.part("nit", "NIT-" + unique("wn"));
        builder.part("name", "Empresa " + unique("w"));
        builder.part("description", "Descripción");
        builder.part("address", "Calle 1 # 2-3");
        builder.part("social_media", "@redessociales");
        builder.part("logo", new byte[]{1, 2, 3}).contentType(MediaType.IMAGE_PNG).filename("logo.png");
        return send(rest.post().uri(path).header("Authorization", authorization).body(builder.build()));
    }

    private JsonNode putMultipart(String path, String authorization) throws Exception {
        var builder = new MultipartBodyBuilder();
        builder.part("name", "Empresa Actualizada");
        builder.part("description", "Descripción actualizada");
        return send(rest.put().uri(path).header("Authorization", authorization).body(builder.build()));
    }

    private JsonNode send(RequestHeadersSpec<?> spec) throws Exception {
        try {
            var entity = spec.retrieve().toEntity(String.class);
            var raw = entity.getBody();
            var wrapped = om.createObjectNode();
            wrapped.put("__status", entity.getStatusCode().value());
            wrapped.set("__body", raw == null || raw.isBlank() ? om.createObjectNode() : om.readTree(raw));
            return wrapped;
        } catch (HttpStatusCodeException e) {
            var wrapped = om.createObjectNode();
            wrapped.put("__status", e.getStatusCode().value());
            var rawBody = e.getResponseBodyAsString();
            wrapped.set("__body", rawBody == null || rawBody.isBlank() ? om.createObjectNode() : om.readTree(rawBody));
            return wrapped;
        }
    }

    private JsonNode body(JsonNode wrapped) {
        return wrapped.path("__body");
    }

    private void assertStatus(JsonNode wrapped, int expected) {
        assertThat(wrapped.path("__status").asInt())
            .as("estado http")
            .isEqualTo(expected);
    }

    private void assertCode(JsonNode wrapped, String code) {
        assertThat(body(wrapped).path("code").stringValue())
            .as("código de error")
            .isEqualTo(code);
    }

    private String registerBody(String email, String document, String password) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + password
            + "\",\"document_type\":\"CC\",\"document_number\":\"" + document
            + "\",\"first_name\":\"Ana\",\"last_name\":\"Perez\",\"birth_date\":\"" + BIRTH_DATE
            + "\",\"phone\":\"3000000000\"}";
    }

    private String serviceBody(String companyId) {
        return "{\"company_id\":\"" + (companyId == null ? "" : companyId)
            + "\",\"name\":\"Corte de pelo " + unique("ws")
            + "\",\"modality\":\"VIRTUAL\",\"cost\":10000,\"duration_minutes\":60,\"description\":\"Prueba\"}";
    }

    private String reservationBody(String serviceId, String date, String clientDoc) {
        return "{\"service_id\":\"" + serviceId + "\",\"reservation_date\":\"" + date
            + "\",\"start_time\":\"09:00\",\"end_time\":\"10:00\",\"client\":{"
            + "\"document_type\":\"CC\",\"document_number\":\"" + clientDoc
            + "\",\"first_name\":\"Luis\",\"last_name\":\"Gomez\",\"phone\":\"3001112233\","
            + "\"email\":\"" + unique("wc2") + "@m.com\"}}";
    }
}