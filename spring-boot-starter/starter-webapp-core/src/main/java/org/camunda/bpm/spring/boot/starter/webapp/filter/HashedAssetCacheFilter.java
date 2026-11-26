/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.spring.boot.starter.webapp.filter;

import org.camunda.bpm.webapp.impl.filter.StaticAssetCacheFilter;

/**
 * The cache headers of the static files of the web applications under Spring Boot; see
 * {@link StaticAssetCacheFilter}, which the WAR of the web applications registers itself.
 */
public class HashedAssetCacheFilter extends StaticAssetCacheFilter {
}
