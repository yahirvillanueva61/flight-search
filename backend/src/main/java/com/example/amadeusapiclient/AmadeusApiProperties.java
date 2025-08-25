package com.example.amadeusapiclient;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "amadeus.api")
@Getter @Setter
public class AmadeusApiProperties {
    private String clientId;
    private String clientSecret;
    private String tokenUrl;
    private String flightSearchUrl;
}
