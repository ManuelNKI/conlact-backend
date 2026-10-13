package com.conlact.conlact_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;




@SpringBootApplication
@EnableScheduling
public class ConlactBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConlactBackendApplication.class, args);
	}

}
