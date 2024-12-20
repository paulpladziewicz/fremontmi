package com.paulpladziewicz.fremontmi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class FremontMIApplication {

    public static void main(String[] args) {
        SpringApplication.run(FremontMIApplication.class, args);
    }

}
