package com.cascadeAI.Orchestrator.service;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

@Service
@AllArgsConstructor
public class ParameterStoreService {

    private final SsmClient ssmClient;

    public String getParameterValue(String parameterName) {
        GetParameterRequest request = GetParameterRequest.builder()
                .name(parameterName)
                .withDecryption(true)
                .build();

        return ssmClient.getParameter(request).parameter().value();
    }
}
