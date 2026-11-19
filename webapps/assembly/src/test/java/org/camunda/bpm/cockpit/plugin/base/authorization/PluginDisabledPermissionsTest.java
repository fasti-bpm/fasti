/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.cockpit.plugin.base.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.bpm.engine.authorization.Authorization.ANY;
import static org.camunda.bpm.engine.authorization.Permissions.READ;
import static org.camunda.bpm.engine.authorization.Permissions.READ_INSTANCE;
import static org.camunda.bpm.engine.authorization.Resources.PROCESS_DEFINITION;
import static org.camunda.bpm.engine.authorization.Resources.PROCESS_INSTANCE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import org.camunda.bpm.cockpit.impl.plugin.base.dto.ProcessInstanceDto;
import org.camunda.bpm.cockpit.impl.plugin.base.dto.query.IncidentQueryDto;
import org.camunda.bpm.cockpit.impl.plugin.base.dto.query.ProcessInstanceQueryDto;
import org.camunda.bpm.cockpit.impl.plugin.resources.IncidentRestService;
import org.camunda.bpm.cockpit.impl.plugin.resources.ProcessDefinitionRestService;
import org.camunda.bpm.cockpit.impl.plugin.resources.ProcessInstanceRestService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * Verifies that the Cockpit plugin queries only skip the check of a permission
 * that is listed in the disabled permissions of the engine configuration.
 */
public class PluginDisabledPermissionsTest extends AuthorizationTest {

  protected static final String FAILING_PROCESS_KEY = "FailingProcess";
  protected static final int INSTANCES = 3;

  protected String deploymentId;
  protected List<String> originalDisabledPermissions;

  protected ProcessDefinitionRestService definitionResource;
  protected ProcessInstanceRestService instanceResource;
  protected IncidentRestService incidentResource;
  protected UriInfo uriInfo;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();

    originalDisabledPermissions = processEngineConfiguration.getDisabledPermissions();

    deploymentId = createDeployment(null, "processes/failing-process.bpmn").getId();
    startProcessInstances(FAILING_PROCESS_KEY, INSTANCES);
    disableAuthorization();
    executeAvailableJobs();
    enableAuthorization();

    definitionResource = new ProcessDefinitionRestService(engineName);
    instanceResource = new ProcessInstanceRestService(engineName);
    incidentResource = new IncidentRestService(engineName);

    MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>();
    uriInfo = Mockito.mock(UriInfo.class);
    Mockito.doReturn(queryParameters).when(uriInfo).getQueryParameters();
  }

  @Override
  @After
  public void tearDown() {
    processEngineConfiguration.setDisabledPermissions(originalDisabledPermissions);
    deleteDeployment(deploymentId);
    super.tearDown();
  }

  @Test
  public void shouldCheckPermissionsWhenUnrelatedPermissionIsDisabled() {
    // given
    disablePermissions("DELETE");

    // then
    assertNothingVisible();
  }

  @Test
  public void shouldCheckPermissionsWhenSeveralUnrelatedPermissionsAreDisabled() {
    // given
    disablePermissions("DELETE", "UPDATE_VARIABLE", "notAPermission");

    // then
    assertNothingVisible();
  }

  @Test
  public void shouldCheckPermissionsWhenDisabledPermissionsAreEmpty() {
    // given
    processEngineConfiguration.setDisabledPermissions(Collections.<String>emptyList());

    // then
    assertNothingVisible();
  }

  @Test
  public void shouldCheckPermissionsWhenDisabledPermissionsAreNull() {
    // given
    processEngineConfiguration.setDisabledPermissions(null);

    // then
    assertNothingVisible();
  }

  @Test
  public void shouldCheckPermissionsWhenDisabledPermissionOnlyDiffersInCase() {
    // given
    disablePermissions("read", "read_instance");

    // then
    assertNothingVisible();
  }

  @Test
  public void shouldGrantAccessByAuthorizationWhenUnrelatedPermissionIsDisabled() {
    // given
    disablePermissions("DELETE");
    createGrantAuthorization(PROCESS_DEFINITION, ANY, userId, READ, READ_INSTANCE);

    // then
    assertThat(definitionResource.queryStatistics(uriInfo, null, null)).hasSize(1);
    assertThat(instanceResource.queryProcessInstances(new ProcessInstanceQueryDto(), null, null)).hasSize(INSTANCES);
    assertThat(incidentResource.queryIncidents(new IncidentQueryDto(), null, null)).hasSize(INSTANCES);
  }

  @Test
  public void shouldStillCheckReadInstanceWhenOnlyReadIsDisabled() {
    // given
    disablePermissions(READ.getName());

    // then
    // instances and incidents remain guarded by READ_INSTANCE on the definition
    assertThat(instanceResource.queryProcessInstances(new ProcessInstanceQueryDto(), null, null)).isEmpty();
    assertThat(instanceResource.queryProcessInstancesCount(new ProcessInstanceQueryDto()).getCount()).isZero();
    assertThat(incidentResource.queryIncidents(new IncidentQueryDto(), null, null)).isEmpty();
    assertThat(incidentResource.queryIncidentsCount(new IncidentQueryDto()).getCount()).isZero();
  }

  @Test
  public void shouldStillCheckReadWhenOnlyReadInstanceIsDisabled() {
    // given
    disablePermissions(READ_INSTANCE.getName());

    // then
    assertNothingVisible();
  }

  @Test
  public void shouldSkipCheckOfDisabledPermission() {
    // given
    disablePermissions("DELETE", READ.getName());

    // then
    // READ is the only permission guarding the definition statistics
    assertThat(definitionResource.queryStatistics(uriInfo, null, null)).hasSize(1);
    assertThat(definitionResource.getStatisticsCount(uriInfo).getCount()).isEqualTo(1);
  }

  @Test
  public void shouldApplyRemainingReadCheckWhenOnlyReadInstanceIsDisabled() {
    // given
    disablePermissions(READ_INSTANCE.getName());
    String processInstanceId = selectAnyProcessInstanceByKey(FAILING_PROCESS_KEY).getId();
    createGrantAuthorization(PROCESS_INSTANCE, processInstanceId, userId, READ);

    // when
    List<ProcessInstanceDto> instances = instanceResource.queryProcessInstances(new ProcessInstanceQueryDto(), null, null);

    // then
    // READ on the instance is still evaluated, it is not switched off together
    // with READ_INSTANCE
    assertThat(instances).hasSize(1);
    assertThat(instances.get(0).getId()).isEqualTo(processInstanceId);
    assertThat(instanceResource.queryProcessInstancesCount(new ProcessInstanceQueryDto()).getCount()).isEqualTo(1);
    assertThat(incidentResource.queryIncidents(new IncidentQueryDto(), null, null)).hasSize(1);
    assertThat(incidentResource.queryIncidentsCount(new IncidentQueryDto()).getCount()).isEqualTo(1);
  }

  @Test
  public void shouldSkipAllChecksWhenEveryCheckedPermissionIsDisabled() {
    // given
    disablePermissions(READ.getName(), READ_INSTANCE.getName());

    // then
    // no permission check is left, which is what the engine queries do as well
    assertThat(definitionResource.queryStatistics(uriInfo, null, null)).hasSize(1);
    assertThat(instanceResource.queryProcessInstances(new ProcessInstanceQueryDto(), null, null)).hasSize(INSTANCES);
    assertThat(instanceResource.queryProcessInstancesCount(new ProcessInstanceQueryDto()).getCount()).isEqualTo(INSTANCES);
    assertThat(incidentResource.queryIncidents(new IncidentQueryDto(), null, null)).hasSize(INSTANCES);
    assertThat(incidentResource.queryIncidentsCount(new IncidentQueryDto()).getCount()).isEqualTo(INSTANCES);
  }

  protected void disablePermissions(String... permissionNames) {
    processEngineConfiguration.setDisabledPermissions(new ArrayList<>(Arrays.asList(permissionNames)));
  }

  protected void assertNothingVisible() {
    assertThat(definitionResource.queryStatistics(uriInfo, null, null)).isEmpty();
    assertThat(definitionResource.getStatisticsCount(uriInfo).getCount()).isZero();

    assertThat(instanceResource.queryProcessInstances(new ProcessInstanceQueryDto(), null, null)).isEmpty();
    assertThat(instanceResource.queryProcessInstancesCount(new ProcessInstanceQueryDto()).getCount()).isZero();

    assertThat(incidentResource.queryIncidents(new IncidentQueryDto(), null, null)).isEmpty();
    assertThat(incidentResource.queryIncidentsCount(new IncidentQueryDto()).getCount()).isZero();
  }

}
