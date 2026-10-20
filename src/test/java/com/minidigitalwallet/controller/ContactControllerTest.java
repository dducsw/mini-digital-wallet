package com.minidigitalwallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minidigitalwallet.dto.request.CreateContactRequest;
import com.minidigitalwallet.dto.response.ContactResponse;
import com.minidigitalwallet.entity.enums.UserRole;
import com.minidigitalwallet.exception.GlobalExceptionHandler;
import com.minidigitalwallet.security.CurrentUser;
import com.minidigitalwallet.security.UserPrincipal;
import com.minidigitalwallet.service.ContactService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
    controllers = ContactController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class},
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = com.minidigitalwallet.security.SecurityConfig.class)
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ContactControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContactService contactService;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        UserPrincipal principal = UserPrincipal.create(userId, "alice", UserRole.USER);
        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /api/v1/contacts returns 200 OK with contacts list")
    void getContacts_ReturnsList() throws Exception {
        ContactResponse contact = new ContactResponse(UUID.randomUUID(), "123456789012", "Bob", false, Instant.now());
        when(contactService.getContacts(userId.toString())).thenReturn(List.of(contact));

        mockMvc.perform(get("/api/v1/contacts"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].nickname").value("Bob"))
            .andExpect(jsonPath("$[0].account_number").value("123456789012"));
    }

    @Test
    @DisplayName("POST /api/v1/contacts returns 201 Created on valid request")
    void createContact_Success() throws Exception {
        CreateContactRequest request = new CreateContactRequest("1234 5678 9012", "Bob");
        ContactResponse response = new ContactResponse(UUID.randomUUID(), "123456789012", "Bob", false, Instant.now());

        when(contactService.createContact(eq(userId.toString()), any(CreateContactRequest.class)))
            .thenReturn(response);

        mockMvc.perform(post("/api/v1/contacts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nickname").value("Bob"));
    }

    @Test
    @DisplayName("DELETE /api/v1/contacts/{id} returns 204 No Content")
    void deleteContact_Success() throws Exception {
        UUID contactId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/contacts/" + contactId))
            .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("PATCH /api/v1/contacts/{id}/favorite returns 200 OK")
    void toggleFavorite_Success() throws Exception {
        UUID contactId = UUID.randomUUID();
        ContactResponse response = new ContactResponse(contactId, "123456789012", "Bob", true, Instant.now());

        when(contactService.toggleFavorite(userId.toString(), contactId)).thenReturn(response);

        mockMvc.perform(patch("/api/v1/contacts/" + contactId + "/favorite"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.is_favorite").value(true));
    }
}
