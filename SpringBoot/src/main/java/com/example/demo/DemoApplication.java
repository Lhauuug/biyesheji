package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.transaction.annotation.Transactional;

@EnableCaching
@SpringBootApplication
@Transactional
@org.springframework.scheduling.annotation.EnableScheduling
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);

		System.out.println("\n");
		System.out.println("SpringBoot is starting......");

	}
}
