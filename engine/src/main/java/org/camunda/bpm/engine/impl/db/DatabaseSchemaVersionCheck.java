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
package org.camunda.bpm.engine.impl.db;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.camunda.bpm.engine.impl.ProcessEngineLogger;

/**
 * Compares the schema version recorded in <code>ACT_GE_SCHEMA_LOG</code> with the schema
 * this engine needs.
 * <p>
 * The engine never upgrades a schema by itself: <code>databaseSchemaUpdate=true</code> only
 * creates missing tables, and the version marker in <code>ACT_GE_PROPERTY</code> has been
 * the constant <code>fox</code> for many releases. Without this check an engine starts on
 * an outdated schema and fails later, on the first statement that touches a missing column.
 * <p>
 * Only major and minor version count: patch scripts within a minor version do not change
 * what this engine reads or writes in a way that the check could judge.
 */
public class DatabaseSchemaVersionCheck {

  protected static final EnginePersistenceLogger LOG = ProcessEngineLogger.PERSISTENCE_LOGGER;

  /** the schema line that this engine creates and expects, see database/pom.xml */
  public static final int REQUIRED_MAJOR = 7;
  public static final int REQUIRED_MINOR = 24;
  public static final String REQUIRED_VERSION = REQUIRED_MAJOR + "." + REQUIRED_MINOR;

  protected static final Pattern VERSION = Pattern.compile("^\\s*(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

  public enum Result { MATCHES, OLDER, NEWER, UNKNOWN }

  /**
   * Checks the given schema log versions.
   *
   * @param versions the values of <code>ACT_GE_SCHEMA_LOG.VERSION_</code>; <code>null</code>
   *                 if they could not be read
   * @param enforce whether an older schema is an error or a warning
   * @param databaseType the type of the database, for the message
   * @return the outcome
   * @throws org.camunda.bpm.engine.ProcessEngineException if the schema is older and
   *         <code>enforce</code> is set
   */
  public Result check(List<String> versions, boolean enforce, String databaseType) {
    if (versions == null) {
      LOG.databaseSchemaVersionUnknown("the persistence session does not provide the schema log");
      return Result.UNKNOWN;
    }

    int[] latest = null;
    String latestText = null;
    for (String version : versions) {
      int[] parsed = parse(version);
      if (parsed != null && (latest == null || compare(parsed, latest) > 0)) {
        latest = parsed;
        latestText = version.trim();
      }
    }

    if (latest == null) {
      // the engine tables exist, but no version is recorded: ACT_GE_SCHEMA_LOG was introduced
      // with 7.11, and every create and upgrade script since then writes an entry
      String described = versions.isEmpty()
          ? "unknown (ACT_GE_SCHEMA_LOG is missing or empty, as in schemas created before 7.11)"
          : "unknown (no readable version in ACT_GE_SCHEMA_LOG: " + versions + ")";
      return older(described, enforce, databaseType);
    }

    int cmp = compare(new int[] { latest[0], latest[1], 0 }, new int[] { REQUIRED_MAJOR, REQUIRED_MINOR, 0 });
    if (cmp < 0) {
      return older(latestText, enforce, databaseType);
    } else if (cmp > 0) {
      LOG.databaseSchemaNewer(latestText, REQUIRED_VERSION);
      return Result.NEWER;
    } else {
      LOG.databaseSchemaVersionMatches(latestText);
      return Result.MATCHES;
    }
  }

  protected Result older(String schemaVersion, boolean enforce, String databaseType) {
    if (enforce) {
      throw LOG.databaseSchemaTooOld(schemaVersion, REQUIRED_VERSION, databaseType == null ? "<database>" : databaseType);
    }
    LOG.databaseSchemaTooOldIgnored(schemaVersion, REQUIRED_VERSION);
    return Result.OLDER;
  }

  protected static int[] parse(String version) {
    if (version == null) {
      return null;
    }
    Matcher matcher = VERSION.matcher(version);
    if (!matcher.find()) {
      return null;
    }
    try {
      int patch = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
      return new int[] { Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), patch };
    } catch (NumberFormatException e) {
      return null;
    }
  }

  protected static int compare(int[] a, int[] b) {
    for (int i = 0; i < 3; i++) {
      if (a[i] != b[i]) {
        return Integer.compare(a[i], b[i]);
      }
    }
    return 0;
  }
}
