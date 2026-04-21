package com.b101.butterfly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.b101.butterfly")
public class ButterflyApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ButterflyApiApplication.class, args);
	}

}
