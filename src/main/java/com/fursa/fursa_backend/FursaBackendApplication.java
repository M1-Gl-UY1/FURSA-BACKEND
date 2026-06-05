package com.fursa.fursa_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
// V2 G.6 (05/06/2026) : envoi d'emails en arriere-plan sans bloquer la requete.
@EnableAsync
public class FursaBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(FursaBackendApplication.class, args);
	}

}
