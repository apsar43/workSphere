package com.worksphere;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    excludeName =
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration")
public class WorkSphereApplication {

  public static void main(String[] args) {
    SpringApplication.run(WorkSphereApplication.class, args);
  }
}
