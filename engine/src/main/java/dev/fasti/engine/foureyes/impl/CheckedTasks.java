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

import java.util.HashSet;
import java.util.Set;

import org.camunda.bpm.engine.impl.interceptor.Session;
import org.camunda.bpm.engine.impl.interceptor.SessionFactory;

/**
 * The guarded tasks whose completion the command checker has already allowed in the current
 * command context, so that the {@code complete} listener does not check them a second time.
 */
public class CheckedTasks implements Session {

  protected final Set<String> taskIds = new HashSet<>();

  public void add(String taskId) {
    taskIds.add(taskId);
  }

  public boolean contains(String taskId) {
    return taskIds.contains(taskId);
  }

  @Override
  public void flush() {
    // nothing to write
  }

  @Override
  public void close() {
    taskIds.clear();
  }

  /** Opens a {@link CheckedTasks} session per command context. */
  public static class Factory implements SessionFactory {

    @Override
    public Class<?> getSessionType() {
      return CheckedTasks.class;
    }

    @Override
    public Session openSession() {
      return new CheckedTasks();
    }
  }
}
