/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.security;

import static org.camunda.bpm.engine.authorization.Authorization.ANY;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GLOBAL;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;

import java.util.List;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.authorization.Authorization;
import org.camunda.bpm.engine.authorization.Groups;
import org.camunda.bpm.engine.authorization.Permissions;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;

/**
 * <p>Decides whether the initial setup of the webapps, i.e. the creation of the
 * first administrator by an <strong>unauthenticated</strong> user, is available
 * for a process engine.</p>
 *
 * <p>The setup is available only as long as no administrator is defined for the
 * engine. Since whoever completes the setup gains full control over the engine,
 * every doubt is resolved against the setup. An administrator is considered to be
 * defined if at least one of the following holds:</p>
 *
 * <ol>
 *   <li>The identity service is read-only. Users are managed by an external
 *   system (e.g. LDAP), so the setup could not create a user anyway.</li>
 *   <li>The engine configuration names an administrator user
 *   ({@link ProcessEngineConfigurationImpl#getAdminUsers()}) or an administrator
 *   group other than the built-in group {@link Groups#CAMUNDA_ADMIN}
 *   ({@link ProcessEngineConfigurationImpl#getAdminGroups()}). This includes the
 *   principals registered by the
 *   {@link org.camunda.bpm.engine.impl.plugin.AdministratorAuthorizationPlugin}.</li>
 *   <li>A user is member of the built-in group {@link Groups#CAMUNDA_ADMIN}.</li>
 *   <li>A user or a group other than {@link Groups#CAMUNDA_ADMIN} is permitted
 *   to create or to change authorizations, i.e. it is granted
 *   {@link Permissions#CREATE}, {@link Permissions#UPDATE} or
 *   {@link Permissions#ALL} on all instances of the resource
 *   {@link Resources#AUTHORIZATION}. Such a principal can grant itself, or
 *   anybody else, every other permission, so it does not matter which
 *   permissions it holds on the other resources. Every way of defining an
 *   administrator that the engine offers creates such an authorization.</li>
 *   <li>Every user is unrestricted, because authorization is disabled or a
 *   global authorization permits everybody to create or to change
 *   authorizations, and at least one user exists.</li>
 * </ol>
 *
 * <p>Rules 2 and 4 intentionally do <strong>not</strong> require the principal
 * to exist in the identity service. A principal that holds administrator rights
 * may be supplied by an external authentication mechanism (single sign-on,
 * container-based authentication) which never creates it in the identity
 * service, so its absence proves nothing. If it really is missing, keeping the
 * setup open would allow anybody to create an administrator, or to register the
 * privileged user id and take over the rights prepared for it. The operator who
 * defined the principal is expected to create it by the regular means (identity
 * API, REST API with another administrator, application configuration).</p>
 *
 * <p>The built-in group {@link Groups#CAMUNDA_ADMIN} is the only exception: it
 * is part of every engine configuration and its authorizations are created by
 * the setup itself, hence it only counts once it has a member (rule 3).
 * Consequently the setup becomes available again when the last member of that
 * group is removed and no other administrator is defined.</p>
 *
 * <p>The methods of this class query users and authorizations and must be
 * invoked without an authenticated user, see
 * {@link SecurityActions#runWithoutAuthentication(org.camunda.bpm.webapp.impl.security.SecurityActions.SecurityAction, ProcessEngine)}.</p>
 */
public class InitialAdminSetup {

  /**
   * @return <code>true</code> if no administrator is defined for the given
   *         engine and the initial setup may be performed
   */
  public static boolean isAvailable(ProcessEngine processEngine) {
    return getUnavailabilityReason(processEngine) == null;
  }

  /**
   * @return a description of the administrator definition that makes the
   *         initial setup unavailable for the given engine, or
   *         <code>null</code> if the setup is available
   */
  public static String getUnavailabilityReason(ProcessEngine processEngine) {
    IdentityService identityService = processEngine.getIdentityService();

    if (identityService.isReadOnly()) {
      return "the identity service is read-only, users are managed externally";
    }

    ProcessEngineConfiguration configuration = processEngine.getProcessEngineConfiguration();

    if (configuration instanceof ProcessEngineConfigurationImpl) {
      ProcessEngineConfigurationImpl configurationImpl = (ProcessEngineConfigurationImpl) configuration;

      if (containsPrincipalOtherThan(configurationImpl.getAdminUsers(), null)) {
        return "an administrator user is defined in the process engine configuration";
      }
      if (containsPrincipalOtherThan(configurationImpl.getAdminGroups(), Groups.CAMUNDA_ADMIN)) {
        return "an administrator group is defined in the process engine configuration";
      }
    }

    if (identityService.createUserQuery().memberOfGroup(Groups.CAMUNDA_ADMIN).count() > 0) {
      return "the group '" + Groups.CAMUNDA_ADMIN + "' has members";
    }

    boolean allUsersUnrestricted = !configuration.isAuthorizationEnabled();
    String unrestrictedReason = "authorization is disabled and users exist";

    List<Authorization> authorizations = processEngine.getAuthorizationService()
        .createAuthorizationQuery()
        .resourceType(Resources.AUTHORIZATION)
        .resourceId(ANY)
        .list();

    for (Authorization authorization : authorizations) {
      if (!permitsAuthorizationManagement(authorization)) {
        continue;
      }

      if (authorization.getAuthorizationType() == AUTH_TYPE_GLOBAL) {
        if (!allUsersUnrestricted) {
          allUsersUnrestricted = true;
          unrestrictedReason = "everybody is permitted to create or change authorizations and users exist";
        }

      } else if (authorization.getUserId() != null) {
        return "a user is permitted to create or change authorizations";

      } else if (authorization.getGroupId() != null && !Groups.CAMUNDA_ADMIN.equals(authorization.getGroupId())) {
        return "a group other than '" + Groups.CAMUNDA_ADMIN + "' is permitted to create or change authorizations";
      }
    }

    if (allUsersUnrestricted && identityService.createUserQuery().count() > 0) {
      return unrestrictedReason;
    }

    return null;
  }

  /**
   * @return <code>true</code> if the given authorization on all instances of
   *         the resource {@link Resources#AUTHORIZATION} allows its holder to
   *         create or to change authorizations. A revoke authorization never
   *         does.
   */
  protected static boolean permitsAuthorizationManagement(Authorization authorization) {
    int authorizationType = authorization.getAuthorizationType();
    if (authorizationType != AUTH_TYPE_GLOBAL && authorizationType != AUTH_TYPE_GRANT) {
      return false;
    }
    return authorization.isPermissionGranted(Permissions.CREATE)
        || authorization.isPermissionGranted(Permissions.UPDATE);
  }

  protected static boolean containsPrincipalOtherThan(List<String> principalIds, String ignoredId) {
    if (principalIds != null) {
      for (String principalId : principalIds) {
        if (principalId != null && !principalId.trim().isEmpty() && !principalId.equals(ignoredId)) {
          return true;
        }
      }
    }
    return false;
  }

}
