/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.client.variable.impl.format.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.w3c.dom.Element;

/**
 * XML variables reach the worker from the engine, that is from whoever may write a variable
 * there; parsing them must not resolve document type declarations or external entities.
 */
public class DomXmlDataFormatXxeTest {

  @Rule
  public TemporaryFolder folder = new TemporaryFolder();

  @Test
  public void shouldParsePlainXml() {
    Element element = new DomXmlDataFormat("application/xml").readAsElement("<order><id>42</id></order>");

    assertThat(element.getTagName()).isEqualTo("order");
    assertThat(element.getTextContent()).isEqualTo("42");
  }

  @Test
  public void shouldRefuseExternalEntities() throws Exception {
    File secret = folder.newFile("secret.txt");
    Files.writeString(secret.toPath(), "do-not-read");
    String xml = "<?xml version=\"1.0\"?>"
        + "<!DOCTYPE order [<!ENTITY secret SYSTEM \"" + secret.toURI() + "\">]>"
        + "<order>&secret;</order>";

    assertThatThrownBy(() -> new DomXmlDataFormat("application/xml").readAsElement(xml))
        .hasStackTraceContaining("DOCTYPE");
  }

  @Test
  public void shouldRefuseInternalEntityExpansion() {
    String xml = "<?xml version=\"1.0\"?>"
        + "<!DOCTYPE order [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">]>"
        + "<order>&b;</order>";

    assertThatThrownBy(() -> new DomXmlDataFormat("application/xml").readAsElement(xml))
        .hasStackTraceContaining("DOCTYPE");
  }
}
