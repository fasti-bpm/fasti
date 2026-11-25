/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
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
package org.camunda.bpm.admin.impl.web;

import static org.camunda.bpm.engine.authorization.Authorization.ANY;
import static org.camunda.bpm.engine.authorization.Authorization.AUTH_TYPE_GRANT;
import static org.camunda.bpm.engine.authorization.Permissions.ALL;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Iterator;
import java.util.ServiceLoader;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.ext.Providers;
import org.camunda.bpm.engine.AuthorizationService;
import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.authorization.Groups;
import org.camunda.bpm.engine.authorization.Resource;
import org.camunda.bpm.engine.authorization.Resources;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.impl.persistence.entity.AuthorizationEntity;
import org.camunda.bpm.engine.rest.dto.identity.UserDto;
import org.camunda.bpm.engine.rest.impl.UserRestServiceImpl;
import org.camunda.bpm.engine.rest.spi.ProcessEngineProvider;
import org.camunda.bpm.engine.rest.util.ProvidersUtil;
import org.camunda.bpm.webapp.impl.WebappLogger;
import org.camunda.bpm.webapp.impl.security.InitialAdminSetup;
import org.camunda.bpm.webapp.impl.security.InitialSetupToken;
import org.camunda.bpm.webapp.impl.security.SecurityActions;
import org.camunda.bpm.webapp.impl.security.SecurityActions.SecurityAction;
import org.camunda.bpm.webapp.impl.util.ServletContextUtil;

/**
 * <p>Jax RS resource allowing to perform the setup steps.</p>
 *
 * <p>All methods of this class must throw Status.FORBIDDEN exception if
 * setup actions are unavailable.</p>
 *
 * @author Daniel Meyer
 *
 */
@Path("/setup/{engine}")
public class SetupResource {

  protected final static WebappLogger LOGGER = WebappLogger.INSTANCE;

  /**
   * Guards the check whether the setup is available and the creation of the
   * initial user. Without it, concurrent requests could all pass the check
   * before the first of them has created its user, and each of them would
   * create an administrator. The lock is effective within one JVM only.
   */
  protected static final Object SETUP_LOCK = new Object();

  @Context
  protected Providers providers;

  @Context
  protected HttpServletRequest request;

  @Path("/user/create")
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public void createInitialUser(final @PathParam("engine") String processEngineName, final UserDto user) throws IOException, ServletException {

    final ProcessEngine processEngine = lookupProcessEngine(processEngineName);
    if(processEngine == null) {
      throw LOGGER.invalidRequestEngineNotFoundForName(processEngineName);
    }

    SecurityActions.runWithoutAuthentication(new SecurityAction<Void>() {
      public Void execute() {
        createInitialUserInternal(processEngineName, user, processEngine);
        return null;
      }
    }, processEngine);

  }

  protected void createInitialUserInternal(String processEngineName, UserDto user, ProcessEngine processEngine) {

    ObjectMapper objectMapper = getObjectMapper();

    synchronized (SETUP_LOCK) {

      // make sure we can process this request at this time
      ensureSetupAvailable(processEngine);
      ensureSetupTokenPresented(processEngineName);

      // reuse logic from rest api implementation
      UserRestServiceImpl userRestServiceImpl = new UserRestServiceImpl(processEngineName, objectMapper);
      userRestServiceImpl.createUser(user);

      // crate the camunda admin group
      ensureCamundaAdminGroupExists(processEngine);

      // create group membership (add new user to admin group)
      processEngine.getIdentityService()
        .createMembership(user.getProfile().getId(), Groups.CAMUNDA_ADMIN);

      // a printed token serves once: should the setup open again, a new one is printed
      InitialSetupToken.consume(processEngineName);
    }
  }

  protected ObjectMapper getObjectMapper() {
    if(providers != null) {
      return ProvidersUtil
        .resolveFromContext(providers, ObjectMapper.class, MediaType.APPLICATION_JSON_TYPE, this.getClass());
    }
    else {
      return null;
    }
  }

  protected void ensureCamundaAdminGroupExists(ProcessEngine processEngine) {

    final IdentityService identityService = processEngine.getIdentityService();
    final AuthorizationService authorizationService = processEngine.getAuthorizationService();

    // create group
    if(identityService.createGroupQuery().groupId(Groups.CAMUNDA_ADMIN).count() == 0) {
      Group camundaAdminGroup = identityService.newGroup(Groups.CAMUNDA_ADMIN);
      camundaAdminGroup.setName("camunda BPM Administrators");
      camundaAdminGroup.setType(Groups.GROUP_TYPE_SYSTEM);
      identityService.saveGroup(camundaAdminGroup);
    }

    // create ADMIN authorizations on all built-in resources
    for (Resource resource : Resources.values()) {
      if(authorizationService.createAuthorizationQuery().groupIdIn(Groups.CAMUNDA_ADMIN).resourceType(resource).resourceId(ANY).count() == 0) {
        AuthorizationEntity userAdminAuth = new AuthorizationEntity(AUTH_TYPE_GRANT);
        userAdminAuth.setGroupId(Groups.CAMUNDA_ADMIN);
        userAdminAuth.setResource(resource);
        userAdminAuth.setResourceId(ANY);
        userAdminAuth.addPermission(ALL);
        authorizationService.saveAuthorization(userAdminAuth);
      }
    }

  }

  /**
   * The setup is only available if it has not been switched off for the web
   * application (see {@link ServletContextUtil#isInitialSetupEnabled(javax.servlet.ServletContext)})
   * and as long as no administrator is defined for the engine, see
   * {@link InitialAdminSetup} for the rules. Refused attempts are logged, the
   * reason is not disclosed to the caller.
   */
  protected void ensureSetupAvailable(ProcessEngine processEngine) {
    String reason;
    if (!isSetupEnabled()) {
      reason = "the initial setup is switched off in the configuration of the web application";
    } else {
      reason = InitialAdminSetup.getUnavailabilityReason(processEngine);
    }

    if (reason != null) {
      LOGGER.warnSetupActionRefused(processEngine.getName(), reason);
      throw LOGGER.setupActionNotAvailable();
    }
  }

  /**
   * The setup also asks for the one-time token printed in the server log, so
   * that only the person who runs the server can create the first
   * administrator, see {@link InitialSetupToken}.
   */
  protected void ensureSetupTokenPresented(String processEngineName) {
    if (!InitialSetupToken.matches(processEngineName, presentedSetupToken())) {
      LOGGER.warnSetupActionRefused(processEngineName, "the setup token is missing or wrong");
      throw LOGGER.setupActionNotAvailable();
    }
  }

  protected String presentedSetupToken() {
    return request == null ? null : request.getHeader(InitialSetupToken.HEADER);
  }

  protected boolean isSetupEnabled() {
    // without a servlet request, i.e. outside of a servlet container, nothing
    // can be configured
    return request == null || ServletContextUtil.isInitialSetupEnabled(request.getServletContext());
  }

  protected ProcessEngine lookupProcessEngine(String engineName) {

    ServiceLoader<ProcessEngineProvider> serviceLoader = ServiceLoader.load(ProcessEngineProvider.class);
    Iterator<ProcessEngineProvider> iterator = serviceLoader.iterator();

    if (iterator.hasNext()) {
      ProcessEngineProvider provider = iterator.next();
      return provider.getProcessEngine(engineName);

    } else {
      throw LOGGER.processEngineProviderNotFound();

    }

  }


}
