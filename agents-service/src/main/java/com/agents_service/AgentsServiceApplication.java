package com.agents_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling 
@SpringBootApplication	
public class AgentsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AgentsServiceApplication.class, args);
	}

}
