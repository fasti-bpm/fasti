/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Fasti BPM contributors
 */
package org.camunda.bpm.webapp.impl.security.auth;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.webapp.impl.util.ProcessEngineUtil;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The web application preferences of the user logged in to an engine. The
 * path names no user: a user only ever reads and writes their own
 * preferences, taken from the session's authentication.
 *
 * <p>The document is a JSON object of at most {@value #MAX_BYTES} bytes with
 * at most {@value #MAX_KEYS} keys of 1 to 128 characters without control
 * characters; values are any JSON nested at most {@value #MAX_DEPTH} levels.
 */
@Path(UserPreferencesResource.PATH)
public class UserPreferencesResource {

  public static final String PATH = "/auth/user/{processEngineName}/preferences";

  public static final int MAX_BYTES = 32 * 1024;
  public static final int MAX_KEYS = 64;
  public static final int MAX_DEPTH = 8;
  static final Pattern KEY = Pattern.compile("[^\\p{Cntrl}]{1,128}");

  protected static final ObjectMapper MAPPER = new ObjectMapper(JsonFactory.builder()
      .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(MAX_DEPTH + 1).build())
      .build())
      .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response get(@PathParam("processEngineName") String engineName) {
    String userId = currentUser(engineName);
    ProcessEngine engine = userId == null ? null : ProcessEngineUtil.lookupProcessEngine(engineName);
    if (engine == null) {
      return Response.status(Status.UNAUTHORIZED).build();
    }
    String document = new UserPreferencesStore(engine).load(userId);
    return Response.ok(document == null ? "{}" : document, MediaType.APPLICATION_JSON_TYPE)
        .header("Cache-Control", "no-store")
        .build();
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public Response put(@PathParam("processEngineName") String engineName, InputStream body) throws IOException {
    String userId = currentUser(engineName);
    ProcessEngine engine = userId == null ? null : ProcessEngineUtil.lookupProcessEngine(engineName);
    if (engine == null) {
      return Response.status(Status.UNAUTHORIZED).build();
    }
    byte[] bytes = body == null ? new byte[0] : body.readNBytes(MAX_BYTES + 1);
    if (bytes.length > MAX_BYTES) {
      return error(Status.REQUEST_ENTITY_TOO_LARGE, "Preferences are larger than " + MAX_BYTES + " bytes");
    }
    String document;
    try {
      document = validate(new String(bytes, StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) {
      return error(Status.BAD_REQUEST, e.getMessage());
    }
    new UserPreferencesStore(engine).save(userId, document);
    return Response.noContent().build();
  }

  /** Checks a document and returns it normalized; throws if it is not acceptable. */
  static String validate(String text) {
    JsonNode node;
    try {
      node = MAPPER.readTree(text);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Preferences are not valid JSON");
    }
    if (node == null || !node.isObject()) {
      throw new IllegalArgumentException("Preferences must be a JSON object");
    }
    ObjectNode object = (ObjectNode) node;
    if (object.size() > MAX_KEYS) {
      throw new IllegalArgumentException("Preferences have more than " + MAX_KEYS + " keys");
    }
    Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();
      if (!KEY.matcher(field.getKey()).matches()) {
        throw new IllegalArgumentException("Invalid preference key");
      }
      if (depth(field.getValue()) > MAX_DEPTH) {
        throw new IllegalArgumentException("Preference values are nested too deeply");
      }
    }
    String normalized;
    try {
      normalized = MAPPER.writeValueAsString(object);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Preferences are not valid JSON");
    }
    if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new IllegalArgumentException("Preferences are larger than " + MAX_BYTES + " bytes");
    }
    return normalized;
  }

  static int depth(JsonNode node) {
    if (!node.isContainerNode()) {
      return 0;
    }
    int max = 0;
    for (JsonNode child : node) {
      max = Math.max(max, depth(child));
    }
    return max + 1;
  }

  protected String currentUser(String engineName) {
    Authentications authentications = Authentications.getCurrent();
    if (authentications == null) {
      return null;
    }
    Authentication authentication = authentications.getAuthenticationForProcessEngine(engineName);
    return authentication == null ? null : authentication.getIdentityId();
  }

  protected Response error(Status status, String message) {
    return Response.status(status)
        .type(MediaType.APPLICATION_JSON_TYPE)
        .entity(MAPPER.createObjectNode().put("type", "InvalidRequestException").put("message", message).toString())
        .build();
  }
}
