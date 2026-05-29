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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.httpd.restapi.RestApiServlet.JSON_MAGIC;
import static com.google.gerrit.httpd.restapi.RestApiServlet.SC_UNPROCESSABLE_ENTITY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;
import static javax.servlet.http.HttpServletResponse.SC_CREATED;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_METHOD_NOT_ALLOWED;
import static javax.servlet.http.HttpServletResponse.SC_MOVED_TEMPORARILY;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_NO_CONTENT;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static javax.servlet.http.HttpServletResponse.SC_PRECONDITION_FAILED;

import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.UsedAt.Project;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;

public class RestResponse extends HttpResponse {

  @UsedAt(Project.GOOGLE)
  public RestResponse(org.apache.http.HttpResponse response) {
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
    assertWithMessage(String.format("Expected status code %d", status))
        .that(getStatusCode())
        .isEqualTo(status);
  }

  public void assertOK() throws Exception {
    assertStatus(SC_OK);
  }

  public void assertNotFound() throws Exception {
    assertStatus(SC_NOT_FOUND);
  }

  public void assertConflict() throws Exception {
    assertStatus(SC_CONFLICT);
  }

  public void assertForbidden() throws Exception {
    assertStatus(SC_FORBIDDEN);
  }

  public void assertNoContent() throws Exception {
    assertStatus(SC_NO_CONTENT);
  }

  public void assertBadRequest() throws Exception {
    assertStatus(SC_BAD_REQUEST);
  }

  public void assertUnprocessableEntity() throws Exception {
    assertStatus(SC_UNPROCESSABLE_ENTITY);
  }

  public void assertMethodNotAllowed() throws Exception {
    assertStatus(SC_METHOD_NOT_ALLOWED);
  }

  public void assertCreated() throws Exception {
    assertStatus(SC_CREATED);
  }

  public void assertPreconditionFailed() throws Exception {
    assertStatus(SC_PRECONDITION_FAILED);
  }

  public void assertTemporaryRedirect(String path) throws Exception {
    assertStatus(SC_MOVED_TEMPORARILY);
    assertThat(URI.create(getHeader("Location")).getPath()).isEqualTo(path);
  }

  public void assertTemporaryRedirectUri(String uri) throws Exception {
    assertStatus(SC_MOVED_TEMPORARILY);
    assertThat(getHeader("Location")).isEqualTo(uri);
  }
}
