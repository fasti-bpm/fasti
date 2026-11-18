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
package org.camunda.bpm.engine.impl;

import java.util.List;

import org.camunda.bpm.engine.ProcessEngineConfiguration;
import org.camunda.bpm.engine.SchemaOperationsCommand;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.db.AbstractPersistenceSession;
import org.camunda.bpm.engine.impl.db.DatabaseSchemaVersionCheck;
import org.camunda.bpm.engine.impl.db.EnginePersistenceLogger;
import org.camunda.bpm.engine.impl.db.PersistenceSession;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.PropertyEntity;

/**
 * @author Tom Baeyens
 * @author Roman Smirnov
 * @author Sebastian Menski
 * @author Daniel Meyer
 */
public class SchemaOperationsProcessEngineBuild implements SchemaOperationsCommand {

  private final static EnginePersistenceLogger LOG = ProcessEngineLogger.PERSISTENCE_LOGGER;

  public Void execute(CommandContext commandContext) {
    String databaseSchemaUpdate = Context.getProcessEngineConfiguration().getDatabaseSchemaUpdate();
    PersistenceSession persistenceSession = commandContext.getSession(PersistenceSession.class);
    if (ProcessEngineConfigurationImpl.DB_SCHEMA_UPDATE_DROP_CREATE.equals(databaseSchemaUpdate)) {
      try {
        persistenceSession.dbSchemaDrop();
      } catch (RuntimeException e) {
        // ignore
      }
    }
    if ( ProcessEngineConfiguration.DB_SCHEMA_UPDATE_CREATE_DROP.equals(databaseSchemaUpdate)
      || ProcessEngineConfigurationImpl.DB_SCHEMA_UPDATE_DROP_CREATE.equals(databaseSchemaUpdate)
      || ProcessEngineConfigurationImpl.DB_SCHEMA_UPDATE_CREATE.equals(databaseSchemaUpdate)
      ) {
      persistenceSession.dbSchemaCreate();
    } else if (ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE.equals(databaseSchemaUpdate)) {
      persistenceSession.dbSchemaCheckVersion();
      checkSchemaVersion(persistenceSession);
    } else if (ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE.equals(databaseSchemaUpdate)) {
      // check an existing schema before anything is created in it: creating the tables that
      // are missing in an outdated schema would leave it half upgraded
      boolean existing = !(persistenceSession instanceof AbstractPersistenceSession)
          || ((AbstractPersistenceSession) persistenceSession).isEngineTablePresent();
      if (existing) {
        checkSchemaVersion(persistenceSession);
      }
      persistenceSession.dbSchemaUpdate();
    }

    return null;
  }

  protected void checkSchemaVersion(PersistenceSession persistenceSession) {
    ProcessEngineConfigurationImpl configuration = Context.getProcessEngineConfiguration();
    List<String> versions;
    try {
      versions = persistenceSession.getSchemaLogVersions();
    } catch (RuntimeException e) {
      // for example no permission to read the table: report, but do not decide on a guess
      LOG.databaseSchemaVersionUnknown(e.getMessage());
      return;
    }
    new DatabaseSchemaVersionCheck().check(
        versions,
        configuration.isEnforceDatabaseSchemaVersion(),
        configuration.getDatabaseType());
  }
}
