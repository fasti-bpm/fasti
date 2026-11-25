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
package org.camunda.bpm.engine.rest.mapper;

import org.apache.commons.fileupload.FileCountLimitExceededException;
import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.RequestContext;
import org.camunda.bpm.engine.rest.exception.RestException;
import org.camunda.bpm.engine.rest.mapper.MultipartFormData.FormPart;

import javax.ws.rs.Consumes;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Locale;

/**
* <p>Provides a {@link MessageBodyReader} for {@link MultipartFormData}. This allows writing resources which
* consume {@link MediaType#MULTIPART_FORM_DATA} which is parsed into a {@link MultipartFormData} object:</p>
*
* <pre>
* {@literal @}POST
* {@literal @}Consumes(MediaType.MULTIPART_FORM_DATA)
* void handleMultipartPost(MultipartFormData multipartFormData);
* </pre>
*
* <p>The implementation used apache commons fileupload in order to parse the request and populate an instance of
* {@link MultipartFormData}.</p>
*
* <p>Every part is read into memory. To keep a request from exhausting the heap, a request may carry at most
* {@value #DEFAULT_MAX_SIZE_TEXT} and {@value #DEFAULT_MAX_PARTS} parts by default; larger requests are answered
* with {@code 413}. The limits are set with the system properties {@value #MAX_SIZE_PROPERTY} (bytes, or with the
* suffix KB, MB or GB; {@code -1} for no limit) and {@value #MAX_PARTS_PROPERTY}, or the environment variables
* {@value #MAX_SIZE_ENV} and {@value #MAX_PARTS_ENV}.</p>
*
* @author Daniel Meyer
*/
@Provider
@Consumes(MediaType.MULTIPART_FORM_DATA)
public class MultipartPayloadProvider implements MessageBodyReader<MultipartFormData> {

  public static final String TYPE_NAME = "multipart";
  public static final String SUB_TYPE_NAME = "form-data";

  public static final String MAX_SIZE_PROPERTY = "fasti.rest.multipart.max-size";
  public static final String MAX_SIZE_ENV = "FASTI_REST_MULTIPART_MAX_SIZE";
  public static final String MAX_PARTS_PROPERTY = "fasti.rest.multipart.max-parts";
  public static final String MAX_PARTS_ENV = "FASTI_REST_MULTIPART_MAX_PARTS";
  public static final long DEFAULT_MAX_SIZE = 100L * 1024 * 1024;
  static final String DEFAULT_MAX_SIZE_TEXT = "100 MB";
  public static final long DEFAULT_MAX_PARTS = 1000;

  public boolean isReadable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
    return TYPE_NAME.equals(mediaType.getType().toLowerCase())
        && SUB_TYPE_NAME.equals(mediaType.getSubtype().toLowerCase());
  }

  public MultipartFormData readFrom(Class<MultipartFormData> type, Type genericType, Annotation[] annotations, MediaType mediaType,
      MultivaluedMap<String, String> httpHeaders, InputStream entityStream) throws IOException, WebApplicationException {

    final MultipartFormData multipartFormData = createMultipartFormDataInstance();
    final FileUpload fileUpload = createFileUploadInstance();

    String contentType = httpHeaders.getFirst("content-type");
    RestMultipartRequestContext requestContext = createRequestContext(entityStream, contentType);

    // parse the request (populates the multipartFormData)
    parseRequest(multipartFormData, fileUpload, requestContext);

    return multipartFormData;

  }

  protected FileUpload createFileUploadInstance() {
    FileUpload fileUpload = new FileUpload();
    fileUpload.setSizeMax(configuredLimit(MAX_SIZE_PROPERTY, MAX_SIZE_ENV, DEFAULT_MAX_SIZE));
    fileUpload.setFileCountMax(configuredLimit(MAX_PARTS_PROPERTY, MAX_PARTS_ENV, DEFAULT_MAX_PARTS));
    return fileUpload;
  }

  /** The limit from the system property, else the environment variable, else the default; -1 means none. */
  protected long configuredLimit(String property, String environmentVariable, long defaultValue) {
    String value = System.getProperty(property);
    if (value == null || value.trim().isEmpty()) {
      value = System.getenv(environmentVariable);
    }
    if (value == null || value.trim().isEmpty()) {
      return defaultValue;
    }
    try {
      return parseSize(value);
    } catch (NumberFormatException | ArithmeticException e) {
      return defaultValue;
    }
  }

  static long parseSize(String value) {
    String text = value.trim().toUpperCase(Locale.ROOT).replace(" ", "");
    long factor = 1;
    if (text.endsWith("KB")) {
      factor = 1024;
    } else if (text.endsWith("MB")) {
      factor = 1024 * 1024;
    } else if (text.endsWith("GB")) {
      factor = 1024L * 1024 * 1024;
    }
    if (factor > 1) {
      text = text.substring(0, text.length() - 2);
    }
    long number = Long.parseLong(text);
    // an overflow would turn the limit negative, that is off: refused as invalid instead
    return number < 0 ? -1 : Math.multiplyExact(number, factor);
  }

  protected MultipartFormData createMultipartFormDataInstance() {
    return new MultipartFormData();
  }

  protected void parseRequest(MultipartFormData multipartFormData, FileUpload fileUpload, RestMultipartRequestContext requestContext) {
    try {
      FileItemIterator itemIterator = fileUpload.getItemIterator(requestContext);
      long maxParts = fileUpload.getFileCountMax();
      long parts = 0;
      while (itemIterator.hasNext()) {
        // the streaming API of commons fileupload does not count the parts itself
        if (maxParts >= 0 && ++parts > maxParts) {
          throw new FileCountLimitExceededException("multipart/form-data", maxParts);
        }
        FileItemStream stream = itemIterator.next();
        multipartFormData.addPart(new FormPart(stream));
      }
    } catch (FileUploadBase.SizeLimitExceededException | FileUploadBase.FileSizeLimitExceededException
        | FileCountLimitExceededException e) {
      throw new RestException(Status.REQUEST_ENTITY_TOO_LARGE, e, "multipart/form-data exceeds the configured limit: " + e.getMessage());
    } catch (Exception e) {
      // reading a part wraps the limit in further exceptions
      for (Throwable cause = e.getCause(); cause != null && cause != cause.getCause(); cause = cause.getCause()) {
        if (cause instanceof FileUploadBase.SizeLimitExceededException
            || cause instanceof FileUploadBase.FileSizeLimitExceededException) {
          throw new RestException(Status.REQUEST_ENTITY_TOO_LARGE, e, "multipart/form-data exceeds the configured limit");
        }
      }
      throw new RestException(Status.BAD_REQUEST, e, "multipart/form-data cannot be processed");

    }
  }

  protected RestMultipartRequestContext createRequestContext(InputStream entityStream, String contentType) {
    return new RestMultipartRequestContext(entityStream, contentType);
  }

  /**
   * Exposes the REST request to commons fileupload
   *
   */
  static class RestMultipartRequestContext implements RequestContext {

    protected InputStream inputStream;
    protected String contentType;

    public RestMultipartRequestContext(InputStream inputStream, String contentType) {
      this.inputStream = inputStream;
      this.contentType = contentType;
    }

    public String getCharacterEncoding() {
      return null;
    }

    public String getContentType() {
      return contentType;
    }

    public int getContentLength() {
      return -1;
    }

    public InputStream getInputStream() throws IOException {
      return inputStream;
    }

  }

}
