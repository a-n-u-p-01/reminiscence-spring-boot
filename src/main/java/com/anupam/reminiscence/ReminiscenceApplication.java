package com.anupam.reminiscence;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReminiscenceApplication {
	public static void main(String[] args) {
		SpringApplication.run(ReminiscenceApplication.class, args);
	}
}