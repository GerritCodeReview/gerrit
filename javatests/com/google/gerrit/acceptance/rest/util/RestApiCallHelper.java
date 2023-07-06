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

package com.google.gerrit.acceptance.rest.util;

import static com.google.common.truth.Truth.assertWithMessage;
import static org.apache.http.HttpStatus.SC_FORBIDDEN;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_METHOD_NOT_ALLOWED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import java.util.List;
import org.junit.Ignore;

/** Helper to execute REST API calls using the HTTP client. */
@Ignore
public class RestApiCallHelper {
  /** See {@link #execute(RestSession, List, BeforeRestCall, String...)} */
  public static void execute(RestSession restSession, List<RestCall> restCalls, String... args)
      throws Exception {
    execute(restSession, restCalls, () -> {}, args);
  }

  /** See {@link #execute(RestSession, RestCall, String...)} */
  public static void execute(
      RestSession restSession,
      List<RestCall> restCalls,
      BeforeRestCall beforeRestCall,
      String... args)
      throws Exception {
    for (RestCall restCall : restCalls) {
      beforeRestCall.run();
      execute(restSession, restCall, args);
    }
  }

  /**
   * This method sends a request to a given REST endpoint and verifies that an implementation is
   * found (no '404 Not Found' response) and that the request doesn't fail (no '500 Internal Server
   * Error' response). It doesn't verify that the REST endpoint works correctly. This is okay since
   * the purpose of the test is only to verify that the REST endpoint implementations are correctly
   * bound.
   */
  public static void execute(RestSession restSession, RestCall restCall, String... args)
      throws Exception {
    String method = restCall.httpMethod().name();
    String uri = restCall.uri(args);

    RestResponse response;
    switch (restCall.httpMethod()) {
      case GET:
        response = restSession.get(uri);
        break;
      case PUT:
        response = restSession.put(uri);
        break;
      case POST:
        response = restSession.post(uri);
        break;
      case DELETE:
        response = restSession.delete(uri);
        break;
      default:
        assertWithMessage(String.format("unsupported method: %s", restCall.httpMethod().name()))
            .fail();
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
          .isNotIn(
              ImmutableList.of(SC_UNAUTHORIZED, SC_FORBIDDEN, SC_NOT_FOUND, SC_METHOD_NOT_ALLOWED));
      assertWithMessage(msg).that(status).isLessThan(SC_INTERNAL_SERVER_ERROR);
    }
  }

  @FunctionalInterface
  public interface BeforeRestCall {
    void run() throws Exception;
  }
}
