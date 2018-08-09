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

import static com.google.common.truth.Truth.assertThat;
import static org.apache.http.HttpStatus.SC_CREATED;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.httpd.restapi.ParameterParser;
import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.server.logging.LoggingContext;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TraceIT extends AbstractDaemonTest {
  @Inject private DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners;

  private TraceValidatingProjectCreationValidationListener listener;
  private RegistrationHandle registrationHandle;

  @Before
  public void setup() {
    listener = new TraceValidatingProjectCreationValidationListener();
    registrationHandle = projectCreationValidationListeners.add(listener);
  }

  @After
  public void cleanup() {
    registrationHandle.remove();
  }

  @Test
  public void withoutTrace() throws Exception {
    RestResponse response = adminRestSession.put("/projects/new1");
    assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
    assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
    assertThat(listener.foundTraceId).isFalse();
  }

  @Test
  public void withTrace() throws Exception {
    RestResponse response =
        adminRestSession.put("/projects/new2?" + ParameterParser.TRACE_PARAMETER);
    assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
    assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNotNull();
    assertThat(listener.foundTraceId).isTrue();
  }

  @Test
  public void withTraceTrue() throws Exception {
    RestResponse response =
        adminRestSession.put("/projects/new3?" + ParameterParser.TRACE_PARAMETER + "=true");
    assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
    assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNotNull();
    assertThat(listener.foundTraceId).isTrue();
  }

  @Test
  public void withTraceFalse() throws Exception {
    RestResponse response =
        adminRestSession.put("/projects/new4?" + ParameterParser.TRACE_PARAMETER + "=false");
    assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
    assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
    assertThat(listener.foundTraceId).isFalse();
  }

  private static class TraceValidatingProjectCreationValidationListener
      implements ProjectCreationValidationListener {
    Boolean foundTraceId;

    @Override
    public void validateNewProject(CreateProjectArgs args) throws ValidationException {
      this.foundTraceId = LoggingContext.getInstance().getTagsAsMap().containsKey("TRACE_ID");
    }
  }
}
