package com.smartbus.schedule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class ScheduleServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ScheduleServiceApplication.class, args);
  }
}
