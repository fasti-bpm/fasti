/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.security.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.ws.rs.core.Response;

import org.camunda.bpm.engine.IdentityService;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class UserPreferencesResourceTest {

  protected static final String ENGINE = "webapps-test-engine";

  @Rule
  public ProcessEngineRule processEngineRule = new ProcessEngineRule("camunda-test-engine.cfg.xml");

  protected IdentityService identityService;
  protected UserPreferencesResource resource = new UserPreferencesResource();

  @Before
  public void setUp() {
    identityService = processEngineRule.getIdentityService();
  }

  @After
  public void tearDown() {
    for (String user : List.of("alice", "bob")) {
      for (String key : identityService.getUserInfoKeys(user)) {
        identityService.deleteUserInfo(user, key);
      }
    }
    Authentications.clearCurrent();
  }

  @Test
  public void shouldReturnEmptyObjectWhenNothingIsStored() {
    loginAs("alice", ENGINE);

    Response response = resource.get(ENGINE);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getEntity()).isEqualTo("{}");
    assertThat(response.getHeaderString("Cache-Control")).isEqualTo("no-store");
  }

  @Test
  public void shouldStoreAndReturnDocumentLargerThanOneChunk() throws IOException {
    loginAs("alice", ENGINE);
    String long_ = "x".repeat(2000);
    String document = "{\"lang\":\"vi\",\"note\":\"Tiếng Việt " + long_ + "\",\"searches\":[{\"name\":\"a\"}]}";

    assertThat(put(document).getStatus()).isEqualTo(204);

    assertThat(resource.get(ENGINE).getEntity()).isEqualTo(document);
    assertThat(identityService.getUserInfoKeys("alice").size()).isGreaterThan(3);
  }

  @Test
  public void shouldReplaceDocumentAndDropStaleChunks() throws IOException {
    loginAs("alice", ENGINE);
    put("{\"a\":\"" + "y".repeat(3000) + "\"}");

    put("{\"a\":1}");

    assertThat(resource.get(ENGINE).getEntity()).isEqualTo("{\"a\":1}");
    assertThat(identityService.getUserInfoKeys("alice"))
        .containsExactlyInAnyOrder(UserPreferencesStore.KEY_PREFIX + "0", UserPreferencesStore.COUNT_KEY);
  }

  @Test
  public void shouldKeepOtherUserInfo() throws IOException {
    identityService.setUserInfo("alice", "phone", "123");
    loginAs("alice", ENGINE);

    put("{\"a\":1}");
    put("{\"a\":2}");

    assertThat(identityService.getUserInfo("alice", "phone")).isEqualTo("123");
  }

  @Test
  public void shouldNotLetUserReadOrWriteAnotherUsersPreferences() throws IOException {
    loginAs("alice", ENGINE);
    put("{\"lang\":\"vi\"}");

    loginAs("bob", ENGINE);
    assertThat(resource.get(ENGINE).getEntity()).isEqualTo("{}");
    put("{\"lang\":\"en\"}");

    loginAs("alice", ENGINE);
    assertThat(resource.get(ENGINE).getEntity()).isEqualTo("{\"lang\":\"vi\"}");
    loginAs("bob", ENGINE);
    assertThat(resource.get(ENGINE).getEntity()).isEqualTo("{\"lang\":\"en\"}");
  }

  @Test
  public void shouldRejectUnauthenticatedRequests() throws IOException {
    assertThat(resource.get(ENGINE).getStatus()).isEqualTo(401);
    assertThat(put("{}").getStatus()).isEqualTo(401);
  }

  @Test
  public void shouldRejectUserAuthenticatedForAnotherEngine() throws IOException {
    loginAs("alice", "other-engine");

    assertThat(resource.get(ENGINE).getStatus()).isEqualTo(401);
    assertThat(put("{}").getStatus()).isEqualTo(401);
  }

  @Test
  public void shouldRejectTooLargeDocument() throws IOException {
    loginAs("alice", ENGINE);

    Response response = put("{\"a\":\"" + "z".repeat(UserPreferencesResource.MAX_BYTES) + "\"}");

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(identityService.getUserInfoKeys("alice")).isEmpty();
  }

  @Test
  public void shouldRejectInvalidDocuments() throws IOException {
    loginAs("alice", ENGINE);

    assertThat(put("not json").getStatus()).isEqualTo(400);
    assertThat(put("[1,2]").getStatus()).isEqualTo(400);
    assertThat(put("{} garbage").getStatus()).isEqualTo(400);
    assertThat(put("{} []").getStatus()).isEqualTo(400);
    assertThat(put("\"text\"").getStatus()).isEqualTo(400);
    assertThat(put("").getStatus()).isEqualTo(400);
    assertThat(put("{\"bad\\u0001key\":1}").getStatus()).isEqualTo(400);
    assertThat(put("{\"a\":[[[[[[[[[[1]]]]]]]]]]}").getStatus()).isEqualTo(400);
    assertThat(identityService.getUserInfoKeys("alice")).isEmpty();
  }

  @Test
  public void shouldValidateKeysCountAndNormalize() {
    StringBuilder many = new StringBuilder("{");
    for (int i = 0; i <= UserPreferencesResource.MAX_KEYS; i++) {
      many.append(i == 0 ? "" : ",").append("\"k").append(i).append("\":1");
    }
    String tooMany = many.append('}').toString();

    assertThatThrownBy(() -> UserPreferencesResource.validate(tooMany)).isInstanceOf(IllegalArgumentException.class);
    assertThat(UserPreferencesResource.validate(" { \"a\" : [ 1 , true ] } ")).isEqualTo("{\"a\":[1,true]}");
    assertThat(UserPreferencesResource.validate("{\"a\":[[[[[[[[1]]]]]]]]}")).isNotNull();
  }

  protected Response put(String body) throws IOException {
    return resource.put(ENGINE, new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
  }

  protected void loginAs(String user, String engine) {
    Authentications authentications = new Authentications();
    authentications.addOrReplace(new UserAuthentication(user, engine));
    Authentications.setCurrent(authentications);
  }
}
