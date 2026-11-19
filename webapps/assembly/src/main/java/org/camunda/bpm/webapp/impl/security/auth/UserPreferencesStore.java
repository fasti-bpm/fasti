/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.security.auth;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.persistence.entity.IdentityInfoEntity;
import org.camunda.bpm.engine.impl.persistence.entity.IdentityInfoManager;

/**
 * Keeps the web application preferences of a user in the engine's user info
 * (ACT_ID_INFO), so they need no schema change and work with read-only
 * identity providers (LDAP) too: user info always lives in the engine
 * database, whatever provider supplies the users.
 *
 * <p>A user info value holds at most 255 characters, so the document is
 * stored Base64 encoded (plain ASCII, safe for byte- and character-sized
 * columns alike) in numbered chunks. A read or a write runs in one
 * transaction, so a reader never sees a half-written document.
 */
public class UserPreferencesStore {

  public static final String KEY_PREFIX = "fasti.prefs.";
  public static final String COUNT_KEY = KEY_PREFIX + "chunks";
  static final int CHUNK_SIZE = 255;

  protected final ProcessEngine processEngine;

  public UserPreferencesStore(ProcessEngine processEngine) {
    this.processEngine = processEngine;
  }

  /** The stored document of the user, or {@code null} if there is none. */
  public String load(String userId) {
    return execute(commandContext -> {
      IdentityInfoManager manager = commandContext.getIdentityInfoManager();
      IdentityInfoEntity count = manager.findUserInfoByUserIdAndKey(userId, COUNT_KEY);
      if (count == null) {
        return null;
      }
      int chunks;
      try {
        chunks = Integer.parseInt(count.getValue());
      } catch (NumberFormatException e) {
        return null;
      }
      StringBuilder encoded = new StringBuilder();
      for (int i = 0; i < chunks; i++) {
        IdentityInfoEntity chunk = manager.findUserInfoByUserIdAndKey(userId, KEY_PREFIX + i);
        if (chunk == null || chunk.getValue() == null) {
          return null;
        }
        encoded.append(chunk.getValue());
      }
      try {
        return new String(Base64.getDecoder().decode(encoded.toString()), StandardCharsets.UTF_8);
      } catch (IllegalArgumentException e) {
        return null;
      }
    });
  }

  /** Replaces the stored document of the user. */
  public void save(String userId, String document) {
    String encoded = Base64.getEncoder().encodeToString(document.getBytes(StandardCharsets.UTF_8));
    List<String> chunks = new ArrayList<>();
    for (int start = 0; start < encoded.length(); start += CHUNK_SIZE) {
      chunks.add(encoded.substring(start, Math.min(encoded.length(), start + CHUNK_SIZE)));
    }
    execute(commandContext -> {
      IdentityInfoManager manager = commandContext.getIdentityInfoManager();
      // existing entries are updated in place: a delete and an insert of the
      // same key in one transaction would leave only the delete
      for (int i = 0; i < chunks.size(); i++) {
        manager.setUserInfo(userId, null, IdentityInfoEntity.TYPE_USERINFO, KEY_PREFIX + i, chunks.get(i), null, null);
      }
      manager.setUserInfo(userId, null, IdentityInfoEntity.TYPE_USERINFO, COUNT_KEY,
          Integer.toString(chunks.size()), null, null);
      deleteSurplusChunks(manager, userId, chunks.size());
      return null;
    });
  }

  protected void deleteSurplusChunks(IdentityInfoManager manager, String userId, int count) {
    for (String key : manager.findUserInfoKeysByUserIdAndType(userId, IdentityInfoEntity.TYPE_USERINFO)) {
      if (key.startsWith(KEY_PREFIX) && !COUNT_KEY.equals(key)) {
        String index = key.substring(KEY_PREFIX.length());
        if (!index.matches("\\d{1,6}") || Integer.parseInt(index) >= count) {
          manager.deleteUserInfoByUserIdAndKey(userId, key);
        }
      }
    }
  }

  protected <T> T execute(Function<CommandContext, T> body) {
    ProcessEngineConfigurationImpl configuration =
        (ProcessEngineConfigurationImpl) processEngine.getProcessEngineConfiguration();
    return configuration.getCommandExecutorTxRequired().execute(body::apply);
  }
}
