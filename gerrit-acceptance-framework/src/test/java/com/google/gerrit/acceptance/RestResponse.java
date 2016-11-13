// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.httpd.restapi.RestApiServlet.JSON_MAGIC;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import org.apache.http.HttpStatus;

public class RestResponse extends HttpResponse {

  RestResponse(org.apache.http.HttpResponse response) {
    super(response);
  }

  @Override
  public Reader getReader() throws IllegalStateException, IOException {
    if (reader == null && response.getEntity() != null) {
      reader = new InputStreamReader(response.getEntity().getContent(), UTF_8);
      reader.skip(JSON_MAGIC.length);
    }
    return reader;
  }

  public void assertStatus(int status) throws Exception {
    assert_()
        .withFailureMessage(String.format("Expected status code %d", status))
        .that(getStatusCode())
        .isEqualTo(status);
  }

  public void assertOK() throws Exception {
    assertStatus(HttpStatus.SC_OK);
  }

  public void assertNotFound() throws Exception {
    assertStatus(HttpStatus.SC_NOT_FOUND);
  }

  public void assertConflict() throws Exception {
    assertStatus(HttpStatus.SC_CONFLICT);
  }

  public void assertForbidden() throws Exception {
    assertStatus(HttpStatus.SC_FORBIDDEN);
  }

  public void assertNoContent() throws Exception {
    assertStatus(HttpStatus.SC_NO_CONTENT);
  }

  public void assertBadRequest() throws Exception {
    assertStatus(HttpStatus.SC_BAD_REQUEST);
  }

  public void assertUnprocessableEntity() throws Exception {
    assertStatus(HttpStatus.SC_UNPROCESSABLE_ENTITY);
  }

  public void assertMethodNotAllowed() throws Exception {
    assertStatus(HttpStatus.SC_METHOD_NOT_ALLOWED);
  }

  public void assertCreated() throws Exception {
    assertStatus(HttpStatus.SC_CREATED);
  }

  public void assertPreconditionFailed() throws Exception {
    assertStatus(HttpStatus.SC_PRECONDITION_FAILED);
  }
}
