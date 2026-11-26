/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.fasti.run.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import org.junit.Test;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.FileSystemResource;

/**
 * The HTTP server, session cookie and connection pool settings of the configuration files that
 * Run and the container image ship (docs/tuning-runtime.md), read as Spring Boot reads them.
 */
public class ShippedServerDefaultsTest {

  static final String[] SHIPPED = {
      "../assembly/resources/default.yml",
      "../assembly/resources/production.yml",
      "../../docker/fasti-docker.yml"
  };

  static final String SESSION_COOKIE_NAME = "server.servlet.session.cookie.name";
  static final String SAME_SITE_FILTER_COOKIE_NAME = "camunda.bpm.webapp.session-cookie.cookie-name";

  @Test
  public void shouldNameTheSessionCookieForFasti() throws IOException {
    for (String file : SHIPPED) {
      StandardEnvironment environment = environment(file, Map.of());

      assertThat(environment.getProperty(SESSION_COOKIE_NAME)).as(file).isEqualTo("FASTI_SESSION");
      // the filter that adds SameSite looks for the cookie by name
      assertThat(environment.getProperty(SAME_SITE_FILTER_COOKIE_NAME)).as(file).isEqualTo("FASTI_SESSION");
    }
  }

  @Test
  public void shouldFollowAnotherSessionCookieName() throws IOException {
    for (String file : SHIPPED) {
      StandardEnvironment environment = environment(file, Map.of("SERVER_SERVLET_SESSION_COOKIE_NAME", "OTHER_SESSION"));

      assertThat(environment.getProperty(SESSION_COOKIE_NAME)).as(file).isEqualTo("OTHER_SESSION");
      assertThat(environment.getProperty(SAME_SITE_FILTER_COOKIE_NAME)).as(file).isEqualTo("OTHER_SESSION");
    }
  }

  @Test
  public void shouldCompressTextResponses() throws IOException {
    for (String file : SHIPPED) {
      StandardEnvironment environment = environment(file, Map.of());

      assertThat(environment.getProperty("server.compression.enabled", Boolean.class)).as(file).isTrue();
      assertThat(environment.getProperty("server.compression.min-response-size")).as(file).isEqualTo("2KB");
      assertThat(environment.getProperty("server.compression.mime-types", String[].class)).as(file)
          .contains("text/html", "text/css", "text/javascript", "application/javascript", "image/svg+xml",
              "application/json", "application/hal+json", "application/xml", "text/xml");
    }
  }

  @Test
  public void shouldBoundRequestThreadsAndTheWaitForAConnection() throws IOException {
    for (String file : SHIPPED) {
      StandardEnvironment environment = environment(file, Map.of());

      assertThat(environment.getProperty("server.tomcat.threads.max", Integer.class)).as(file).isEqualTo(100);
      assertThat(environment.getProperty("spring.datasource.hikari.connection-timeout", Long.class)).as(file)
          .isEqualTo(10_000L);
    }
  }

  /** The file below environment variables, as in Run and the image. */
  protected static StandardEnvironment environment(String file, Map<String, Object> variables) throws IOException {
    Path path = Path.of(file);
    assertThat(path).as("shipped configuration file").exists();
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
    environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    environment.getPropertySources().addLast(new SystemEnvironmentPropertySource("variables", variables));
    for (PropertySource<?> source : new YamlPropertySourceLoader().load(file, new FileSystemResource(path))) {
      environment.getPropertySources().addLast(source);
    }
    ConfigurationPropertySources.attach(environment);
    return environment;
  }
}
