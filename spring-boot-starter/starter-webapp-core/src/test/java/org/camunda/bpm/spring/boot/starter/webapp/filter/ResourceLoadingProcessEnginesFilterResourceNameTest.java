/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.webapp.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.spring.boot.starter.property.WebappProperty;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

/**
 * The index pages are read from the web jar on the class path; a name that could leave the folder
 * of the web jar is never handed to the resource loader.
 */
public class ResourceLoadingProcessEnginesFilterResourceNameTest {

  protected ResourceLoadingProcessEnginesFilter filter;
  protected List<String> loaded;

  @Before
  public void setUp() {
    loaded = new ArrayList<>();
    filter = new ResourceLoadingProcessEnginesFilter();
    filter.setWebappProperty(new WebappProperty());
    filter.setResourceLoader(new DefaultResourceLoader() {
      @Override
      public Resource getResource(String location) {
        loaded.add(location);
        return new ByteArrayResource("<html></html>".getBytes(StandardCharsets.UTF_8));
      }
    });
  }

  @Test
  public void shouldReadIndexPageFromWebjar() throws Exception {
    assertThat(filter.getWebResourceContents("/app/cockpit/index.html")).isEqualTo("<html></html>\n");
    assertThat(loaded).containsExactly("classpath:/META-INF/resources/webjars/camunda/app/cockpit/index.html");
  }

  @Test
  public void shouldNotReadResourceOutsideOfWebjar() {
    String[] names = {
      "/app/../../../../application.yaml",
      "/app/..\\..\\application.yaml",
      "/app/%2e%2e/%2e%2e/application.yaml",
      null
    };

    for (String name : names) {
      assertThatThrownBy(() -> filter.getWebResourceContents(name)).as(name)
        .isInstanceOf(FileNotFoundException.class);
    }
    assertThat(loaded).isEmpty();
  }
}
