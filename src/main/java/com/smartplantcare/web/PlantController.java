package com.smartplantcare.web;

import com.smartplantcare.model.PlantData.CareEntry;
import com.smartplantcare.model.PlantData.Plant;
import com.smartplantcare.model.PlantData.Reminder;
import com.smartplantcare.security.AccountPrincipal;
import com.smartplantcare.service.PlantCareService;
import com.smartplantcare.service.PlantCareService.Dashboard;
import com.smartplantcare.service.PlantCareService.PlantInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PlantController {
    private final PlantCareService careService;

    public PlantController(PlantCareService careService) {
        this.careService = careService;
    }

    @GetMapping("/dashboard")
    public Dashboard dashboard(@AuthenticationPrincipal AccountPrincipal user) {
        return careService.dashboard(user.id(), user.fullName());
    }

    @GetMapping("/plants")
    public List<Plant> plants(@AuthenticationPrincipal AccountPrincipal user) {
        return careService.plants(user.id());
    }

    @PostMapping("/plants")
    @ResponseStatus(HttpStatus.CREATED)
    public Plant create(@AuthenticationPrincipal AccountPrincipal user, @Valid @RequestBody PlantRequest request) {
        return careService.create(user.id(), request.toInput());
    }

    @GetMapping("/plants/{id}")
    public Plant get(@AuthenticationPrincipal AccountPrincipal user, @PathVariable String id) {
        return careService.plant(user.id(), id);
    }

    @PutMapping("/plants/{id}")
    public Plant update(@AuthenticationPrincipal AccountPrincipal user, @PathVariable String id,
                        @Valid @RequestBody PlantRequest request) {
        return careService.update(user.id(), id, request.toInput());
    }

    @DeleteMapping("/plants/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AccountPrincipal user, @PathVariable String id) {
        careService.delete(user.id(), id);
    }

    @PostMapping("/plants/{id}/water")
    public Plant water(@AuthenticationPrincipal AccountPrincipal user, @PathVariable String id,
                       @RequestBody(required = false) WaterRequest request) {
        return careService.water(user.id(), id, request == null ? "" : request.notes());
    }

    @GetMapping("/reminders")
    public List<Reminder> reminders(@AuthenticationPrincipal AccountPrincipal user) {
        return careService.reminders(user.id());
    }

    @PostMapping("/reminders/{id}/complete")
    public Reminder completeReminder(@AuthenticationPrincipal AccountPrincipal user, @PathVariable String id) {
        return careService.completeReminder(user.id(), id);
    }

    @GetMapping("/history")
    public List<CareEntry> history(@AuthenticationPrincipal AccountPrincipal user) {
        return careService.history(user.id());
    }

    @PostMapping("/assistant")
    public AssistantResponse assistant(@AuthenticationPrincipal AccountPrincipal user,
                                       @Valid @RequestBody AssistantRequest request) {
        return new AssistantResponse(careService.answer(user.id(), request.message()));
    }

    public record PlantRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 100) String species,
            @NotBlank @Size(max = 80) String location,
            @Min(1) @Max(90) int wateringIntervalDays,
            @NotNull LocalDate lastWatered,
            @Pattern(regexp = "^(?:$|/images/[A-Za-z0-9_-]+\\.(?:jpg|jpeg|png|webp)|https://[A-Za-z0-9.-]+(?::[0-9]+)?(?:/[^\\s]*)?)$",
                    message = "Choose a bundled plant photo or use a secure https URL.") @Size(max = 1000) String imageUrl,
            @Size(max = 1000) String notes) {
        PlantInput toInput() {
            return new PlantInput(name, species, location, wateringIntervalDays, lastWatered, imageUrl, notes);
        }
    }

    public record WaterRequest(@Size(max = 500) String notes) {
    }

    public record AssistantRequest(@NotBlank @Size(max = 500) String message) {
    }

    public record AssistantResponse(String answer) {
    }
}
