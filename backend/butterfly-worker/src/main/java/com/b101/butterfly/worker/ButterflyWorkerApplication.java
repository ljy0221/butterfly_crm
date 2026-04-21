package com.b101.butterfly.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.b101.butterfly")
public class ButterflyWorkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ButterflyWorkerApplication.class, args);
	}

}
