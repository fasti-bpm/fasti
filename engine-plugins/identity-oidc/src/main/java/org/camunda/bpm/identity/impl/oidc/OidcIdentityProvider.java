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
package org.camunda.bpm.identity.impl.oidc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.GroupQuery;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.TenantQuery;
import org.camunda.bpm.engine.identity.User;
import org.camunda.bpm.engine.identity.UserQuery;
import org.camunda.bpm.engine.impl.Page;
import org.camunda.bpm.engine.impl.UserQueryImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.identity.Authentication;
import org.camunda.bpm.engine.impl.identity.db.DbGroupQueryImpl;
import org.camunda.bpm.engine.impl.identity.db.DbIdentityServiceProvider;
import org.camunda.bpm.engine.impl.identity.db.DbTenantQueryImpl;
import org.camunda.bpm.engine.impl.identity.db.DbUserQueryImpl;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandExecutor;
import org.camunda.bpm.engine.impl.persistence.entity.GroupEntity;
import org.camunda.bpm.engine.impl.persistence.entity.TenantEntity;
import org.camunda.bpm.engine.impl.persistence.entity.UserEntity;

/**
 * The database identity provider, except for the user who makes the request:
 * the groups and tenants of that user are those of the authentication of the
 * request (set by the single sign-on filters from the claims of the token),
 * and the user exists even without a row in the database. All writes, and all
 * questions about other users, go to the database.
 */
public class OidcIdentityProvider extends DbIdentityServiceProvider {

  protected static Authentication current(CommandContext commandContext) {
    CommandContext context = commandContext != null ? commandContext : Context.getCommandContext();
    return context == null ? null : context.getAuthentication();
  }

  protected static CommandExecutor executor() {
    return Context.getProcessEngineConfiguration().getCommandExecutorTxRequired();
  }

  @Override
  public UserQuery createUserQuery() {
    return new OidcUserQuery(executor());
  }

  @Override
  public UserQueryImpl createUserQuery(CommandContext commandContext) {
    return new OidcUserQuery();
  }

  @Override
  public GroupQuery createGroupQuery() {
    return new OidcGroupQuery(executor());
  }

  @Override
  public GroupQuery createGroupQuery(CommandContext commandContext) {
    return new OidcGroupQuery();
  }

  @Override
  public TenantQuery createTenantQuery() {
    return new OidcTenantQuery(executor());
  }

  @Override
  public TenantQuery createTenantQuery(CommandContext commandContext) {
    return new OidcTenantQuery();
  }

  protected static boolean matchesLike(String like, String value) {
    if (like == null) {
      return true;
    }
    if (value == null) {
      return false;
    }
    String regex = Arrays.stream(like.toLowerCase(Locale.ROOT).split("%", -1)).map(java.util.regex.Pattern::quote)
        .reduce((a, b) -> a + ".*" + b).orElse("");
    return value.toLowerCase(Locale.ROOT).matches(regex);
  }

  protected static <T> List<T> page(List<T> list, Page page) {
    if (page == null) {
      return list;
    }
    int from = Math.min(list.size(), Math.max(0, page.getFirstResult()));
    int to = (int) Math.min(list.size(), (long) from + Math.max(0, page.getMaxResults()));
    return new ArrayList<>(list.subList(from, to));
  }

  /** Users: the user of the request exists even without a database row. */
  public static class OidcUserQuery extends DbUserQueryImpl {
    private static final long serialVersionUID = 1L;

    public OidcUserQuery() {
    }

    public OidcUserQuery(CommandExecutor commandExecutor) {
      super(commandExecutor);
    }

    @Override
    public long executeCount(CommandContext commandContext) {
      return synthesized(commandContext) != null ? 1 : super.executeCount(commandContext);
    }

    @Override
    public List<User> executeList(CommandContext commandContext, Page page) {
      User user = synthesized(commandContext);
      return user != null ? page(List.of(user), page) : super.executeList(commandContext, page);
    }

    /** The user of the request if the query asks for exactly that user and the database does not know it. */
    protected User synthesized(CommandContext commandContext) {
      Authentication authentication = current(commandContext);
      boolean onlyId = firstName == null && firstNameLike == null && lastName == null && lastNameLike == null
          && email == null && emailLike == null && groupId == null && procDefId == null && tenantId == null && ids == null;
      if (authentication == null || id == null || !onlyId || !id.equals(authentication.getUserId())) {
        return null;
      }
      if (super.executeCount(commandContext) > 0) {
        return null;
      }
      UserEntity user = new UserEntity();
      user.setId(id);
      return user;
    }
  }

  /** Groups: those of the user of the request come from the authentication. */
  public static class OidcGroupQuery extends DbGroupQueryImpl {
    private static final long serialVersionUID = 1L;

    public OidcGroupQuery() {
    }

    public OidcGroupQuery(CommandExecutor commandExecutor) {
      super(commandExecutor);
    }

    @Override
    public long executeCount(CommandContext commandContext) {
      List<Group> groups = ofAuthentication(commandContext);
      return groups != null ? groups.size() : super.executeCount(commandContext);
    }

    @Override
    public List<Group> executeList(CommandContext commandContext, Page page) {
      List<Group> groups = ofAuthentication(commandContext);
      return groups != null ? page(groups, page) : super.executeList(commandContext, page);
    }

    protected List<Group> ofAuthentication(CommandContext commandContext) {
      Authentication authentication = current(commandContext);
      if (authentication == null || userId == null || !userId.equals(authentication.getUserId())
          || authentication.getGroupIds() == null || procDefId != null || tenantId != null) {
        return null;
      }
      Function<String, Group> lookup = groupId -> {
        // names and types of known groups; the request may not be allowed to read groups
        Group stored = commandContext.runWithoutAuthorization(
            () -> commandContext.getReadOnlyIdentityProvider().findGroupById(groupId));
        if (stored != null) {
          return stored;
        }
        GroupEntity group = new GroupEntity();
        group.setId(groupId);
        group.setName(groupId);
        return group;
      };
      List<Group> result = new ArrayList<>();
      for (String groupId : authentication.getGroupIds().stream().sorted().toList()) {
        Group group = lookup.apply(groupId);
        if ((id == null || id.equals(groupId)) && (ids == null || Arrays.asList(ids).contains(groupId))
            && (name == null || name.equals(group.getName())) && matchesLike(nameLike, group.getName())
            && (type == null || type.equals(group.getType()))) {
          result.add(group);
        }
      }
      return result;
    }
  }

  /** Tenants: those of the user of the request come from the authentication. */
  public static class OidcTenantQuery extends DbTenantQueryImpl {
    private static final long serialVersionUID = 1L;

    public OidcTenantQuery() {
    }

    public OidcTenantQuery(CommandExecutor commandExecutor) {
      super(commandExecutor);
    }

    @Override
    public long executeCount(CommandContext commandContext) {
      List<Tenant> tenants = ofAuthentication(commandContext);
      return tenants != null ? tenants.size() : super.executeCount(commandContext);
    }

    @Override
    public List<Tenant> executeList(CommandContext commandContext, Page page) {
      List<Tenant> tenants = ofAuthentication(commandContext);
      return tenants != null ? page(tenants, page) : super.executeList(commandContext, page);
    }

    protected List<Tenant> ofAuthentication(CommandContext commandContext) {
      Authentication authentication = current(commandContext);
      if (authentication == null || userId == null || !userId.equals(authentication.getUserId())
          || authentication.getTenantIds() == null || groupId != null) {
        return null;
      }
      List<Tenant> result = new ArrayList<>();
      for (String tenantId : authentication.getTenantIds().stream().sorted().toList()) {
        Tenant tenant = commandContext.runWithoutAuthorization(
            () -> (Tenant) commandContext.getReadOnlyIdentityProvider().findTenantById(tenantId));
        if (tenant == null) {
          TenantEntity entity = new TenantEntity();
          entity.setId(tenantId);
          entity.setName(tenantId);
          tenant = entity;
        }
        if ((id == null || id.equals(tenantId)) && (ids == null || Arrays.asList(ids).contains(tenantId))
            && (name == null || name.equals(tenant.getName())) && matchesLike(nameLike, tenant.getName())) {
          result.add(tenant);
        }
      }
      return result;
    }
  }
}
