package com.testtask.authapi.service;

import com.testtask.authapi.dto.ProcessRequest;
import com.testtask.authapi.dto.ProcessResponse;
import com.testtask.authapi.entity.ProcessingLog;
import com.testtask.authapi.repository.ProcessingLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Service
public class ProcessService {

    private final ProcessingLogRepository processingLogRepository;
    private final RestTemplate restTemplate;
    private final String dataApiUrl;
    private final String internalToken;

    public ProcessService(ProcessingLogRepository processingLogRepository,
                          @Value("${app.data-api-url}") String dataApiUrl,
                          @Value("${app.internal-token}") String internalToken) {
        this.processingLogRepository = processingLogRepository;
        this.restTemplate = new RestTemplate();
        this.dataApiUrl = dataApiUrl;
        this.internalToken = internalToken;
    }

    public ProcessResponse process(ProcessRequest request, UUID userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Token", internalToken);
        headers.set(HttpHeaders.CONTENT_TYPE, "application/json");

        HttpEntity<ProcessRequest> entity = new HttpEntity<>(request, headers);
        ProcessResponse response = restTemplate.postForObject(
                dataApiUrl + "/api/transform", entity, ProcessResponse.class);

        ProcessingLog log = new ProcessingLog();
        log.setUserId(userId);
        log.setInputText(request.text());
        log.setOutputText(response != null ? response.result() : "");
        processingLogRepository.save(log);

        return response;
    }
}
