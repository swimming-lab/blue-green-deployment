package com.swimming.zookeeperclient.serviceDiscovery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Component
public class ServiceHandler {

    Logger logger = LoggerFactory.getLogger(ServiceHandler.class);

    private final String SCHEME = "http://";
    private final String SET_STATUS_API_API = "/eureka/setStatus";

    public int changeService(String instanceId, String instanceDomain, String instanceStatus) {
        RestTemplate restTemplate = new RestTemplate();
        MultiValueMap<String, String> data = new LinkedMultiValueMap<String, String>();
        data.add("instanceId", instanceId);
        data.add("status", instanceStatus);

        ResponseEntity<String> response = restTemplate.postForEntity(
                new StringBuilder(SCHEME).append(instanceDomain).append(SET_STATUS_API_API).toString(),
                data,
                String.class);

        logger.info("REST API RESPONSE [response.getStatusCode()={}]", response.getStatusCode().toString());

        if (!response.getStatusCode().is2xxSuccessful()) {
            return response.getStatusCode().value();
        }

        return HttpStatus.OK.value();
    }
}
