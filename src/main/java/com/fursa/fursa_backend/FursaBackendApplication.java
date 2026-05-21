package com.fursa.fursa_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FursaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(FursaBackendApplication.class, args);
	}

}
