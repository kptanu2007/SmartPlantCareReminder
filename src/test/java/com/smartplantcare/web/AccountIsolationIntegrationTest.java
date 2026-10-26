package com.smartplantcare.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartplantcare.storage.JsonStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountIsolationIntegrationTest {
    @TempDir
    static Path dataDirectory;

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonStore store;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    ObjectMapper mapper;

    @DynamicPropertySource
    static void configureDataDirectory(DynamicPropertyRegistry registry) {
        registry.add("plantcare.data-dir", () -> dataDirectory.toString());
    }

    @BeforeEach
    void clearData() {
        store.update(data -> {
            data.users.clear();
            data.plants.clear();
            data.reminders.clear();
            data.careHistory.clear();
            return data;
        }, data -> null);
    }

    @Test
    void registrationHashesPasswordsAndTwoAccountsCannotReadEachOthersPlants() throws Exception {
        register("alex", "alex@example.com");
        register("sam", "sam@example.com");
        assertEquals(2, store.snapshot().users.size());
        assertNotEquals("StrongPass123!", store.snapshot().users.get(0).passwordHash());
        assertTrue(passwordEncoder.matches("StrongPass123!", store.snapshot().users.get(0).passwordHash()));

        MockHttpSession alexSession = login("alex", "alex@example.com");
        String plantBody = mapper.writeValueAsString(new PlantController.PlantRequest(
                "Monstera", "Monstera", "Living room", 7, LocalDate.now(), "", ""));
        MvcResult created = mvc.perform(post("/api/plants").session(alexSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(plantBody))
                .andExpect(status().isCreated()).andReturn();
        String plantId = mapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        MockHttpSession samSession = login("sam", "sam@example.com");
        mvc.perform(get("/api/plants").session(samSession))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/plants/" + plantId).session(samSession))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/plants").session(alexSession))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Monstera"));
    }

    @Test
    void privateApiRejectsUnauthenticatedRequests() throws Exception {
        mvc.perform(get("/api/plants")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidLoginIsRejectedAndLogoutEndsTheSession() throws Exception {
        register("jules", "jules@example.com");
        String wrongPassword = mapper.writeValueAsString(
                new AuthController.LoginRequest("jules", "not-the-password"));
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(wrongPassword))
                .andExpect(status().isUnauthorized());

        MockHttpSession session = login("jules", "jules@example.com");
        mvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/plants").session(session))
                .andExpect(status().isUnauthorized());
    }

    private void register(String username, String email) throws Exception {
        String body = mapper.writeValueAsString(new AuthController.RegisterRequest(
                username, email, username, "StrongPass123!", "StrongPass123!"));
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    private MockHttpSession login(String username, String email) throws Exception {
        String body = mapper.writeValueAsString(new AuthController.LoginRequest(username, "StrongPass123!"));
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
