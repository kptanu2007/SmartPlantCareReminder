package com.smartplantcare.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartplantcare.model.PlantData.Plant;
import com.smartplantcare.model.PlantData.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JsonStoreTest {
    @TempDir
    Path directory;

    private ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void createsAndReloadsTheJsonSnapshot() {
        JsonStore store = new JsonStore(mapper(), directory.toString());
        User user = new User(UUID.randomUUID().toString(), "Avery Green", "avery@example.com",
                "avery", "$2a$12$hashed", Instant.parse("2025-01-01T00:00:00Z"));

        store.update(data -> {
            data.users.add(user);
            return data;
        }, data -> null);

        JsonStore reloaded = new JsonStore(mapper(), directory.toString());
        assertEquals(user, reloaded.snapshot().users.get(0));
        assertTrue(Files.exists(directory.resolve("store.json")));
    }

    @Test
    void computedWateringDateDoesNotBreakPlantRoundTrips() {
        JsonStore store = new JsonStore(mapper(), directory.toString());
        Plant plant = new Plant("plant-id", "owner-id", "Fern", "Fern", "Study", 5,
                java.time.LocalDate.of(2025, 1, 1), "", "", Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T00:00:00Z"));
        store.update(data -> {
            data.plants.add(plant);
            return data;
        }, data -> null);

        assertEquals(plant, new JsonStore(mapper(), directory.toString()).snapshot().plants.get(0));
    }

    @Test
    void malformedDataIsReportedAndNeverReplacedWithEmptyData() throws Exception {
        Path dataFile = directory.resolve("store.json");
        Files.createDirectories(directory);
        Files.writeString(dataFile, "{ this is not valid JSON");

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new JsonStore(mapper(), directory.toString()));

        assertTrue(exception.getMessage().contains("existing file was preserved"));
        assertEquals("{ this is not valid JSON", Files.readString(dataFile));
    }

    @Test
    void failedTransactionDoesNotChangeThePersistedSnapshot() {
        JsonStore store = new JsonStore(mapper(), directory.toString());
        assertThrows(IllegalArgumentException.class, () -> store.update(data -> {
            data.users.add(new User("id", "Name", "person@example.com", "person", "hash", Instant.now()));
            throw new IllegalArgumentException("stop before commit");
        }, data -> null));

        assertTrue(store.snapshot().users.isEmpty());
        assertTrue(new JsonStore(mapper(), directory.toString()).snapshot().users.isEmpty());
    }

    @Test
    void failedFileWriteLeavesTheLastSavedSnapshotUntouched() throws Exception {
        JsonStore store = new JsonStore(mapper(), directory.toString());
        Path dataFile = directory.resolve("store.json");
        byte[] original = Files.readAllBytes(dataFile);
        Files.createDirectory(directory.resolve("store.json.tmp"));

        assertThrows(JsonStore.StorageException.class, () -> store.update(data -> {
            data.users.add(new User("id", "Name", "person@example.com", "person", "hash", Instant.now()));
            return data;
        }, data -> null));

        assertArrayEquals(original, Files.readAllBytes(dataFile));
        assertTrue(store.snapshot().users.isEmpty());
    }
}
