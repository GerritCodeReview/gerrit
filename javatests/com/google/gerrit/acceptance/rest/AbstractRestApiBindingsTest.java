// Copyright (C) 2018 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.acceptance.rest;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang.StringUtils;
import org.junit.Ignore;

/**
 * Base class for testing the REST API bindings.
 *
 * <p>This test sends a request to each REST endpoint and verifies that an implementation is found
 * (no '404 Not Found' response) and that the request doesn't fail (no '500 Internal Server Error'
 * response). It doesn't verify that the REST endpoint works correctly. This is okay since the
 * purpose of the test is only to verify that the REST endpoint implementations are correctly bound.
 */
@Ignore
public abstract class AbstractRestApiBindingsTest extends AbstractDaemonTest {
  protected void execute(List<RestCall> restCalls, String... args) throws Exception {
    execute(restCalls, () -> {}, args);
  }

  protected void execute(List<RestCall> restCalls, BeforeRestCall beforeRestCall, String... args)
      throws Exception {
    for (RestCall restCall : restCalls) {
      beforeRestCall.run();
      execute(restCall, args);
    }
  }

  protected void execute(RestCall restCall, String... args) throws Exception {
    String method = restCall.httpMethod().name();
    String uri = restCall.uri(args);

    RestResponse response;
    switch (restCall.httpMethod()) {
      case GET:
        response = adminRestSession.get(uri);
        break;
      case PUT:
        response = adminRestSession.put(uri);
        break;
      case POST:
        response = adminRestSession.post(uri);
        break;
      case DELETE:
        response = adminRestSession.delete(uri);
        break;
      default:
        fail("unsupported method: %s", restCall.httpMethod().name());
        throw new IllegalStateException();
    }

    int status = response.getStatusCode();
    String body = response.hasContent() ? response.getEntityContent() : "";

    String msg = String.format("%s %s returned %d: %s", method, uri, status, body);
    if (restCall.expectedResponseCode().isPresent()) {
      assertWithMessage(msg).that(status).isEqualTo(restCall.expectedResponseCode().get());
      if (restCall.expectedMessage().isPresent()) {
        assertWithMessage(msg).that(body).contains(restCall.expectedMessage().get());
      }
    } else {
      assertWithMessage(msg)
          .that(status)
          .isNotIn(ImmutableList.of(SC_FORBIDDEN, SC_NOT_FOUND, SC_METHOD_NOT_ALLOWED));
      assertWithMessage(msg).that(status).isLessThan(SC_INTERNAL_SERVER_ERROR);
    }
  }

  enum Method {
    GET,
    PUT,
    POST,
    DELETE
  }

  @AutoValue
  abstract static class RestCall {
    static RestCall get(String uriFormat) {
      return builder(Method.GET, uriFormat).build();
    }

    static RestCall put(String uriFormat) {
      return builder(Method.PUT, uriFormat).build();
    }

    static RestCall post(String uriFormat) {
      return builder(Method.POST, uriFormat).build();
    }

    static RestCall delete(String uriFormat) {
      return builder(Method.DELETE, uriFormat).build();
    }

    static Builder builder(Method httpMethod, String uriFormat) {
      return new AutoValue_AbstractRestApiBindingsTest_RestCall.Builder()
          .httpMethod(httpMethod)
          .uriFormat(uriFormat);
    }

    abstract Method httpMethod();

    abstract String uriFormat();

    abstract Optional<Integer> expectedResponseCode();

    abstract Optional<String> expectedMessage();

    String uri(String... args) {
      String uriFormat = uriFormat();
      int expectedArgNum = StringUtils.countMatches(uriFormat, "%s");
      checkState(
          args.length == expectedArgNum,
          "uriFormat %s needs %s arguments, got only %s: %s",
          uriFormat,
          expectedArgNum,
          args.length,
          args);
      return String.format(uriFormat, (Object[]) args);
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder httpMethod(Method httpMethod);

      abstract Builder uriFormat(String uriFormat);

      abstract Builder expectedResponseCode(int expectedResponseCode);

      abstract Builder expectedMessage(String expectedMessage);

      abstract RestCall build();
    }
  }

  @FunctionalInterface
  public interface BeforeRestCall {
    void run() throws Exception;
  }
}
