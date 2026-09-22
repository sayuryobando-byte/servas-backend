package com.servas.web.schedule;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.servas.application.schedule.ScheduleService;
import com.servas.application.schedule.ScheduleService.AvailabilitySlot;
import com.servas.application.schedule.ScheduleService.ScheduleItem;
import com.servas.domain.entity.Schedule;
import com.servas.web.Api;
import com.servas.web.AuthResolver;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final AuthResolver authResolver;

    @PostMapping("/services/{id}/schedules")
    public ResponseEntity<?> configure(@RequestHeader(name = "Authorization", required = false) String authorization,
                                       @PathVariable UUID id,
                                       @RequestBody List<ScheduleItemRequest> items) {
        var outcome = authResolver.requireProvider(authorization)
            .flatMap(provider -> scheduleService.configure(provider.getId(), id, items.stream().map(ScheduleItemRequest::toItem).toList()));
        return Api.resolve(outcome.map(ScheduleController::viewSchedules), HttpStatus.CREATED);
    }

    @PutMapping("/services/{id}/schedules")
    public ResponseEntity<?> replace(@RequestHeader(name = "Authorization", required = false) String authorization,
                                     @PathVariable UUID id,
                                     @RequestBody List<ScheduleItemRequest> items) {
        var outcome = authResolver.requireProvider(authorization)
            .flatMap(provider -> scheduleService.replace(provider.getId(), id, items.stream().map(ScheduleItemRequest::toItem).toList()));
        return Api.resolve(outcome.map(ScheduleController::viewSchedules), HttpStatus.OK);
    }

    @GetMapping("/services/{id}/availability")
    public ResponseEntity<?> availability(@PathVariable UUID id,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Api.resolve(scheduleService.availableSlots(id, date).map(ScheduleController::viewSlots), HttpStatus.OK);
    }

    private static List<ScheduleView> viewSchedules(List<Schedule> schedules) {
        return schedules.stream()
            .map(schedule -> new ScheduleView(schedule.getId(), schedule.getDayOfWeek(),
                schedule.getStartTime(), schedule.getEndTime()))
            .toList();
    }

    private static List<SlotView> viewSlots(List<AvailabilitySlot> slots) {
        return slots.stream()
            .map(slot -> new SlotView(slot.startTime(), slot.endTime()))
            .toList();
    }

    public record ScheduleView(UUID id, Integer dayOfWeek,
                               @JsonFormat(pattern = "HH:mm") LocalTime startTime,
                               @JsonFormat(pattern = "HH:mm") LocalTime endTime) {
    }

    public record SlotView(@JsonFormat(pattern = "HH:mm") LocalTime startTime,
                           @JsonFormat(pattern = "HH:mm") LocalTime endTime) {
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ScheduleItemRequest(@JsonFormat(pattern = "HH:mm") LocalTime startTime,
                                      @JsonFormat(pattern = "HH:mm") LocalTime endTime,
                                      Integer dayOfWeek) {

        public ScheduleItem toItem() {
            return new ScheduleItem(dayOfWeek, startTime, endTime);
        }
    }
}