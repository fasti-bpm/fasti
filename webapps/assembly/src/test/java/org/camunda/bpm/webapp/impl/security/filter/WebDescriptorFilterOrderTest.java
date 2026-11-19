/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.security.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Filters are invoked in the order of their mappings in the deployment
 * descriptor. The filters that reject requests (authentication, security,
 * CSRF prevention) answer on their own; the security headers must be set
 * before, otherwise they are missing on exactly these error responses.
 */
public class WebDescriptorFilterOrderTest {

  public static final String WEB_DESCRIPTOR_FILE = "src/main/webapp/WEB-INF/web.xml";

  protected static final String HEADER_SECURITY_FILTER = "HttpHeaderSecurity";

  protected List<String> filterMappings;

  @Before
  public void readFilterMappings() throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Document descriptor = factory.newDocumentBuilder().parse(new File(WEB_DESCRIPTOR_FILE));

    filterMappings = new ArrayList<>();
    NodeList mappings = descriptor.getElementsByTagNameNS("*", "filter-mapping");
    for (int i = 0; i < mappings.getLength(); i++) {
      Element mapping = (Element) mappings.item(i);
      filterMappings.add(mapping.getElementsByTagNameNS("*", "filter-name").item(0).getTextContent().trim());
    }
  }

  @Test
  public void shouldSetSecurityHeadersBeforeRequestsCanBeRejected() {
    assertThat(filterMappings).contains(HEADER_SECURITY_FILTER, "Authentication Filter", "SecurityFilter", "CsrfPreventionFilter");

    assertThat(filterMappings.indexOf(HEADER_SECURITY_FILTER))
      .isLessThan(filterMappings.indexOf("Authentication Filter"))
      .isLessThan(filterMappings.indexOf("SecurityFilter"))
      .isLessThan(filterMappings.indexOf("CsrfPreventionFilter"));
  }

}
