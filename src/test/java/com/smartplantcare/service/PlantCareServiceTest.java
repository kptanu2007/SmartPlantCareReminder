package com.smartplantcare.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartplantcare.model.PlantData.Plant;
import com.smartplantcare.storage.JsonStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class PlantCareServiceTest {
    @TempDir
    Path directory;

    @Test
    void isolatesPlantsAndRecordsWateringAndNextReminder() {
        PlantCareService service = new PlantCareService(
                new JsonStore(new ObjectMapper().findAndRegisterModules(), directory.toString()));
        Plant created = service.create("owner-one", new PlantCareService.PlantInput(
                "Window fern", "Fern", "Study", 5, LocalDate.now().minusDays(6), "", ""));

        assertEquals(1, service.plants("owner-one").size());
        assertTrue(service.plants("owner-two").isEmpty());
        assertThrows(ResponseStatusException.class, () -> service.plant("owner-two", created.id()));
        assertEquals(1, service.dashboard("owner-one", "Avery").totalPlants());
        assertEquals(1, service.dashboard("owner-one", "Avery").plantsDue());
        assertEquals(0, service.dashboard("owner-one", "Avery").plantsOnSchedule());

        Plant watered = service.water("owner-one", created.id(), "Soil was dry");
        assertEquals(LocalDate.now(), watered.lastWatered());
        assertEquals(LocalDate.now().plusDays(5), watered.nextWateringDate());
        assertEquals(0, service.dashboard("owner-one", "Avery").plantsDue());
        assertEquals(1, service.dashboard("owner-one", "Avery").plantsOnSchedule());
        assertEquals(1, service.history("owner-one").size());
        assertEquals("Soil was dry", service.history("owner-one").get(0).notes());
        assertEquals(LocalDate.now().plusDays(5), service.reminders("owner-one").stream()
                .filter(reminder -> !reminder.completed()).findFirst().orElseThrow().dueDate());
        assertTrue(service.history("owner-two").isEmpty());
    }

    @Test
    void completingAReminderPersistsCareAndCreatesTheNextSchedule() {
        PlantCareService service = new PlantCareService(
                new JsonStore(new ObjectMapper().findAndRegisterModules(), directory.toString()));
        Plant plant = service.create("owner-one", new PlantCareService.PlantInput(
                "Peace Lily", "Peace Lily", "Bedroom", 6, LocalDate.now().minusDays(7), "", ""));
        var reminder = service.reminders("owner-one").get(0);

        var completed = service.completeReminder("owner-one", reminder.id());

        assertTrue(completed.completed());
        assertEquals(LocalDate.now(), service.plant("owner-one", plant.id()).lastWatered());
        assertEquals(1, service.history("owner-one").size());
        assertEquals(2, service.reminders("owner-one").size());
        assertTrue(service.reminders("owner-one").stream().anyMatch(item -> !item.completed()
                && item.dueDate().equals(LocalDate.now().plusDays(6))));
    }
}
