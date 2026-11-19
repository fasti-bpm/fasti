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
package org.camunda.bpm.engine.test.api.form;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.camunda.bpm.engine.FormService;
import org.camunda.bpm.engine.RuntimeService;
import org.camunda.bpm.engine.TaskService;
import org.camunda.bpm.engine.form.FormField;
import org.camunda.bpm.engine.form.TaskFormData;
import org.camunda.bpm.engine.impl.form.type.DateFormType;
import org.camunda.bpm.engine.runtime.ProcessInstance;
import org.camunda.bpm.engine.test.ProcessEngineRule;
import org.camunda.bpm.engine.test.util.ProcessEngineTestRule;
import org.camunda.bpm.engine.test.util.ProvidedProcessEngineRule;
import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.TypedValue;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

public class DateFormTypeTest {

  protected static final String DATE_PATTERN = "dd/MM/yyyy";

  protected ProcessEngineRule engineRule = new ProvidedProcessEngineRule();
  protected ProcessEngineTestRule testRule = new ProcessEngineTestRule(engineRule);

  @Rule
  public RuleChain ruleChain = RuleChain.outerRule(engineRule).around(testRule);

  protected RuntimeService runtimeService;
  protected TaskService taskService;
  protected FormService formService;

  @Before
  public void setUp() {
    runtimeService = engineRule.getRuntimeService();
    taskService = engineRule.getTaskService();
    formService = engineRule.getFormService();
  }

  @Test
  public void shouldProvideDateOfDefaultValueExpression() {
    // given a date form field with a default value expression that resolves to a date
    testRule.deploy(processWithDateField("${defaultDate}"));

    Date defaultDate = new GregorianCalendar(2021, Calendar.MARCH, 15).getTime();
    runtimeService.startProcessInstanceByKey("process",
        Variables.createVariables().putValue("defaultDate", defaultDate));
    String taskId = taskService.createTaskQuery().singleResult().getId();

    // when
    TaskFormData formData = formService.getTaskFormData(taskId);

    // then
    FormField formField = formData.getFormFields().get(0);
    assertThat(formField.getDefaultValue()).isEqualTo(defaultDate);
    assertThat(formField.getValue().getValue()).isEqualTo("15/03/2021");
  }

  @Test
  public void shouldProvideDateOfDefaultValueConstant() {
    // given
    testRule.deploy(processWithDateField("15/03/2021"));

    runtimeService.startProcessInstanceByKey("process");
    String taskId = taskService.createTaskQuery().singleResult().getId();

    // when
    TaskFormData formData = formService.getTaskFormData(taskId);

    // then
    FormField formField = formData.getFormFields().get(0);
    assertThat(formField.getDefaultValue()).isEqualTo(new GregorianCalendar(2021, Calendar.MARCH, 15).getTime());
    assertThat(formField.getValue().getValue()).isEqualTo("15/03/2021");
  }

  @Test
  public void shouldSubmitDateOfDefaultValueExpression() {
    // given
    testRule.deploy(processWithDateField("${defaultDate}"));

    Date defaultDate = new GregorianCalendar(2021, Calendar.MARCH, 15).getTime();
    ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("process",
        Variables.createVariables().putValue("defaultDate", defaultDate));
    String taskId = taskService.createTaskQuery().singleResult().getId();

    // when the form is submitted without a value for the field
    formService.submitTaskForm(taskId, Variables.createVariables());

    // then
    assertThat(runtimeService.getVariable(processInstance.getId(), "dateField")).isEqualTo(defaultDate);
  }

  @Test
  public void shouldConvertDatesConcurrently() throws Exception {
    // given one form type instance that is shared by all threads, as it is in the engine
    final DateFormType formType = new DateFormType(DATE_PATTERN);

    int numberOfThreads = 8;
    final int conversionsPerThread = 5000;
    final CyclicBarrier startBarrier = new CyclicBarrier(numberOfThreads);
    ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < numberOfThreads; i++) {
        final int threadNumber = i;
        results.add(executorService.submit(new Callable<Integer>() {

          @Override
          public Integer call() throws Exception {
            // every thread has its own format to calculate the expected values
            SimpleDateFormat expectedFormat = new SimpleDateFormat(DATE_PATTERN);
            Calendar calendar = new GregorianCalendar(1990 + threadNumber, Calendar.JANUARY, 1);
            int failures = 0;

            startBarrier.await(30, TimeUnit.SECONDS);

            for (int j = 0; j < conversionsPerThread; j++) {
              calendar.add(Calendar.DAY_OF_YEAR, 1);
              Date date = calendar.getTime();
              String formattedDate = expectedFormat.format(date);

              try {
                TypedValue formValue = formType.convertToFormValue(Variables.dateValue(date));
                TypedValue modelValue = formType.convertToModelValue(Variables.stringValue(formattedDate));

                if (!formattedDate.equals(formValue.getValue()) || !date.equals(modelValue.getValue())) {
                  failures++;
                }
              } catch (Exception e) {
                failures++;
              }
            }
            return failures;
          }
        }));
      }

      // when
      int failures = 0;
      for (Future<Integer> result : results) {
        failures += result.get(2, TimeUnit.MINUTES);
      }

      // then
      assertThat(failures).isZero();
    } finally {
      executorService.shutdownNow();
    }
  }

  protected BpmnModelInstance processWithDateField(String defaultValue) {
    return Bpmn.createExecutableProcess("process")
        .camundaHistoryTimeToLive(180)
        .startEvent()
        .userTask("task")
          .camundaFormField()
            .camundaId("dateField")
            .camundaType("date")
            .camundaDefaultValue(defaultValue)
          .camundaFormFieldDone()
        .userTask("afterTask")
        .endEvent()
        .done();
  }

}
