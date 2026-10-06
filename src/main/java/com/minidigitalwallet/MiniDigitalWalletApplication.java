package com.minidigitalwallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class MiniDigitalWalletApplication {

	public static void main(String[] args) {
		SpringApplication.run(MiniDigitalWalletApplication.class, args);
	}

}
