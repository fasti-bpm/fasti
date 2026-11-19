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
package org.camunda.bpm.spring.boot.starter.security.oauth2.impl;

import java.util.Objects;

import org.springframework.security.core.GrantedAuthority;

/**
 * A tenant membership taken from the claims of a user. It is kept apart from
 * the group authorities so that a tenant id never counts as a group id.
 */
public final class TenantGrantedAuthority implements GrantedAuthority {

  private static final long serialVersionUID = 1L;

  public static final String PREFIX = "TENANT_";

  private final String tenantId;

  public TenantGrantedAuthority(String tenantId) {
    this.tenantId = Objects.requireNonNull(tenantId);
  }

  public String getTenantId() {
    return tenantId;
  }

  @Override
  public String getAuthority() {
    return PREFIX + tenantId;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof TenantGrantedAuthority that && tenantId.equals(that.tenantId);
  }

  @Override
  public int hashCode() {
    return tenantId.hashCode();
  }

  @Override
  public String toString() {
    return getAuthority();
  }
}
