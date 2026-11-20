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
package org.camunda.bpm.engine.impl.db.sql;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.camunda.bpm.engine.impl.ProcessEngineLogger;
import org.camunda.bpm.engine.impl.QueryOrderingProperty;
import org.camunda.bpm.engine.impl.db.EnginePersistenceLogger;
import org.camunda.bpm.engine.query.QueryProperty;

/**
 * @author Thorben Lindhauer
 */
public class MybatisJoinHelper {

  protected static final EnginePersistenceLogger LOG = ProcessEngineLogger.PERSISTENCE_LOGGER;
  protected static final String DEFAULT_ORDER = "RES.ID_ asc";
  public static Map<String, MyBatisTableMapping> mappings = new HashMap<String, MyBatisTableMapping>();

  static {
    mappings.put(QueryOrderingProperty.RELATION_VARIABLE, new VariableTableMapping());
    mappings.put(QueryOrderingProperty.RELATION_PROCESS_DEFINITION, new ProcessDefinitionTableMapping());
    mappings.put(QueryOrderingProperty.RELATION_CASE_DEFINITION, new CaseDefinitionTableMapping());
    mappings.put(QueryOrderingProperty.RELATION_DEPLOYMENT, new DeploymentTableMapping());
  }

  public static String tableAlias(String relation, int index) {
    if (relation == null) {
      return "RES";
    } else {
      MyBatisTableMapping mapping = getTableMapping(relation);

      if (mapping.isOneToOneRelation()) {
        return mapping.getTableAlias();
      } else {
        return mapping.getTableAlias() + index;
      }
    }
  }

  public static String tableMapping(String relation) {
    MyBatisTableMapping mapping = getTableMapping(relation);

    return mapping.getTableName();
  }

  public static String orderBySelection(QueryOrderingProperty orderingProperty, int index) {
    QueryProperty queryProperty = orderingProperty.getQueryProperty();

    StringBuilder sb = new StringBuilder();

    if (queryProperty.getFunction() != null) {
      sb.append(queryProperty.getFunction());
      sb.append("(");
    }

    sb.append(tableAlias(orderingProperty.getRelation(), index));
    sb.append(".");
    sb.append(queryProperty.getName());

    if (queryProperty.getFunction() != null) {
      sb.append(")");
    }

    return sb.toString();
  }

  public static String orderBy(QueryOrderingProperty orderingProperty, int index) {
    QueryProperty queryProperty = orderingProperty.getQueryProperty();

    StringBuilder sb = new StringBuilder();

    sb.append(tableAlias(orderingProperty.getRelation(), index));
    if (orderingProperty.isContainedProperty()) {
      sb.append(".");
    } else {
      sb.append("_");
    }
    sb.append(queryProperty.getName());

    sb.append(" ");

    sb.append(orderingProperty.getDirection().getName());

    return sb.toString();

  }

  /**
   * Returns the complete ORDER BY list for the given ordering properties, or the default
   * order if there are none. The mappings compute it in one expression instead of
   * accumulating it over the iterations of a {@code <foreach>}: since MyBatis 3.6 a
   * {@code <bind>} inside a {@code <foreach>} is only visible within its iteration.
   */
  public static String orderBy(List<QueryOrderingProperty> orderingProperties) {
    if (orderingProperties == null || orderingProperties.isEmpty()) {
      return DEFAULT_ORDER;
    }

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < orderingProperties.size(); i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(orderBy(orderingProperties.get(i), i));
    }

    return sb.toString();
  }

  protected static MyBatisTableMapping getTableMapping(String relation) {
    MyBatisTableMapping mapping = mappings.get(relation);

    if (mapping == null) {
      throw LOG.missingRelationMappingException(relation);
    }

    return mapping;
  }
}
