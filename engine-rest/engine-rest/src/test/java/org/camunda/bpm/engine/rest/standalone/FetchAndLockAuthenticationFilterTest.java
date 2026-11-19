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
package org.camunda.bpm.engine.rest.standalone;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.core.Response.Status;

import org.camunda.bpm.engine.ExternalTaskService;
import org.camunda.bpm.engine.externaltask.ExternalTaskQueryTopicBuilder;
import org.camunda.bpm.engine.externaltask.FetchAndLockBuilder;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.GroupQuery;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.TenantQuery;
import org.camunda.bpm.engine.impl.IdentityServiceImpl;
import org.camunda.bpm.engine.impl.util.ClockUtil;
import org.camunda.bpm.engine.rest.AbstractRestServiceTest;
import org.camunda.bpm.engine.rest.dto.externaltask.FetchExternalTasksExtendedDto;
import org.camunda.bpm.engine.rest.helper.MockProvider;
import org.camunda.bpm.engine.rest.util.container.TestContainerRule;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import io.restassured.http.ContentType;

/**
 * Fetching and locking external tasks suspends the request (long polling).
 * This requires that every filter the request passes supports asynchronous
 * processing, the authentication filter included. The REST API is deployed
 * here with the authentication filter and the servlet declared as
 * <code>async-supported</code>, as in the shipped deployment descriptors.
 */
public class FetchAndLockAuthenticationFilterTest extends AbstractRestServiceTest {

  @ClassRule
  public static TestContainerRule testContainer = new TestContainerRule();

  protected static final String FETCH_AND_LOCK_URL = TEST_RESOURCE_ROOT_PATH + "/rest/external-task/fetchAndLock";

  protected static final long ASYNC_RESPONSE_TIMEOUT = 1000L;

  /** a request that is not resumed must fail the test instead of blocking the build */
  protected static final long TEST_TIMEOUT = 60000L;

  protected IdentityServiceImpl identityServiceMock;
  protected ExternalTaskService externalTaskServiceMock;
  protected FetchAndLockBuilder fetchAndLockBuilder;
  protected ExternalTaskQueryTopicBuilder fetchTopicBuilder;

  protected List<String> groupIds;
  protected List<String> tenantIds;

  @Before
  public void setUpRuntimeData() {
    // the timeout of a suspended request is evaluated against the engine clock
    ClockUtil.reset();

    identityServiceMock = mock(IdentityServiceImpl.class);
    externalTaskServiceMock = mock(ExternalTaskService.class);
    fetchAndLockBuilder = mock(FetchAndLockBuilder.class);
    fetchTopicBuilder = mock(ExternalTaskQueryTopicBuilder.class);

    when(processEngine.getIdentityService()).thenReturn(identityServiceMock);
    when(processEngine.getExternalTaskService()).thenReturn(externalTaskServiceMock);

    // authentication
    when(identityServiceMock.checkPassword(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)).thenReturn(true);

    List<Group> groups = MockProvider.createMockGroups();
    GroupQuery groupQuery = mock(GroupQuery.class);
    when(identityServiceMock.createGroupQuery()).thenReturn(groupQuery);
    when(groupQuery.groupMember(anyString())).thenReturn(groupQuery);
    when(groupQuery.list()).thenReturn(groups);
    groupIds = new ArrayList<>();
    for (Group group : groups) {
      groupIds.add(group.getId());
    }

    List<Tenant> tenants = Collections.singletonList(MockProvider.createMockTenant());
    TenantQuery tenantQuery = mock(TenantQuery.class);
    when(identityServiceMock.createTenantQuery()).thenReturn(tenantQuery);
    when(tenantQuery.userMember(anyString())).thenReturn(tenantQuery);
    when(tenantQuery.includingGroupsOfUser(anyBoolean())).thenReturn(tenantQuery);
    when(tenantQuery.list()).thenReturn(tenants);
    tenantIds = new ArrayList<>();
    for (Tenant tenant : tenants) {
      tenantIds.add(tenant.getId());
    }

    // fetch and lock: no external task is available
    when(externalTaskServiceMock.fetchAndLock(anyInt(), anyString(), anyBoolean())).thenReturn(fetchTopicBuilder);
    when(externalTaskServiceMock.fetchAndLock()).thenReturn(fetchAndLockBuilder);

    when(fetchAndLockBuilder.workerId(anyString())).thenReturn(fetchAndLockBuilder);
    when(fetchAndLockBuilder.maxTasks(anyInt())).thenReturn(fetchAndLockBuilder);
    when(fetchAndLockBuilder.usePriority(anyBoolean())).thenReturn(fetchAndLockBuilder);
    when(fetchAndLockBuilder.orderByCreateTime()).thenReturn(fetchAndLockBuilder);
    when(fetchAndLockBuilder.asc()).thenReturn(fetchAndLockBuilder);
    when(fetchAndLockBuilder.desc()).thenReturn(fetchAndLockBuilder);
    when(fetchAndLockBuilder.subscribe()).thenReturn(fetchTopicBuilder);

    when(fetchTopicBuilder.topic(anyString(), anyLong())).thenReturn(fetchTopicBuilder);
    when(fetchTopicBuilder.variables(anyList())).thenReturn(fetchTopicBuilder);
    when(fetchTopicBuilder.enableCustomObjectDeserialization()).thenReturn(fetchTopicBuilder);
    when(fetchTopicBuilder.processDefinitionVersionTag(anyString())).thenReturn(fetchTopicBuilder);
    when(fetchTopicBuilder.execute()).thenReturn(Collections.emptyList());
  }

  @After
  public void resetClock() {
    ClockUtil.reset();
  }

  @Test(timeout = TEST_TIMEOUT)
  public void shouldSuspendAuthenticatedRequest() {
    // given
    long start = System.currentTimeMillis();

    // when
    given()
      .auth().preemptive().basic(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)
      .contentType(ContentType.JSON)
      .body(createDto(ASYNC_RESPONSE_TIMEOUT))
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
      .body("size()", is(0))
    .when()
      .post(FETCH_AND_LOCK_URL);

    // then the request was suspended until its timeout expired
    assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(ASYNC_RESPONSE_TIMEOUT - 100L);
    verify(identityServiceMock).setAuthentication(MockProvider.EXAMPLE_USER_ID, groupIds, tenantIds);
  }

  @Test(timeout = TEST_TIMEOUT)
  public void shouldAnswerAuthenticatedRequestWithoutTimeout() {
    given()
      .auth().preemptive().basic(MockProvider.EXAMPLE_USER_ID, MockProvider.EXAMPLE_USER_PASSWORD)
      .contentType(ContentType.JSON)
      .body(createDto(null))
    .then().expect()
      .statusCode(Status.OK.getStatusCode())
      .body("size()", is(0))
    .when()
      .post(FETCH_AND_LOCK_URL);

    verify(identityServiceMock).setAuthentication(MockProvider.EXAMPLE_USER_ID, groupIds, tenantIds);
  }

  @Test(timeout = TEST_TIMEOUT)
  public void shouldRejectUnauthenticatedRequest() {
    given()
      .contentType(ContentType.JSON)
      .body(createDto(ASYNC_RESPONSE_TIMEOUT))
    .then().expect()
      .statusCode(Status.UNAUTHORIZED.getStatusCode())
    .when()
      .post(FETCH_AND_LOCK_URL);
  }

  protected FetchExternalTasksExtendedDto createDto(Long asyncResponseTimeout) {
    FetchExternalTasksExtendedDto dto = new FetchExternalTasksExtendedDto();
    if (asyncResponseTimeout != null) {
      dto.setAsyncResponseTimeout(asyncResponseTimeout);
    }
    dto.setMaxTasks(5);
    dto.setWorkerId("aWorkerId");

    FetchExternalTasksExtendedDto.FetchExternalTaskTopicDto topicDto = new FetchExternalTasksExtendedDto.FetchExternalTaskTopicDto();
    topicDto.setTopicName("aTopicName");
    topicDto.setLockDuration(12354L);
    dto.setTopics(Collections.singletonList(topicDto));
    return dto;
  }

}
