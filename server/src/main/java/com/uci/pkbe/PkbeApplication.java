package com.uci.pkbe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PkbeApplication {

    public static void main(String[] args) {
        SpringApplication.run(PkbeApplication.class, args);
    }
}
