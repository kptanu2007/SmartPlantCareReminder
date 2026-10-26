package com.smartplantcare.service;

import com.smartplantcare.model.PlantData.CareEntry;
import com.smartplantcare.model.PlantData.Plant;
import com.smartplantcare.model.PlantData.Reminder;
import com.smartplantcare.storage.JsonStore;
import com.smartplantcare.storage.JsonStore.Data;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PlantCareService {
    private final JsonStore store;

    public PlantCareService(JsonStore store) {
        this.store = store;
    }

    public List<Plant> plants(String ownerId) {
        return store.snapshot().plants.stream().filter(plant -> plant.ownerId().equals(ownerId))
                .sorted(Comparator.comparing(Plant::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public Plant plant(String ownerId, String id) {
        return findOwnedPlant(store.snapshot(), ownerId, id);
    }

    public Plant create(String ownerId, PlantInput input) {
        Instant now = Instant.now();
        Plant plant = new Plant(UUID.randomUUID().toString(), ownerId, input.name().trim(),
                input.species().trim(), input.location().trim(), input.wateringIntervalDays(),
                input.lastWatered(), normalizeImage(input.imageUrl()), cleanNotes(input.notes()), now, now);
        store.update(data -> {
            data.plants.add(plant);
            schedule(data, plant);
            return data;
        }, data -> null);
        return plant;
    }

    public Plant update(String ownerId, String id, PlantInput input) {
        return store.update(data -> {
            Plant old = findOwnedPlant(data, ownerId, id);
            Plant changed = new Plant(old.id(), old.ownerId(), input.name().trim(), input.species().trim(),
                    input.location().trim(), input.wateringIntervalDays(), input.lastWatered(),
                    normalizeImage(input.imageUrl()), cleanNotes(input.notes()), old.createdAt(), Instant.now());
            data.plants.replaceAll(plant -> plant.id().equals(id) && plant.ownerId().equals(ownerId) ? changed : plant);
            data.reminders.removeIf(reminder -> reminder.ownerId().equals(ownerId)
                    && reminder.plantId().equals(id) && !reminder.completed());
            schedule(data, changed);
            return data;
        }, data -> findOwnedPlant(data, ownerId, id));
    }

    public void delete(String ownerId, String id) {
        store.update(data -> {
            findOwnedPlant(data, ownerId, id);
            data.plants.removeIf(item -> item.id().equals(id) && item.ownerId().equals(ownerId));
            data.reminders.removeIf(reminder -> reminder.ownerId().equals(ownerId) && reminder.plantId().equals(id));
            data.careHistory.removeIf(entry -> entry.ownerId().equals(ownerId) && entry.plantId().equals(id));
            return data;
        }, data -> null);
    }

    public Plant water(String ownerId, String id, String notes) {
        return store.update(data -> {
            Plant old = findOwnedPlant(data, ownerId, id);
            Instant now = Instant.now();
            Plant watered = new Plant(old.id(), old.ownerId(), old.name(), old.species(), old.location(),
                    old.wateringIntervalDays(), LocalDate.now(), old.imageUrl(), old.notes(), old.createdAt(), now);
            data.plants.replaceAll(item -> item.id().equals(id) && item.ownerId().equals(ownerId) ? watered : item);
            data.reminders.replaceAll(reminder -> reminder.ownerId().equals(ownerId)
                    && reminder.plantId().equals(id) && !reminder.completed()
                    ? new Reminder(reminder.id(), reminder.ownerId(), reminder.plantId(), reminder.type(),
                    reminder.dueDate(), true, now) : reminder);
            data.careHistory.add(new CareEntry(UUID.randomUUID().toString(), ownerId, id, "Watered", now, cleanNotes(notes)));
            schedule(data, watered);
            return data;
        }, data -> findOwnedPlant(data, ownerId, id));
    }

    public List<Reminder> reminders(String ownerId) {
        return store.snapshot().reminders.stream().filter(reminder -> reminder.ownerId().equals(ownerId))
                .sorted(Comparator.comparing(Reminder::dueDate)).toList();
    }

    public Reminder completeReminder(String ownerId, String reminderId) {
        return store.update(data -> {
            Reminder reminder = data.reminders.stream()
                    .filter(item -> item.id().equals(reminderId) && item.ownerId().equals(ownerId))
                    .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (reminder.completed()) {
                return data;
            }
            Plant old = findOwnedPlant(data, ownerId, reminder.plantId());
            Instant now = Instant.now();
            Plant watered = new Plant(old.id(), old.ownerId(), old.name(), old.species(), old.location(),
                    old.wateringIntervalDays(), LocalDate.now(), old.imageUrl(), old.notes(), old.createdAt(), now);
            data.plants.replaceAll(item -> item.id().equals(old.id()) && item.ownerId().equals(ownerId) ? watered : item);
            data.reminders.replaceAll(item -> item.ownerId().equals(ownerId) && item.plantId().equals(old.id())
                    && !item.completed() ? new Reminder(item.id(), item.ownerId(), item.plantId(), item.type(),
                    item.dueDate(), true, now) : item);
            data.careHistory.add(new CareEntry(UUID.randomUUID().toString(), ownerId, old.id(),
                    "Watered", now, "Completed watering reminder"));
            schedule(data, watered);
            return data;
        }, data -> data.reminders.stream()
                .filter(item -> item.id().equals(reminderId) && item.ownerId().equals(ownerId))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    public List<CareEntry> history(String ownerId) {
        return store.snapshot().careHistory.stream().filter(entry -> entry.ownerId().equals(ownerId))
                .sorted(Comparator.comparing(CareEntry::occurredAt).reversed()).toList();
    }

    public Dashboard dashboard(String ownerId, String fullName) {
        Data data = store.snapshot();
        List<Plant> plants = data.plants.stream().filter(plant -> plant.ownerId().equals(ownerId)).toList();
        LocalDate today = LocalDate.now();
        long due = plants.stream().filter(plant -> !plant.nextWateringDate().isAfter(today)).count();
        long onSchedule = plants.size() - due;
        List<Reminder> upcoming = data.reminders.stream()
                .filter(reminder -> reminder.ownerId().equals(ownerId) && !reminder.completed())
                .sorted(Comparator.comparing(Reminder::dueDate)).limit(5).toList();
        List<CareEntry> recent = data.careHistory.stream().filter(entry -> entry.ownerId().equals(ownerId))
                .sorted(Comparator.comparing(CareEntry::occurredAt).reversed()).limit(5).toList();
        return new Dashboard(fullName, plants.size(), due, onSchedule, plants, upcoming, recent);
    }

    public String answer(String ownerId, String question) {
        String prompt = question.toLowerCase(java.util.Locale.ROOT);
        List<Plant> plants = plants(ownerId);
        if (prompt.contains("reminder") || prompt.contains("due") || prompt.contains("water today")) {
            List<Plant> due = plants.stream().filter(plant -> !plant.nextWateringDate().isAfter(LocalDate.now())).toList();
            if (due.isEmpty()) {
                return "You have no plants due for watering today. Check the upcoming reminders for the next tasks.";
            }
            return "These plants need attention: " + due.stream().map(Plant::name)
                    .collect(java.util.stream.Collectors.joining(", ")) + ". Open Reminders to record care.";
        }
        if (prompt.contains("my plant") || prompt.contains("my collection") || prompt.contains("how many")) {
            return plants.isEmpty() ? "Your collection is empty. Use Add a plant to start tracking your care routine."
                    : "You are tracking " + plants.size() + " plant" + (plants.size() == 1 ? "" : "s") + ": "
                    + plants.stream().map(Plant::name).collect(java.util.stream.Collectors.joining(", ")) + ".";
        }
        if (prompt.contains("yellow") || prompt.contains("brown") || prompt.contains("droop") || prompt.contains("problem")) {
            return "Leaf changes can have several causes, including watering, light, pests, or normal aging. Check the soil and light first, inspect both sides of leaves, and change one care factor at a time. This guide is informational, not a diagnosis.";
        }
        if (prompt.contains("light") || prompt.contains("sun")) {
            return "Most common houseplants do well in bright, indirect light. Keep foliage away from harsh midday sun unless the species is known to tolerate it, and rotate the pot occasionally for even growth.";
        }
        if (prompt.contains("water") || prompt.contains("watering")) {
            return "Check the soil before watering rather than following a calendar blindly. Water thoroughly when the top layer is dry, and make sure the pot can drain. Your saved interval is a reminder, not a substitute for checking the plant.";
        }
        return "I’m a built-in plant-care guide. Ask about watering, light, your saved plants, or upcoming reminders. For persistent plant-health concerns, consult a local horticulture professional.";
    }

    private static Plant findOwnedPlant(Data data, String ownerId, String id) {
        return data.plants.stream().filter(plant -> plant.id().equals(id) && plant.ownerId().equals(ownerId))
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static void schedule(Data data, Plant plant) {
        data.reminders.add(new Reminder(UUID.randomUUID().toString(), plant.ownerId(), plant.id(),
                "Watering", plant.nextWateringDate(), false, null));
    }

    private static String normalizeImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return "/images/plant-leaves.jpg";
        }
        return imageUrl.trim();
    }

    private static String cleanNotes(String notes) {
        return notes == null ? "" : notes.trim();
    }

    public record PlantInput(String name, String species, String location, int wateringIntervalDays,
                            LocalDate lastWatered, String imageUrl, String notes) {
    }

    public record Dashboard(String fullName, int totalPlants, long plantsDue, long plantsOnSchedule,
                            List<Plant> plants, List<Reminder> upcomingReminders,
                            List<CareEntry> recentCare) {
    }
}
