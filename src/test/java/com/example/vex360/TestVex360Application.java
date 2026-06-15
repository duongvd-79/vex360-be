package com.example.vex360;

import org.springframework.boot.SpringApplication;

public class TestVex360Application {

	public static void main(String[] args) {
		SpringApplication.from(Vex360Application::main).with(TestcontainersConfiguration.class).run(args);
	}

}
