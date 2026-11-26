/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.IdentityInfoEntity;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class UserPreferencesStoreTest {

  protected static final String USER = "carol";

  @Rule
  public ProcessEngineRule processEngineRule = new ProcessEngineRule("camunda-test-engine.cfg.xml");

  protected ProcessEngine processEngine;
  protected UserPreferencesStore store;

  @Before
  public void setUp() {
    processEngine = processEngineRule.getProcessEngine();
    store = new UserPreferencesStore(processEngine);
  }

  @After
  public void tearDown() {
    execute(commandContext -> {
      for (IdentityInfoEntity row : allRows(commandContext)) {
        commandContext.getIdentityInfoManager().deleteIdentityInfo(row);
      }
      return null;
    });
  }

  @Test
  public void shouldReadDocumentWrittenBy131() {
    String document = "{\"lang\":\"vi\",\"note\":\"" + "Tiếng Việt ".repeat(60) + "\"}";
    insertRows(legacyRows(document));

    assertThat(store.load(USER)).isEqualTo(document);
  }

  @Test
  public void shouldReplaceDocumentWrittenBy131() {
    insertRows(legacyRows("{\"a\":\"" + "x".repeat(1000) + "\"}"));

    store.save(USER, "{\"a\":1}");

    assertThat(store.load(USER)).isEqualTo("{\"a\":1}");
    assertThat(keys()).containsExactlyInAnyOrder(UserPreferencesStore.KEY_PREFIX + "0",
        UserPreferencesStore.COUNT_KEY, UserPreferencesStore.CHECKSUM_KEY, UserPreferencesStore.LOCK_KEY);
  }

  @Test
  public void shouldNotReturnDocumentWhoseChunksComeFromDifferentWrites() {
    store.save(USER, "{\"a\":\"" + "x".repeat(400) + "\"}");
    String other = Base64.getEncoder().encodeToString(
        ("{\"a\":\"" + "y".repeat(400) + "\"}").getBytes(StandardCharsets.UTF_8));
    // the second chunk of another write: valid Base64 and JSON, but not this document
    execute(commandContext -> {
      for (IdentityInfoEntity row : allRows(commandContext)) {
        if ((UserPreferencesStore.KEY_PREFIX + "1").equals(row.getKey())) {
          row.setValue(other.substring(UserPreferencesStore.CHUNK_SIZE, 2 * UserPreferencesStore.CHUNK_SIZE));
        }
      }
      return null;
    });

    assertThat(store.load(USER)).isNull();
  }

  @Test
  public void shouldNotReturnInvalidDocumentOf131() {
    insertRows(List.of(
        new String[] { UserPreferencesStore.KEY_PREFIX + "0",
            Base64.getEncoder().encodeToString("{\"a\":".getBytes(StandardCharsets.UTF_8)) },
        new String[] { UserPreferencesStore.COUNT_KEY, "1" }));

    assertThat(store.load(USER)).isNull();
  }

  @Test
  public void shouldReadAndRepairDuplicatedRows() {
    // two concurrent first saves of 1.3.1, both inserted every row
    String document = "{\"a\":\"" + "x".repeat(600) + "\"}";
    insertRows(legacyRows(document));
    insertRows(legacyRows(document));

    assertThat(store.load(USER)).isEqualTo(document);

    assertThat(keys()).doesNotHaveDuplicates();
    assertThat(store.load(USER)).isEqualTo(document);
  }

  @Test
  public void shouldReadAndRepairDuplicatedRowsOfDifferentDocuments() {
    String first = "{\"a\":\"" + "x".repeat(600) + "\"}";
    String second = "{\"b\":\"" + "y".repeat(300) + "\"}";
    insertRows(legacyRows(first));
    insertRows(legacyRows(second));

    String loaded = store.load(USER);

    assertThat(loaded).isIn(first, second);
    assertThat(keys()).doesNotHaveDuplicates();
    assertThat(store.load(USER)).isEqualTo(loaded);
  }

  @Test
  public void shouldSaveOverDuplicatedRows() {
    insertRows(legacyRows("{\"a\":1}"));
    insertRows(legacyRows("{\"a\":2}"));

    store.save(USER, "{\"a\":3}");

    assertThat(keys()).doesNotHaveDuplicates();
    assertThat(store.load(USER)).isEqualTo("{\"a\":3}");
  }

  @Test
  public void shouldNotDuplicateRowsOnConcurrentFirstSaves() throws Exception {
    saveConcurrently(() -> store);
  }

  @Test
  public void shouldNotDuplicateRowsOnConcurrentFirstSavesOfDifferentNodes() throws Exception {
    // every save on a node of its own: only the database serialises them
    saveConcurrently(() -> new UserPreferencesStore(processEngine) {
      @Override
      protected ReentrantLock localLock(String userId) {
        return new ReentrantLock();
      }
    });
  }

  protected void saveConcurrently(java.util.function.Supplier<UserPreferencesStore> stores) throws Exception {
    int writers = 6;
    CyclicBarrier start = new CyclicBarrier(writers);
    ExecutorService executor = Executors.newFixedThreadPool(writers);
    List<String> documents = new ArrayList<>();
    try {
      List<Future<?>> results = new ArrayList<>();
      for (int i = 0; i < writers; i++) {
        String document = "{\"writer\":" + i + ",\"pad\":\"" + "z".repeat(300 * i) + "\"}";
        documents.add(document);
        UserPreferencesStore writer = stores.get();
        results.add(executor.submit(() -> {
          start.await(10, TimeUnit.SECONDS);
          writer.save(USER, document);
          return null;
        }));
      }
      for (Future<?> result : results) {
        result.get(60, TimeUnit.SECONDS);
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(keys()).doesNotHaveDuplicates();
    String loaded = store.load(USER);
    assertThat(loaded).isIn(documents);
    // and later saves still work
    store.save(USER, "{\"a\":1}");
    assertThat(store.load(USER)).isEqualTo("{\"a\":1}");
    assertThat(keys()).doesNotHaveDuplicates();
  }

  // helpers ////////////////////////////////////////////////////////////////////////////////

  /** The rows 1.3.1 wrote for a document: the chunks and their number. */
  protected static List<String[]> legacyRows(String document) {
    String encoded = Base64.getEncoder().encodeToString(document.getBytes(StandardCharsets.UTF_8));
    List<String[]> rows = new ArrayList<>();
    int count = 0;
    for (int start = 0; start < encoded.length(); start += UserPreferencesStore.CHUNK_SIZE) {
      rows.add(new String[] { UserPreferencesStore.KEY_PREFIX + count++,
          encoded.substring(start, Math.min(encoded.length(), start + UserPreferencesStore.CHUNK_SIZE)) });
    }
    rows.add(new String[] { UserPreferencesStore.COUNT_KEY, Integer.toString(count) });
    return rows;
  }

  protected void insertRows(List<String[]> rows) {
    execute(commandContext -> {
      for (String[] keyAndValue : rows) {
        IdentityInfoEntity row = new IdentityInfoEntity();
        row.setUserId(USER);
        row.setType(IdentityInfoEntity.TYPE_USERINFO);
        row.setKey(keyAndValue[0]);
        row.setValue(keyAndValue[1]);
        commandContext.getDbEntityManager().insert(row);
      }
      return null;
    });
  }

  protected List<String> keys() {
    return execute(commandContext -> allRows(commandContext).stream()
        .map(IdentityInfoEntity::getKey)
        .collect(Collectors.toList()));
  }

  @SuppressWarnings("unchecked")
  protected static List<IdentityInfoEntity> allRows(CommandContext commandContext) {
    return commandContext.getDbEntityManager().selectList("selectIdentityInfoByUserId", USER);
  }

  protected <T> T execute(Function<CommandContext, T> body) {
    ProcessEngineConfigurationImpl configuration =
        (ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration();
    return configuration.getCommandExecutorTxRequired().execute(body::apply);
  }
}
