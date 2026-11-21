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
package dev.fasti.engine.audit.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;

import dev.fasti.engine.audit.AuditService;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.db.entitymanager.DbEntityManager;
import org.camunda.bpm.engine.impl.db.entitymanager.cache.DbEntityCache;
import org.camunda.bpm.engine.impl.db.entitymanager.operation.DbBulkOperation;
import org.camunda.bpm.engine.impl.db.entitymanager.operation.DbOperationManager;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;
import org.camunda.bpm.engine.impl.persistence.entity.DeploymentEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ResourceEntity;

/**
 * Opens the audit session of a command before the command runs (see {@link AuditSession#open}) and
 * records deployments, which produce no history event: after each command, a deployment the
 * command created or deleted (as the entity cache shows before it is flushed) becomes an audit
 * record, with the SHA-256 of every resource of a new deployment. Deploying unchanged resources
 * with duplicate filtering creates nothing and records nothing.
 */
public class AuditDeploymentInterceptor extends CommandInterceptor {

  protected final AuditSettings settings;

  public AuditDeploymentInterceptor(AuditSettings settings) {
    this.settings = settings;
  }

  @Override
  public <T> T execute(Command<T> command) {
    CommandContext commandContext = Context.getCommandContext();
    if (commandContext != null) {
      // before the engine opens its sessions: the records are then written after its statements
      AuditSession.open(commandContext);
    }
    T result = next.execute(command);
    if (commandContext != null && settings.isRecorded(AuditService.TYPE_DEPLOYMENT)) {
      record(commandContext);
    }
    return result;
  }

  protected void record(CommandContext commandContext) {
    if (!commandContext.getSessions().containsKey(DbEntityManager.class)) {
      // the command touched no entity
      return;
    }
    DbEntityManager entityManager = commandContext.getDbEntityManager();
    DbEntityCache cache = entityManager.getDbEntityCache();
    DbOperationManager operations = entityManager.getDbOperationManager();
    if (cache.getEntitiesByType(DeploymentEntity.class).isEmpty() && operations.bulkOperationsInsertionOrder.isEmpty()
        && !operations.bulkOperations.containsKey(DeploymentEntity.class)) {
      // as after most commands: no deployment in the cache and no bulk statement
      return;
    }
    Map<String, DeploymentEntity> created = new LinkedHashMap<>();
    Map<String, DeploymentEntity> deleted = new LinkedHashMap<>();
    for (DeploymentEntity deployment : cache.getEntitiesByType(DeploymentEntity.class)) {
      if (cache.isTransient(deployment)) {
        created.put(deployment.getId(), deployment);
      } else if (cache.isDeleted(deployment)) {
        deleted.put(deployment.getId(), deployment);
      }
    }
    // a deployment is deleted by a bulk statement, with its entity (if loaded) left in the cache
    List<DbBulkOperation> bulk = new ArrayList<>(operations.bulkOperationsInsertionOrder);
    SortedSet<DbBulkOperation> deploymentBulk = operations.bulkOperations.get(DeploymentEntity.class);
    if (deploymentBulk != null) {
      bulk.addAll(deploymentBulk);
    }
    for (DbBulkOperation operation : bulk) {
      if (DeploymentEntity.class.equals(operation.getEntityType()) && "deleteDeployment".equals(operation.getStatement())
          && operation.getParameter() instanceof String) {
        String id = (String) operation.getParameter();
        deleted.putIfAbsent(id, entityManager.getCachedEntity(DeploymentEntity.class, id));
      }
    }
    if (created.isEmpty() && deleted.isEmpty()) {
      return;
    }
    AuditSession session = AuditHistoryEventHandler.session(commandContext);
    for (DeploymentEntity deployment : created.values()) {
      if (!session.isDeploymentRecorded(deployment.getId() + ":create")) {
        AuditRecord record = record("create", deployment.getId(), deployment);
        Map<String, String> resources = new TreeMap<>();
        if (deployment.getResources() != null) {
          for (ResourceEntity resource : deployment.getResources().values()) {
            resources.put(resource.getName(), resource.getBytes() == null ? null : AuditChain.sha256Hex(resource.getBytes()));
          }
        }
        record.setDetails(AuditJson.object().put("source", deployment.getSource()).put("resources", resources).toJson());
        session.add(record, true);
      }
    }
    for (Map.Entry<String, DeploymentEntity> entry : deleted.entrySet()) {
      if (!session.isDeploymentRecorded(entry.getKey() + ":delete")) {
        AuditRecord record = record("delete", entry.getKey(), entry.getValue());
        record.setDetails(AuditJson.object().put("source", entry.getValue() == null ? null : entry.getValue().getSource()).toJson());
        session.add(record, true);
      }
    }
  }

  static AuditRecord record(String action, String id, DeploymentEntity deployment) {
    AuditRecord record = new AuditRecord(AuditService.TYPE_DEPLOYMENT, action);
    record.setEntityType("deployment");
    record.setEntityId(id);
    if (deployment != null) {
      record.setTenantId(deployment.getTenantId());
      record.setEntityName(deployment.getName());
    }
    return record;
  }
}
