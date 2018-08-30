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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.httpd.restapi.ParameterParser;
import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.logging.LoggingContext;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class TraceIT extends AbstractDaemonTest {
  @Inject private DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners;
  @Inject private DynamicSet<CommitValidationListener> commitValidationListeners;

  private TraceValidatingProjectCreationValidationListener projectCreationListener;
  private RegistrationHandle projectCreationListenerRegistrationHandle;
  private TraceValidatingCommitValidationListener commitValidationListener;
  private RegistrationHandle commitValidationRegistrationHandle;

  @Before
  public void setup() {
    projectCreationListener = new TraceValidatingProjectCreationValidationListener();
    projectCreationListenerRegistrationHandle =
        projectCreationValidationListeners.add(projectCreationListener);
    commitValidationListener = new TraceValidatingCommitValidationListener();
    commitValidationRegistrationHandle = commitValidationListeners.add(commitValidationListener);
  }

  @After
  public void cleanup() {
    projectCreationListenerRegistrationHandle.remove();
    commitValidationRegistrationHandle.remove();
  }

  @Test
  public void restCallWithoutTrace() throws Exception {
    RestResponse response = adminRestSession.put("/projects/new1");
    assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
    assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
    assertThat(projectCreationListener.traceId).isNull();
    assertThat(projectCreationListener.foundTraceId).isFalse();
    assertThat(projectCreationListener.isLoggingForced).isFalse();
  }

  @Test
  public void restCallWithTrace() throws Exception {
    RestResponse response =
        adminRestSession.put("/projects/new2?" + ParameterParser.TRACE_PARAMETER);
    assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
    assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNotNull();
    assertThat(projectCreationListener.traceId).isNotNull();
    assertThat(projectCreationListener.foundTraceId).isTrue();
    assertThat(projectCreationListener.isLoggingForced).isTrue();
  }

  @Test
  public void restCallWithTraceAndProvidedTraceId() throws Exception {
    RestResponse response =
        adminRestSession.put("/projects/new3?" + ParameterParser.TRACE_PARAMETER + "=foo");
    assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
    assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNotNull();
    assertThat(projectCreationListener.traceId).isEqualTo("foo");
    assertThat(projectCreationListener.foundTraceId).isTrue();
    assertThat(projectCreationListener.isLoggingForced).isTrue();
  }

  @Test
  public void pushWithoutTrace() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r = push.to("refs/heads/master");
    r.assertOkStatus();
    assertThat(commitValidationListener.traceId).isNull();
    assertThat(commitValidationListener.foundTraceId).isFalse();
    assertThat(commitValidationListener.isLoggingForced).isFalse();
  }

  @Test
  public void pushWithTrace() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    push.setPushOptions(ImmutableList.of("trace"));
    PushOneCommit.Result r = push.to("refs/heads/master");
    r.assertOkStatus();
    assertThat(commitValidationListener.traceId).isNotNull();
    assertThat(commitValidationListener.foundTraceId).isTrue();
    assertThat(commitValidationListener.isLoggingForced).isTrue();
  }

  @Test
  public void pushWithTraceAndProvidedTraceId() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    push.setPushOptions(ImmutableList.of("trace=foo"));
    PushOneCommit.Result r = push.to("refs/heads/master");
    r.assertOkStatus();
    assertThat(commitValidationListener.traceId).isEqualTo("foo");
    assertThat(commitValidationListener.foundTraceId).isTrue();
    assertThat(commitValidationListener.isLoggingForced).isTrue();
  }

  @Test
  public void pushForReviewWithoutTrace() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();
    assertThat(commitValidationListener.traceId).isNull();
    assertThat(commitValidationListener.foundTraceId).isFalse();
    assertThat(commitValidationListener.isLoggingForced).isFalse();
  }

  @Test
  public void pushForReviewWithTrace() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    push.setPushOptions(ImmutableList.of("trace"));
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();
    assertThat(commitValidationListener.traceId).isNotNull();
    assertThat(commitValidationListener.foundTraceId).isTrue();
    assertThat(commitValidationListener.isLoggingForced).isTrue();
  }

  @Test
  public void pushForReviewWithTraceAndProvidedTraceId() throws Exception {
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    push.setPushOptions(ImmutableList.of("trace=foo"));
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();
    assertThat(commitValidationListener.traceId).isEqualTo("foo");
    assertThat(commitValidationListener.foundTraceId).isTrue();
    assertThat(commitValidationListener.isLoggingForced).isTrue();
  }

  private static class TraceValidatingProjectCreationValidationListener
      implements ProjectCreationValidationListener {
    String traceId;
    Boolean foundTraceId;
    Boolean isLoggingForced;

    @Override
    public void validateNewProject(CreateProjectArgs args) throws ValidationException {
      this.traceId =
          Iterables.getFirst(LoggingContext.getInstance().getTagsAsMap().get("TRACE_ID"), null);
      this.foundTraceId = traceId != null;
      this.isLoggingForced = LoggingContext.getInstance().shouldForceLogging(null, null, false);
    }
  }

  private static class TraceValidatingCommitValidationListener implements CommitValidationListener {
    String traceId;
    Boolean foundTraceId;
    Boolean isLoggingForced;

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      this.traceId =
          Iterables.getFirst(LoggingContext.getInstance().getTagsAsMap().get("TRACE_ID"), null);
      this.foundTraceId = traceId != null;
      this.isLoggingForced = LoggingContext.getInstance().shouldForceLogging(null, null, false);
      return ImmutableList.of();
    }
  }
}
