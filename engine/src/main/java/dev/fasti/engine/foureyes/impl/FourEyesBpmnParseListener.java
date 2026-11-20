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
package dev.fasti.engine.foureyes.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import dev.fasti.engine.foureyes.FourEyesService;
import dev.fasti.engine.foureyes.FourEyesStatus.Step;
import org.camunda.bpm.engine.BpmnParseException;
import org.camunda.bpm.engine.delegate.TaskListener;
import org.camunda.bpm.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.camunda.bpm.engine.impl.bpmn.parser.AbstractBpmnParseListener;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParse;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParseUtil;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.DeploymentEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.process.ScopeImpl;
import org.camunda.bpm.engine.impl.task.TaskDefinition;
import org.camunda.bpm.engine.impl.util.xml.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the extension property {@value FourEyesService#PROPERTY_NAME} of BPMN user tasks. Once the
 * whole process is parsed, the named steps are resolved: each must be a user task of the same
 * process. The guarded task gets its {@link FourEyesRule}; each named step gets the built-in
 * {@code complete} listener that records who completed it.
 *
 * <p>A wrong rule fails a new deployment with a parse error. A process that is already deployed
 * (deployed while the plugin was off, then parsed again into the cache) is not refused, so that
 * its instances keep running: the wrong names are left out and logged.</p>
 */
public class FourEyesBpmnParseListener extends AbstractBpmnParseListener {

  private static final Logger LOG = LoggerFactory.getLogger(FourEyesBpmnParseListener.class);

  /** Property of the parsed process: the rules found while parsing it, not yet resolved. */
  static final String PROPERTY_PENDING = "fasti.fourEyes.pending";

  protected final FourEyesTaskListener listener = new FourEyesTaskListener();

  /** A rule as declared, before the whole process is known. */
  static final class Declared {
    final ActivityImpl activity;
    final Element element;
    final List<String> ids;

    Declared(ActivityImpl activity, Element element, List<String> ids) {
      this.activity = activity;
      this.element = element;
      this.ids = ids;
    }
  }

  @Override
  public void parseUserTask(Element userTaskElement, ScopeImpl scope, ActivityImpl activity) {
    List<String> values = declaredValues(userTaskElement);
    if (values.isEmpty()) {
      return;
    }
    Set<String> ids = new LinkedHashSet<>();
    for (String value : values) {
      for (String part : (value == null ? "" : value).split(",")) {
        String id = part.trim();
        if (!id.isEmpty()) {
          ids.add(id);
        }
      }
    }
    ProcessDefinitionEntity processDefinition = (ProcessDefinitionEntity) activity.getProcessDefinition();
    @SuppressWarnings("unchecked")
    List<Declared> pending = (List<Declared>) processDefinition.getProperty(PROPERTY_PENDING);
    if (pending == null) {
      pending = new ArrayList<>();
      processDefinition.setProperty(PROPERTY_PENDING, pending);
    }
    pending.add(new Declared(activity, userTaskElement, new ArrayList<>(ids)));
  }

  @Override
  public void parseProcess(Element processElement, ProcessDefinitionEntity processDefinition) {
    @SuppressWarnings("unchecked")
    List<Declared> pending = (List<Declared>) processDefinition.getProperty(PROPERTY_PENDING);
    if (pending == null) {
      return;
    }
    processDefinition.setProperty(PROPERTY_PENDING, null);
    boolean newDeployment = isNewDeployment(processDefinition);
    for (Declared declared : pending) {
      resolve(processDefinition, declared, newDeployment);
    }
  }

  protected void resolve(ProcessDefinitionEntity processDefinition, Declared declared, boolean newDeployment) {
    String taskId = declared.activity.getId();
    List<String> problems = new ArrayList<>();
    List<Step> steps = new ArrayList<>();
    List<ActivityImpl> checked = new ArrayList<>();
    if (declared.ids.isEmpty()) {
      problems.add("it names no user task");
    }
    for (String id : declared.ids) {
      ActivityImpl step = processDefinition.findActivity(id);
      if (step == null) {
        problems.add("'" + id + "' is not an element of process '" + processDefinition.getKey() + "'");
      } else if (!(step.getActivityBehavior() instanceof UserTaskActivityBehavior)) {
        problems.add("'" + id + "' is not a user task");
      } else {
        steps.add(new Step(step.getId(), (String) step.getProperty("name")));
        checked.add(step);
      }
    }
    if (!problems.isEmpty()) {
      String message = "The property " + FourEyesService.PROPERTY_NAME + " of user task '" + taskId
          + "' must list the ids of user tasks of the same process, comma separated: " + String.join("; ", problems) + ".";
      if (newDeployment) {
        throw new BpmnParseException(message, declared.element);
      }
      LOG.warn("Process '{}' (deployment {}): {} The four-eyes rule is enforced for the remaining steps only.",
          processDefinition.getKey(), processDefinition.getDeploymentId(), message);
    }
    if (steps.isEmpty()) {
      return;
    }
    declared.activity.setProperty(FourEyesRule.PROPERTY_RULE, new FourEyesRule(steps));
    addListener(declared.activity);
    for (ActivityImpl step : checked) {
      step.setProperty(FourEyesRule.PROPERTY_CHECKED, Boolean.TRUE);
      addListener(step);
    }
  }

  /** Adds the listener once per user task: it records completers and checks rules as needed. */
  protected void addListener(ActivityImpl activity) {
    TaskDefinition taskDefinition = ((UserTaskActivityBehavior) activity.getActivityBehavior()).getTaskDefinition();
    if (taskDefinition == null) {
      return;
    }
    List<TaskListener> listeners = taskDefinition.getBuiltinTaskListeners().get(TaskListener.EVENTNAME_COMPLETE);
    if (listeners == null || !listeners.contains(listener)) {
      taskDefinition.addBuiltInTaskListener(TaskListener.EVENTNAME_COMPLETE, listener);
    }
  }

  /** The values of all {@value FourEyesService#PROPERTY_NAME} properties of the element. */
  static List<String> declaredValues(Element element) {
    List<String> values = new ArrayList<>();
    Element properties = BpmnParseUtil.findCamundaExtensionElement(element, "properties");
    if (properties != null) {
      for (Element property : properties.elementsNS(BpmnParse.CAMUNDA_BPMN_EXTENSIONS_NS, "property")) {
        if (FourEyesService.PROPERTY_NAME.equals(property.attribute("name"))) {
          values.add(property.attribute("value"));
        }
      }
    }
    return values;
  }

  /**
   * Whether the process is parsed for a deployment that is being made now, as opposed to parsed
   * again into the cache: the deployment command marks its new deployment, which is in the cache
   * of the command.
   */
  static boolean isNewDeployment(ProcessDefinitionEntity processDefinition) {
    CommandContext commandContext = Context.getCommandContext();
    if (commandContext == null || processDefinition.getDeploymentId() == null) {
      return false;
    }
    DeploymentEntity deployment = commandContext.getDbEntityManager()
        .getCachedEntity(DeploymentEntity.class, processDefinition.getDeploymentId());
    return deployment != null && deployment.isNew();
  }
}
