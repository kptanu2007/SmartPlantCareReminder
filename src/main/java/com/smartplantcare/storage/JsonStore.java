package com.smartplantcare.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartplantcare.model.PlantData.CareEntry;
import com.smartplantcare.model.PlantData.Plant;
import com.smartplantcare.model.PlantData.Reminder;
import com.smartplantcare.model.PlantData.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

@Component
public class JsonStore {
    private final ObjectMapper mapper;
    private final Path file;
    private Data data;

    public JsonStore(ObjectMapper mapper, @Value("${plantcare.data-dir:}") String configuredDirectory) {
        this.mapper = mapper;
        this.file = resolveDirectory(configuredDirectory).resolve("store.json");
        load();
    }

    public synchronized Data snapshot() {
        return new Data(data);
    }

    public synchronized <T> T update(UnaryOperator<Data> change, java.util.function.Function<Data, T> result) {
        Data candidate = change.apply(new Data(data));
        persist(candidate);
        data = candidate;
        return result.apply(new Data(data));
    }

    private Path resolveDirectory(String configuredDirectory) {
        if (configuredDirectory != null && !configuredDirectory.isBlank()) {
            return Path.of(configuredDirectory).toAbsolutePath().normalize();
        }
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            return Path.of(localAppData, "SmartPlantCareReminder", "data");
        }
        return Path.of(System.getProperty("user.home"), ".smart-plant-care-reminder", "data");
    }

    private void load() {
        try {
            Files.createDirectories(file.getParent());
            if (Files.notExists(file)) {
                Data initial = new Data();
                persist(initial);
                data = initial;
                return;
            }
            data = mapper.readValue(file.toFile(), Data.class);
            data.validate();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not load plant-care data at " + file
                    + ". The existing file was preserved; check its permissions or restore a valid backup.", exception);
        }
    }

    private void persist(Data candidate) {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), candidate);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new StorageException("Could not save plant-care data at " + file
                    + ". The last saved data remains intact; check disk space and folder permissions.", exception);
        }
    }

    public static final class StorageException extends RuntimeException {
        public StorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static final class Data {
        public List<User> users;
        public List<Plant> plants;
        public List<Reminder> reminders;
        public List<CareEntry> careHistory;

        public Data() {
            users = new ArrayList<>();
            plants = new ArrayList<>();
            reminders = new ArrayList<>();
            careHistory = new ArrayList<>();
        }

        public Data(Data source) {
            users = new ArrayList<>(source.users);
            plants = new ArrayList<>(source.plants);
            reminders = new ArrayList<>(source.reminders);
            careHistory = new ArrayList<>(source.careHistory);
        }

        private void validate() {
            if (users == null || plants == null || reminders == null || careHistory == null) {
                throw new IllegalStateException("The JSON data file is missing a required collection.");
            }
        }
    }
}
