package com.project.goTruck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GoTruckApplication {

	public static void main(String[] args) {
		SpringApplication.run(GoTruckApplication.class, args);
	}

}
