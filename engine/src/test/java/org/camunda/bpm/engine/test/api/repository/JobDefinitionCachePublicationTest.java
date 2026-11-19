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
package org.camunda.bpm.engine.test.api.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.camunda.bpm.engine.ManagementService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.impl.persistence.deploy.cache.CacheFactory;
import org.camunda.bpm.engine.impl.persistence.deploy.cache.DefaultCacheFactory;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.repository.ProcessDefinition;
import org.camunda.bpm.engine.runtime.Job;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProcessEngineBootstrapRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.commons.utils.cache.Cache;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

/**
 * A process definition that is loaded into the deployment cache is visible to all
 * threads from the moment it is put into the cache. At that moment its job declarations
 * must already be linked to their job definitions, otherwise a thread that creates
 * a job in the meantime, e.g. for a timer boundary event, fails or creates a job
 * without job definition and process definition.
 */
public class JobDefinitionCachePublicationTest {

  protected static final ObservableCacheFactory CACHE_FACTORY = new ObservableCacheFactory();

  @ClassRule
  public static ProcessEngineBootstrapRule bootstrapRule = new ProcessEngineBootstrapRule(
      configuration -> configuration.setCacheFactory(CACHE_FACTORY));

  protected ProcessEngineRule engineRule = new ProvidedProcessEngineRule(bootstrapRule);
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected RuntimeService runtimeService;
  protected ManagementService managementService;

  @Before
  public void setUp() {
    runtimeService = engineRule.getRuntimeService();
    managementService = engineRule.getManagementService();
  }

  @After
  public void tearDown() {
    CACHE_FACTORY.onPut = null;
  }

  @Test
  public void shouldLinkJobDefinitionsBeforeDefinitionIsVisibleInCache() throws Exception {
    // given a deployed process with a timer boundary event that is not in the cache anymore
    BpmnModelInstance process = Bpmn.createExecutableProcess("process")
        .camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("task")
          .boundaryEvent("timer").timerWithDuration("PT1H")
          .endEvent()
        .moveToActivity("task")
        .endEvent()
        .done();

    testRule.deploy(process);
    final ProcessDefinition processDefinition = engineRule.getRepositoryService()
        .createProcessDefinitionQuery()
        .singleResult();

    engineRule.getProcessEngineConfiguration().getDeploymentCache().discardProcessDefinitionCache();

    // and another thread that starts an instance as soon as the definition is visible in the cache
    final AtomicBoolean armed = new AtomicBoolean(true);
    final AtomicReference<Throwable> concurrentFailure = new AtomicReference<>();
    final AtomicBoolean concurrentStartFinished = new AtomicBoolean(false);

    CACHE_FACTORY.onPut = value -> {
      if (value instanceof ProcessDefinitionEntity && armed.compareAndSet(true, false)) {
        Thread concurrentThread = new Thread(() -> {
          try {
            runtimeService.startProcessInstanceById(processDefinition.getId());
          } catch (Throwable t) {
            concurrentFailure.set(t);
          } finally {
            concurrentStartFinished.set(true);
          }
        });
        concurrentThread.start();
        try {
          concurrentThread.join(60000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    };

    // when the definition is loaded into the cache again
    runtimeService.startProcessInstanceById(processDefinition.getId());

    // then
    assertThat(armed.get()).isFalse();
    assertThat(concurrentStartFinished.get()).isTrue();
    assertThat(concurrentFailure.get()).isNull();

    List<Job> jobs = managementService.createJobQuery().list();
    assertThat(jobs).hasSize(2);
    for (Job job : jobs) {
      assertThat(job.getJobDefinitionId()).isNotNull();
      assertThat(job.getProcessDefinitionId()).isEqualTo(processDefinition.getId());
      assertThat(job.getProcessDefinitionKey()).isEqualTo("process");
    }
  }

  public static class ObservableCacheFactory implements CacheFactory {

    protected final CacheFactory delegate = new DefaultCacheFactory();

    public volatile Consumer<Object> onPut;

    @Override
    public <T> Cache<String, T> createCache(int maxNumberOfElementsInCache) {
      final Cache<String, T> cache = delegate.createCache(maxNumberOfElementsInCache);

      return new Cache<String, T>() {

        @Override
        public T get(String key) {
          return cache.get(key);
        }

        @Override
        public void put(String key, T value) {
          cache.put(key, value);

          Consumer<Object> listener = onPut;
          if (listener != null) {
            listener.accept(value);
          }
        }

        @Override
        public void clear() {
          cache.clear();
        }

        @Override
        public void remove(String key) {
          cache.remove(key);
        }

        @Override
        public Set<String> keySet() {
          return cache.keySet();
        }

        @Override
        public int size() {
          return cache.size();
        }

        @Override
        public boolean isEmpty() {
          return cache.isEmpty();
        }
      };
    }
  }

}
