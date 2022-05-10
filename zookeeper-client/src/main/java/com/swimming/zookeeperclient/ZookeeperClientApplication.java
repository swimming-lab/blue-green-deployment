package com.swimming.zookeeperclient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
public class ZookeeperClientApplication {

	@GetMapping("/")
	public String home(@Value("${server.color}") String color) {
		StringBuilder message = new StringBuilder()
				.append("Hello Project !! Service color : ")
				.append(color);
		System.out.println(message.toString());
		return message.toString();
	}

	public static void main(String[] args) {
		SpringApplication.run(ZookeeperClientApplication.class, args);
	}
}
