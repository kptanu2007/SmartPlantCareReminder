package com.smartplantcare.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

public final class PlantData {
    private PlantData() {
    }

    public record User(String id, String fullName, String email, String username,
                       String passwordHash, Instant createdAt) {
    }

    public record Plant(String id, String ownerId, String name, String species, String location,
                        int wateringIntervalDays, LocalDate lastWatered, String imageUrl,
                        String notes, Instant createdAt, Instant updatedAt) {
        public LocalDate nextWateringDate() {
            return lastWatered.plusDays(wateringIntervalDays);
        }

        @JsonProperty(access = JsonProperty.Access.READ_ONLY)
        public LocalDate getNextWateringDate() {
            return nextWateringDate();
        }
    }

    public record Reminder(String id, String ownerId, String plantId, String type,
                           LocalDate dueDate, boolean completed, Instant completedAt) {
    }

    public record CareEntry(String id, String ownerId, String plantId, String action,
                            Instant occurredAt, String notes) {
    }
}
