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

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.camunda.bpm.engine.ProcessEngineException;

/**
 * The canonical form of a record and the hashes over it. The hash of a record is
 * {@code SHA-256(previous hash ‖ "\n" ‖ canonical form)}, hex encoded; the first record of a chain
 * follows {@link #GENESIS}. The canonical form names every field on its own line, with
 * backslashes and line breaks escaped, and tells {@code null} apart from text. It starts with a
 * version line, so that a later format can be told apart.
 */
public final class AuditChain {

  public static final String GENESIS = "0".repeat(64);
  public static final String FORMAT = "fasti-audit-v1";

  private AuditChain() {
  }

  public static String canonical(AuditRecord record) {
    StringBuilder out = new StringBuilder(512);
    out.append(FORMAT).append('\n');
    field(out, "id", record.getId());
    field(out, "chain", record.getChainId());
    field(out, "seq", Long.toString(record.getSequence()));
    field(out, "prev", record.getPreviousHash());
    field(out, "time", Long.toString(record.getTime()));
    field(out, "type", record.getType());
    field(out, "action", record.getAction());
    field(out, "actor", record.getActor());
    field(out, "engine", record.getEngineName());
    field(out, "tenant", record.getTenantId());
    field(out, "procDefKey", record.getProcessDefinitionKey());
    field(out, "procDefId", record.getProcessDefinitionId());
    field(out, "procInst", record.getProcessInstanceId());
    field(out, "businessKey", record.getBusinessKey());
    field(out, "entityType", record.getEntityType());
    field(out, "entityId", record.getEntityId());
    field(out, "entityName", record.getEntityName());
    field(out, "details", record.getDetails());
    return out.toString();
  }

  static void field(StringBuilder out, String name, String value) {
    out.append(name);
    if (value == null) {
      out.append('!');
    } else {
      out.append('=');
      for (int i = 0; i < value.length(); i++) {
        char c = value.charAt(i);
        if (c == '\\') {
          out.append("\\\\");
        } else if (c == '\n') {
          out.append("\\n");
        } else if (c == '\r') {
          out.append("\\r");
        } else {
          out.append(c);
        }
      }
    }
    out.append('\n');
  }

  /** The hash the record must carry, given its previous hash. */
  public static String hash(AuditRecord record) {
    return sha256Hex((record.getPreviousHash() + "\n" + canonical(record)).getBytes(StandardCharsets.UTF_8));
  }

  public static String sha256Hex(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (GeneralSecurityException e) {
      throw new ProcessEngineException("SHA-256 is not available", e);
    }
  }

  public static String sha256Hex(String text) {
    return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
  }

  public static String hmacHex(byte[] key, String text) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new ProcessEngineException("HmacSHA256 is not available", e);
    }
  }

  /** What a checkpoint signs: the chain, the position and hash of the record before the checkpoint. */
  public static String checkpointText(String chainId, long sequence, String previousHash) {
    return FORMAT + "-checkpoint\n" + chainId + "\n" + sequence + "\n" + previousHash;
  }

  /** Compares two hex strings in constant time. */
  public static boolean same(String a, String b) {
    if (a == null || b == null) {
      return a == b;
    }
    return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
  }
}
