package com.worksphere.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class PostgreSqlConnectionIT {

  @Container
  private static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:17.7-alpine");

  @Test
  void establishesPostgreSqlConnection() throws Exception {
    try (Connection connection =
            java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("select current_database(), version()")) {
      assertThat(result.next()).isTrue();
      assertThat(result.getString(1)).isNotBlank();
      assertThat(result.getString(2)).contains("PostgreSQL");
    }
  }
}
