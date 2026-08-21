package com.example.vex360;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Vex360Application {

	public static void main(String[] args) {
		SpringApplication.run(Vex360Application.class, args);
	}

}
