package com.worksphere;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WorkSphereApplicationTest {

  @Autowired private ApplicationContext applicationContext;

  @Test
  void applicationContextStarts() {
    assertThat(applicationContext).isNotNull();
  }
}
