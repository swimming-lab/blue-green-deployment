package com.swimming.clientservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
//@EnableAspectJAutoProxy
@RestController
public class ClientServiceApplication {

	@GetMapping("/")
	public String home(@Value("${server.color}") String color) {
		StringBuilder message = new StringBuilder()
				.append("현재 서비스중인 서버 Color : ")
				.append(color);
		System.out.println(message.toString());
		return message.toString();
	}

	public static void main(String[] args) {
		SpringApplication.run(ClientServiceApplication.class, args);
	}
}
