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
package dev.fasti.engine.analytics;

/**
 * An analytics statement ran longer than {@link ProcessAnalytics#setStatementTimeout} allows (or
 * the database cancelled it): the range holds more history than can be read in that time. Ask for
 * a shorter range or fewer versions, or raise the timeout. A kind of {@link AnalyticsBusyException},
 * so that callers that handle a busy node handle this too.
 */
public class AnalyticsTimeoutException extends AnalyticsBusyException {

  private static final long serialVersionUID = 1L;

  public AnalyticsTimeoutException(String message, Throwable cause) {
    super(message, cause);
  }
}
