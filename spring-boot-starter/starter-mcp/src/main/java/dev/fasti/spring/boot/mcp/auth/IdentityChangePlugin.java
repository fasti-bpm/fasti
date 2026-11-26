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
package dev.fasti.spring.boot.mcp.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.camunda.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.cfg.TransactionState;
import org.camunda.bpm.engine.impl.cmd.CreateMembershipCmd;
import org.camunda.bpm.engine.impl.cmd.CreateTenantGroupMembershipCmd;
import org.camunda.bpm.engine.impl.cmd.CreateTenantUserMembershipCmd;
import org.camunda.bpm.engine.impl.cmd.DeleteGroupCmd;
import org.camunda.bpm.engine.impl.cmd.DeleteMembershipCmd;
import org.camunda.bpm.engine.impl.cmd.DeleteTenantCmd;
import org.camunda.bpm.engine.impl.cmd.DeleteTenantGroupMembershipCmd;
import org.camunda.bpm.engine.impl.cmd.DeleteTenantUserMembershipCmd;
import org.camunda.bpm.engine.impl.cmd.DeleteUserCmd;
import org.camunda.bpm.engine.impl.cmd.SaveGroupCmd;
import org.camunda.bpm.engine.impl.cmd.SaveTenantCmd;
import org.camunda.bpm.engine.impl.cmd.SaveUserCmd;
import org.camunda.bpm.engine.impl.cmd.UnlockUserCmd;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.interceptor.Command;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.interceptor.CommandInterceptor;

/**
 * Runs an action after a transaction that changed users, groups, tenants or memberships through
 * the identity service of this engine committed, whichever API it came from (REST, web
 * applications, Java). It clears the cache of {@link BasicMcpAuthenticator}.
 */
public class IdentityChangePlugin extends AbstractProcessEnginePlugin {

  protected static final Set<Class<?>> IDENTITY_CHANGES = Set.of(
      SaveUserCmd.class, DeleteUserCmd.class, UnlockUserCmd.class,
      SaveGroupCmd.class, DeleteGroupCmd.class, CreateMembershipCmd.class, DeleteMembershipCmd.class,
      SaveTenantCmd.class, DeleteTenantCmd.class,
      CreateTenantUserMembershipCmd.class, DeleteTenantUserMembershipCmd.class,
      CreateTenantGroupMembershipCmd.class, DeleteTenantGroupMembershipCmd.class);

  protected final Runnable onChange;

  public IdentityChangePlugin(Runnable onChange) {
    this.onChange = onChange;
  }

  @Override
  public void preInit(ProcessEngineConfigurationImpl configuration) {
    List<CommandInterceptor> interceptors = configuration.getCustomPostCommandInterceptorsTxRequired();
    List<CommandInterceptor> extended = interceptors == null ? new ArrayList<>() : new ArrayList<>(interceptors);
    extended.add(new Interceptor());
    configuration.setCustomPostCommandInterceptorsTxRequired(extended);
  }

  /** Runs inside the command context, so it can wait for the commit. */
  protected class Interceptor extends CommandInterceptor {

    @Override
    public <T> T execute(Command<T> command) {
      T result = next.execute(command);
      if (IDENTITY_CHANGES.contains(command.getClass())) {
        CommandContext commandContext = Context.getCommandContext();
        if (commandContext != null) {
          commandContext.getTransactionContext()
              .addTransactionListener(TransactionState.COMMITTED, committed -> onChange.run());
        } else {
          onChange.run();
        }
      }
      return result;
    }
  }
}
