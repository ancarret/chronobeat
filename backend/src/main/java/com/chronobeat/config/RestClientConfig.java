package com.chronobeat.config;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient appleMusicRestClient(AppleMusicProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()));

        // The iTunes Search API serves its JSON body as "text/javascript" (a legacy
        // JSONP-era default) instead of "application/json", so the JSON converter
        // must be taught to accept it too.
        JacksonJsonHttpMessageConverter jsonConverter = new JacksonJsonHttpMessageConverter();
        List<MediaType> supported = new ArrayList<>(jsonConverter.getSupportedMediaTypes());
        supported.add(new MediaType("text", "javascript"));
        jsonConverter.setSupportedMediaTypes(supported);

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .configureMessageConverters(builder -> builder.withJsonConverter(jsonConverter))
                .build();
    }
}
