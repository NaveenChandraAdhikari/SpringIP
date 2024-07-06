package com.example.demo.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
public class WeatherController {

    private final WebClient webClient;
    private final String IP_API_URL = "http://ip-api.com/json/{ip}";
    private final String WEATHER_API_URL = "https://api.open-meteo.com/v1/forecast";

    public WeatherController(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @GetMapping("/api/hello")
    public Mono<Map<String, Object>> hello(@RequestParam("visitor_name") String visitorName, ServerWebExchange exchange) {
        String clientIp = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        System.out.println(clientIp);


        return webClient.get()
                .uri(IP_API_URL, clientIp)
                .retrieve()
                .bodyToMono(Map.class)
                .flatMap(locationData -> {
                    if (locationData == null) {
                        return Mono.error(new RuntimeException("Location data is null"));
                    }

                    String status = (String) locationData.get("status");
                    if (!"success".equals(status)) {
                        return Mono.error(new RuntimeException("Unable to fetch location data: " + locationData.get("message")));
                    }

                    String city = (String) locationData.get("city");
                    Double latitude = (Double) locationData.get("lat");
                    Double longitude = (Double) locationData.get("lon");

                    if (city == null || latitude == null || longitude == null) {
                        return Mono.error(new RuntimeException("Incomplete location data"));
                    }

                    return webClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .scheme("https")
                                    .host("api.open-meteo.com")
                                    .path("/v1/forecast")
                                    .queryParam("latitude", latitude)
                                    .queryParam("longitude", longitude)
                                    .queryParam("current_weather", true)
                                    .build())
                            .retrieve()
                            .bodyToMono(Map.class)
                            .map(weatherData -> {
                                Map<String, Object> currentWeather = (Map<String, Object>) weatherData.get("current_weather");
                                if (currentWeather == null) {
                                    throw new RuntimeException("Unable to fetch weather data");
                                }

                                Double temperature = (Double) currentWeather.get("temperature");

                                Map<String, Object> response = new HashMap<>();
                                response.put("client_ip", clientIp);
                                response.put("location", city);
                                response.put("greeting", String.format("Hello, %s! The temperature is %.1f degrees Celsius in %s", visitorName, temperature, city));

                                return response;
                            });
                })
                .onErrorResume(e -> {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", e.getMessage());
                    return Mono.just(errorResponse);
                });
    }
}
