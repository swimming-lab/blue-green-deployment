package com.swimming.clientservice;

import com.swimming.clientservice.serviceDiscovery.ZookeeperHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class ClientServiceApplication {

	Logger logger = LoggerFactory.getLogger(ClientServiceApplication.class);

	@GetMapping("/")
	public String home(@Value("${server.runtime-color}") String color) {
		String message = "현재 서비스중인 Color 정보 : runtime-color=" + color;
		logger.info(message);
		return message;
	}

	public static void main(String[] args) {
		SpringApplication.run(ClientServiceApplication.class, args);
	}
}
