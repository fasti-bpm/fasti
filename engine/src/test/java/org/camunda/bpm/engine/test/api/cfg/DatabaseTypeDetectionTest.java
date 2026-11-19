/*
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
package org.camunda.bpm.engine.test.api.cfg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.cfg.StandaloneInMemProcessEngineConfiguration;
import org.junit.Test;

/**
 * The database type is derived from the product name the JDBC driver reports.
 */
public class DatabaseTypeDetectionTest {

  @Test
  public void shouldUseMySqlTypeForMariaDbConnector() throws SQLException {
    // MariaDB Connector/J reports "MariaDB"; the server is used with the MySQL dialect
    assertThat(detect("MariaDB", "11.8.3-MariaDB")).isEqualTo("mysql");
  }

  @Test
  public void shouldUseMySqlTypeForMySqlConnector() throws SQLException {
    assertThat(detect("MySQL", "8.4.0")).isEqualTo("mysql");
  }

  @Test
  public void shouldUsePostgresType() throws SQLException {
    assertThat(detect("PostgreSQL", "16.4")).isEqualTo("postgres");
  }

  @Test
  public void shouldRejectUnknownProduct() {
    assertThatThrownBy(() -> detect("SQLite", "3.46.0"))
        .isInstanceOf(ProcessEngineException.class)
        .hasMessageContaining("SQLite");
  }

  protected String detect(String productName, String productVersion) throws SQLException {
    DatabaseMetaData metaData = mock(DatabaseMetaData.class);
    when(metaData.getDatabaseProductName()).thenReturn(productName);
    when(metaData.getDatabaseProductVersion()).thenReturn(productVersion);
    Connection connection = mock(Connection.class);
    when(connection.getMetaData()).thenReturn(metaData);
    DataSource dataSource = mock(DataSource.class);
    when(dataSource.getConnection()).thenReturn(connection);

    StandaloneInMemProcessEngineConfiguration configuration = new StandaloneInMemProcessEngineConfiguration();
    configuration.setDataSource(dataSource);
    configuration.initDatabaseType();
    return configuration.getDatabaseType();
  }
}
