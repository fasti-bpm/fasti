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
package dev.fasti.engine.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.fasti.engine.audit.impl.AuditChain;
import dev.fasti.engine.audit.impl.AuditJson;
import dev.fasti.engine.audit.impl.AuditRecord;
import dev.fasti.engine.audit.impl.AuditSettings;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.Test;

/** Settings, the canonical form and the hashes, without an engine. */
public class AuditSettingsTest {

  static AuditSettings settings(String types, String values, String masked, byte[] key) {
    return new AuditSettings("n", 4, types, values, masked, key, 1000, 365);
  }

  @Test
  public void shouldMaskByCaseInsensitivePatterns() {
    AuditSettings settings = settings(null, null, AuditSettings.DEFAULT_MASKED + ",iban", null);
    assertThat(settings.isMasked("dbPassword")).isTrue();
    assertThat(settings.isMasked("CLIENT_SECRET")).isTrue();
    assertThat(settings.isMasked("accessToken")).isTrue();
    assertThat(settings.isMasked("IBAN")).isTrue();
    assertThat(settings.isMasked("ibanCheck")).isFalse();
    assertThat(settings.isMasked("amount")).isFalse();
    assertThat(settings(null, null, "a?c", null).isMasked("abc")).isTrue();
    assertThat(settings(null, null, "a.c", null).isMasked("abc")).isFalse();
  }

  @Test
  public void shouldParseTypes() {
    assertThat(settings(null, null, null, null).getTypes()).doesNotContain(AuditService.TYPE_ACTIVITY)
        .contains(AuditService.TYPE_PROCESS, AuditService.TYPE_OPERATION, AuditService.TYPE_DEPLOYMENT);
    assertThat(settings("all", null, null, null).getTypes()).contains(AuditService.TYPE_ACTIVITY);
    assertThat(settings(" Task , process ", null, null, null).getTypes()).containsExactly("task", "process");
    assertThatThrownBy(() -> settings("tasks", null, null, null)).isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("tasks");
  }

  @Test
  public void shouldRefuseWrongSettings() {
    assertThatThrownBy(() -> settings(null, "clear", null, null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> settings(null, null, null, "short".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("32 bytes");
    assertThatThrownBy(() -> new AuditSettings("n", 0, null, null, null, null, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new AuditSettings("n", 1, null, null, null, null, -1, 0)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void shouldReadTheKeyFromAFile() throws Exception {
    Path file = Files.createTempFile("fasti-audit", ".key");
    try {
      Files.writeString(file, "  key-from-a-file-that-is-long-enough-for-hmac \n");
      AuditTrailPlugin plugin = new AuditTrailPlugin();
      plugin.setHmacKeyFile(file.toString());
      plugin.preInit(new StandaloneInMemProcessEngineConfiguration());
      AuditTrailPlugin same = new AuditTrailPlugin();
      same.setHmacKey("key-from-a-file-that-is-long-enough-for-hmac");
      same.preInit(new StandaloneInMemProcessEngineConfiguration());
      assertThat(keyId(plugin)).isNotNull().isEqualTo(keyId(same));

      AuditTrailPlugin missing = new AuditTrailPlugin();
      missing.setHmacKeyFile(file + ".missing");
      assertThatThrownBy(() -> missing.preInit(new StandaloneInMemProcessEngineConfiguration()))
          .isInstanceOf(ProcessEngineException.class).hasMessageContaining("cannot be read");
    } finally {
      Files.deleteIfExists(file);
    }
  }

  static String keyId(AuditTrailPlugin plugin) {
    return plugin.settings.getKeyId();
  }

  @Test
  public void shouldTellFieldsApartInTheCanonicalForm() {
    AuditRecord a = record("x\nactor=y", null);
    AuditRecord b = record("x", "y");
    assertThat(AuditChain.canonical(a)).isNotEqualTo(AuditChain.canonical(b));
    // null and empty text are the same (Oracle stores empty text as NULL)
    assertThat(AuditChain.canonical(record("", null))).isEqualTo(AuditChain.canonical(record(null, null)));
    assertThat(AuditChain.hash(a)).hasSize(64).isEqualTo(AuditChain.hash(record("x\nactor=y", null)));
    assertThat(AuditChain.canonical(a)).startsWith(AuditChain.FORMAT + "\n");
  }

  static AuditRecord record(String entityName, String actor) {
    AuditRecord record = new AuditRecord("task", "create");
    record.setId("1");
    record.setChainId("n/0");
    record.setSequence(1);
    record.setPreviousHash(AuditChain.GENESIS);
    record.setTime(1_700_000_000_000L);
    record.setEntityName(entityName);
    record.setActor(actor);
    return record;
  }

  @Test
  public void shouldCutLongTextToTheColumnWithoutSplittingCharacters() {
    AuditRecord record = new AuditRecord("task", "create");
    record.setEntityName("a".repeat(254) + "😀");
    assertThat(record.getEntityName()).hasSize(254);
    record.setBusinessKey("b".repeat(300));
    assertThat(record.getBusinessKey()).hasSize(255);
  }

  @Test
  public void shouldWriteJsonInKeyOrder() {
    String json = AuditJson.object().put("b", "x\"y\n").put("a", 1L).put("c", null).put("d", "").put("e", true).toJson();
    assertThat(json).isEqualTo("{\"a\":1,\"b\":\"x\\\"y\\n\",\"e\":true}");
    assertThat(AuditJson.object().toJson()).isNull();
  }
}
