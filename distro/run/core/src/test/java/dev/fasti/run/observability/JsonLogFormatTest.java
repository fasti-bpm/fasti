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
package dev.fasti.run.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.OutputCaptureRule;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/**
 * The log lines of an application started with {@code fasti.logging.format}; only the
 * environment post processor of Fasti and the logging of Spring Boot are involved.
 */
public class JsonLogFormatTest {

  @Rule
  public OutputCaptureRule output = new OutputCaptureRule();

  @Test
  public void shouldWriteOneJsonObjectPerLine() {
    String marker = "json-log-marker-" + System.nanoTime();
    try (ConfigurableApplicationContext context = start("--fasti.logging.format=json")) {
      LoggerFactory.getLogger("dev.fasti.test").warn("{} with \"quotes\"", marker);
    }

    DocumentContext line = line(marker);
    assertThat(line.read("$.message", String.class)).isEqualTo(marker + " with \"quotes\"");
    assertThat(line.read("$.log.level", String.class)).isEqualTo("WARN");
    assertThat(line.read("$.log.logger", String.class)).isEqualTo("dev.fasti.test");
    assertThat(line.read("$.service.name", String.class)).isEqualTo("fasti-bpm");
    assertThat(line.read("$['@timestamp']", String.class)).isNotBlank();
    assertThat(line.read("$.ecs.version", String.class)).isNotBlank();
  }

  @Test
  public void shouldWriteLogstashFormat() {
    String marker = "logstash-log-marker-" + System.nanoTime();
    try (ConfigurableApplicationContext context = start("--fasti.logging.format=logstash")) {
      LoggerFactory.getLogger("dev.fasti.test").info(marker);
    }

    DocumentContext line = line(marker);
    assertThat(line.read("$.message", String.class)).isEqualTo(marker);
    assertThat(line.read("$.level", String.class)).isEqualTo("INFO");
    assertThat(line.read("$['@version']", String.class)).isEqualTo("1");
  }

  @Test
  public void shouldKeepTextByDefault() {
    String marker = "text-log-marker-" + System.nanoTime();
    try (ConfigurableApplicationContext context = start()) {
      LoggerFactory.getLogger("dev.fasti.test").info(marker);
    }

    String line = output.getOut().lines().filter(l -> l.contains(marker)).findFirst().orElseThrow();
    assertThat(line).doesNotStartWith("{").contains("INFO");
  }

  protected ConfigurableApplicationContext start(String... args) {
    return new SpringApplicationBuilder(Empty.class)
        .web(WebApplicationType.NONE)
        .properties("spring.main.banner-mode=off")
        .run(args);
  }

  protected DocumentContext line(String marker) {
    String line = output.getOut().lines().filter(l -> l.contains(marker)).findFirst().orElseThrow(
        () -> new AssertionError("no log line with " + marker + " in:\n" + output.getOut()));
    assertThat(line).startsWith("{").endsWith("}");
    return JsonPath.parse(line);
  }

  @Configuration(proxyBeanMethods = false)
  static class Empty {
  }
}
