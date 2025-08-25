package com.example.amadeusapiclient;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AmadeusApiClientService {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final AmadeusApiProperties properties;

    private volatile String accessToken;
    private volatile Instant tokenExpiry;

    public AmadeusApiClientService(AmadeusApiProperties properties) {
        this.properties = properties;
    }

    private static String url(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
    private static String buildQuery(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> url(e.getKey()) + "=" + url(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private synchronized void authenticate() throws IOException, InterruptedException {
        if (accessToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
            return;
        }
        String form = "grant_type=client_credentials"
                + "&client_id=" + url(properties.getClientId())
                + "&client_secret=" + url(properties.getClientSecret());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getTokenUrl()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to authenticate with Amadeus API: " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        this.accessToken = json.get("access_token").getAsString();
        int expiresIn = json.get("expires_in").getAsInt();
        this.tokenExpiry = Instant.now().plusSeconds(Math.max(30, expiresIn - 60));
        log.info("Amadeus token OK. Expira en {}", tokenExpiry);
    }

    private String getValidAccessToken() throws IOException, InterruptedException {
        if (accessToken == null || tokenExpiry == null || Instant.now().isAfter(tokenExpiry)) {
            authenticate();
        }
        return accessToken;
    }

    public String searchFlights(String origin, String destination, String departureDate, int adults) {
        try {
            String token = getValidAccessToken();

            Map<String, String> params = Map.of(
                    "originLocationCode", origin,
                    "destinationLocationCode", destination,
                    "departureDate", departureDate,
                    "adults", String.valueOf(adults),
                    "max", "5"
            );
            String url = properties.getFlightSearchUrl() + "?" + buildQuery(params);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + token)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("Flight search failed ({}): {}", response.statusCode(), response.body());
                return response.body();
            }
            return response.body();

        } catch (IOException | InterruptedException e) {
            log.error("Flight search error: {}", e.toString());
            Thread.currentThread().interrupt();
            return "{\"error\":\"amadeus_request_failed\",\"message\":\"" + e.getMessage() + "\"}";
        } catch (RuntimeException e) {
            log.error("Runtime error: {}", e.toString());
            return "{\"error\":\"amadeus_runtime_error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }
}
