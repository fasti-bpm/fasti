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

  /** the descriptors of the Tomcat and WildFly distributions, which ship descriptors of their own */
  public static final List<String> DISTRIBUTION_DESCRIPTOR_FILES = List.of(
      "../../distro/tomcat/webapp-jakarta/src/main/webapp/WEB-INF/web.xml",
      "../../distro/wildfly/webapp/src/main/webapp/WEB-INF/web.xml");

  protected static final String STATIC_ASSET_CACHE_FILTER = "StaticAssetCacheFilter";

  protected List<String> filterMappings;

  @Before
  public void readFilterMappings() throws Exception {
    filterMappings = new ArrayList<>();
    for (Element mapping : filterMappings(WEB_DESCRIPTOR_FILE)) {
      filterMappings.add(filterName(mapping));
    }
  }

  protected static List<Element> filterMappings(String file) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Document descriptor = factory.newDocumentBuilder().parse(new File(file));

    List<Element> result = new ArrayList<>();
    NodeList mappings = descriptor.getElementsByTagNameNS("*", "filter-mapping");
    for (int i = 0; i < mappings.getLength(); i++) {
      result.add((Element) mappings.item(i));
    }
    return result;
  }

  protected static String filterName(Element mapping) {
    return mapping.getElementsByTagNameNS("*", "filter-name").item(0).getTextContent().trim();
  }

  @Test
  public void shouldSetCacheHeadersOfStaticFilesBeforeTheIndexPageIsWritten() throws Exception {
    List<String> files = new ArrayList<>(DISTRIBUTION_DESCRIPTOR_FILES);
    files.add(WEB_DESCRIPTOR_FILE);
    for (String file : files) {
      List<String> names = new ArrayList<>();
      List<String> patterns = new ArrayList<>();
      for (Element mapping : filterMappings(file)) {
        names.add(filterName(mapping));
        if (STATIC_ASSET_CACHE_FILTER.equals(filterName(mapping))) {
          NodeList urlPatterns = mapping.getElementsByTagNameNS("*", "url-pattern");
          for (int i = 0; i < urlPatterns.getLength(); i++) {
            patterns.add(urlPatterns.item(i).getTextContent().trim());
          }
        }
      }
      assertThat(names).as(file).contains(STATIC_ASSET_CACHE_FILTER, "Engines Filter");
      assertThat(names.indexOf(STATIC_ASSET_CACHE_FILTER)).as(file).isLessThan(names.indexOf("Engines Filter"));
      assertThat(patterns).as(file).containsExactlyInAnyOrder("/app/*", "/lib/*", "/assets/*");
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
