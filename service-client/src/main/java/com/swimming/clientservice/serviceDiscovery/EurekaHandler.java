package com.swimming.clientservice.serviceDiscovery;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.shared.resolver.DefaultEndpoint;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.netflix.eureka.http.RestTemplateTransportClientFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

@Component
@RestController
public class EurekaHandler {

    Logger logger = LoggerFactory.getLogger(EurekaHandler.class);

    private final String SCHEME = "http://";
    private final String SET_STATUS_API_API = "/eureka/setStatus";

    @Autowired
    private EurekaClient eurekaClient;
    private EurekaHttpClient eurekaHttpClient;

    public EurekaHandler(
            @Value("${eureka.client.service-url.default-zone}")
            String serviceUrl) {
        this.eurekaHttpClient = new RestTemplateTransportClientFactory()
                .newClient(new DefaultEndpoint(serviceUrl));
    }

    @PostMapping(SET_STATUS_API_API)
    public ResponseEntity restApiSetStatus(
            @RequestParam(required = true) String instanceId,
            @RequestParam(required = true) String status) {

        logger.info("REST API {} [instanceId={}, status={}]", SET_STATUS_API_API, instanceId, status);

        if (!getInstanceId().equals(instanceId)) {
            logger.error("eureka status 변경 실패, 이유: instanceId 다름 > {} != {}", getInstanceId(), instanceId);
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }

        try {
            setStatus(InstanceInfo.InstanceStatus.toEnum(status));
        } catch (Exception e) {
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity(HttpStatus.OK);
    }

    public int setStatus(String instanceId, String instanceDomain, String instanceStatus) {
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

    private int setStatus(InstanceInfo.InstanceStatus instanceStatus) {
        InstanceInfo instanceInfo = eurekaClient.getApplicationInfoManager().getInfo();
        int responseStatusCode = eurekaHttpClient.statusUpdate(instanceInfo.getAppName(), instanceInfo.getInstanceId(), instanceStatus, instanceInfo).getStatusCode();
        if (responseStatusCode == 200) {
            logger.info("Eureka status 변경 성공. [timestamp={}, responseStatusCode={}, status={}]", System.currentTimeMillis(), responseStatusCode, instanceStatus.toString());
        } else {
            logger.error("Eureka status 변경 실패. [eurekaHttpResponse.getStatusCode()={}]", responseStatusCode);
        }

        return responseStatusCode;
    }

    public String getInstanceId() {
        return eurekaClient.getApplicationInfoManager().getInfo().getInstanceId();
    }
}
