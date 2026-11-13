package com.urlshortener.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full-stack tests (real service + repository + H2) driving the two
 * endpoints the exercise asks for: POST /shorten and GET /{code}.
 * Each test runs in a transaction that's rolled back afterward, so tests
 * don't see each other's rows despite sharing one schema for the class.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UrlShortenerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shortenThenRedirect_roundTripsToOriginalUrl() throws Exception {
        String longUrl = "https://developer.mozilla.org/en-US/docs/Web/HTTP";

        String responseJson = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + longUrl + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").isNotEmpty())
                .andExpect(jsonPath("$.longUrl").value(longUrl))
                .andReturn().getResponse().getContentAsString();

        String shortCode = com.jayway.jsonpath.JsonPath.read(responseJson, "$.shortCode");

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", longUrl));
    }

    @Test
    void redirect_unknownCode_returns404() throws Exception {
        mockMvc.perform(get("/this-code-does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void shorten_sameUrlTwice_isIdempotentAndReturnsSameCode() throws Exception {
        String longUrl = "https://example.com/dedup-check";
        String body = "{\"url\":\"" + longUrl + "\"}";

        String firstResponse = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String firstCode = com.jayway.jsonpath.JsonPath.read(firstResponse, "$.shortCode");

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value(firstCode));
    }

    @Test
    void shorten_withCustomAlias_usesAliasAsShortCode() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/docs\",\"alias\":\"my-docs-alias\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value("my-docs-alias"));
    }

    @Test
    void shorten_duplicateCustomAlias_returns409() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/a\",\"alias\":\"taken-alias\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/b\",\"alias\":\"taken-alias\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void shorten_invalidUrl_returns400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"not-a-url\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void shorten_blankUrl_returns400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shorten_reservedAlias_returns400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/x\",\"alias\":\"actuator\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shorten_malformedAliasCharacters_returns400() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/x\",\"alias\":\"has space\"}"))
                .andExpect(status().isBadRequest());
    }
}
