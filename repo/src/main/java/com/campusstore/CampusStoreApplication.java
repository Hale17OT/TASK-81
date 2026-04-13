package com.campusstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CampusStoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusStoreApplication.class, args);
    }
}
