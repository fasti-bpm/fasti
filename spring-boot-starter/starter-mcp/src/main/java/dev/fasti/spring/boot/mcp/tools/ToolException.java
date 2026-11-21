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
package dev.fasti.spring.boot.mcp.tools;

/**
 * A tool call that cannot be carried out, with a message meant for the caller (invalid argument,
 * nothing found or not visible to the user).
 */
public class ToolException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public ToolException(String message) {
    super(message);
  }

  /** Nothing with this id exists or the user may not read it; the two are not told apart. */
  public static ToolException notFound(String what, String id) {
    return new ToolException(what + " '" + id + "' was not found or you are not allowed to read it");
  }
}
