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
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.truth.Expect;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.httpd.restapi.ParameterParser;
import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.server.ExceptionHook;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.logging.LoggingContext;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.PerformanceLogger;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.SortedSet;
import org.apache.http.message.BasicHeader;
import org.junit.Rule;
import org.junit.Test;

/**
 * This test tests the tracing of requests.
 *
 * <p>To verify that tracing is working we do:
 *
 * <ul>
 *   <li>Register a plugin extension that we know is invoked when the request is done. Within the
 *       implementation of this plugin extension we access the status of the thread local state in
 *       the {@link LoggingContext} and store it locally in the plugin extension class.
 *   <li>Do a request (e.g. REST) that triggers the plugin extension.
 *   <li>When the plugin extension is invoked it records the current logging context.
 *   <li>After the request is done the test verifies that logging context that was recorded by the
 *       plugin extension has the expected state.
 * </ul>
 */
public class TraceIT extends AbstractDaemonTest {
  @Rule public final Expect expect = Expect.create();

  @Inject private ExtensionRegistry extensionRegistry;
  @Inject private WorkQueue workQueue;

  @Test
  public void restCallWithoutTrace() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new1");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isNull();
      assertThat(projectCreationListener.isLoggingForced).isFalse();

      // The logging tag with the project name is also set if tracing is off.
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new1");
    }
  }

  @Test
  public void restCallForChangeSetsProjectTag() throws Exception {
    String changeId = createChange().getChangeId();

    TraceChangeIndexedListener changeIndexedListener = new TraceChangeIndexedListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedListener)) {
      RestResponse response =
          adminRestSession.post(
              "/changes/" + changeId + "/revisions/current/review", ReviewInput.approve());
      assertThat(response.getStatusCode()).isEqualTo(SC_OK);

      // The logging tag with the project name is also set if tracing is off.
      assertThat(changeIndexedListener.tags.get("project")).containsExactly(project.get());
    }
  }

  @Test
  public void restCallWithTraceRequestParam() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response =
          adminRestSession.put("/projects/new2?" + ParameterParser.TRACE_PARAMETER);
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNotNull();
      assertThat(projectCreationListener.traceId).isNotNull();
      assertThat(projectCreationListener.isLoggingForced).isTrue();
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new2");
    }
  }

  @Test
  public void restCallWithTraceRequestParamAndProvidedTraceId() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response =
          adminRestSession.put("/projects/new3?" + ParameterParser.TRACE_PARAMETER + "=issue/123");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isEqualTo("issue/123");
      assertThat(projectCreationListener.traceId).isEqualTo("issue/123");
      assertThat(projectCreationListener.isLoggingForced).isTrue();
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new3");
    }
  }

  @Test
  public void restCallWithTraceHeader() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response =
          adminRestSession.putWithHeader(
              "/projects/new4", new BasicHeader(RestApiServlet.X_GERRIT_TRACE, null));
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNotNull();
      assertThat(projectCreationListener.traceId).isNotNull();
      assertThat(projectCreationListener.isLoggingForced).isTrue();
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new4");
    }
  }

  @Test
  public void restCallWithTraceHeaderAndProvidedTraceId() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response =
          adminRestSession.putWithHeader(
              "/projects/new5", new BasicHeader(RestApiServlet.X_GERRIT_TRACE, "issue/123"));
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isEqualTo("issue/123");
      assertThat(projectCreationListener.traceId).isEqualTo("issue/123");
      assertThat(projectCreationListener.isLoggingForced).isTrue();
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new5");
    }
  }

  @Test
  public void restCallWithTraceRequestParamAndTraceHeader() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      // trace ID only specified by trace header
      RestResponse response =
          adminRestSession.putWithHeader(
              "/projects/new6?trace", new BasicHeader(RestApiServlet.X_GERRIT_TRACE, "issue/123"));
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isEqualTo("issue/123");
      assertThat(projectCreationListener.traceId).isEqualTo("issue/123");
      assertThat(projectCreationListener.isLoggingForced).isTrue();
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new6");

      // trace ID only specified by trace request parameter
      response =
          adminRestSession.putWithHeader(
              "/projects/new7?trace=issue/123",
              new BasicHeader(RestApiServlet.X_GERRIT_TRACE, null));
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isEqualTo("issue/123");
      assertThat(projectCreationListener.traceId).isEqualTo("issue/123");
      assertThat(projectCreationListener.isLoggingForced).isTrue();
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new7");

      // same trace ID specified by trace header and trace request parameter
      response =
          adminRestSession.putWithHeader(
              "/projects/new8?trace=issue/123",
              new BasicHeader(RestApiServlet.X_GERRIT_TRACE, "issue/123"));
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isEqualTo("issue/123");
      assertThat(projectCreationListener.traceId).isEqualTo("issue/123");
      assertThat(projectCreationListener.isLoggingForced).isTrue();
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new8");

      // different trace IDs specified by trace header and trace request parameter
      response =
          adminRestSession.putWithHeader(
              "/projects/new9?trace=issue/123",
              new BasicHeader(RestApiServlet.X_GERRIT_TRACE, "issue/456"));
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeaders(RestApiServlet.X_GERRIT_TRACE))
          .containsExactly("issue/123", "issue/456");
      assertThat(projectCreationListener.traceIds).containsExactly("issue/123", "issue/456");
      assertThat(projectCreationListener.isLoggingForced).isTrue();
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new9");
    }
  }

  @Test
  public void pushWithoutTrace() throws Exception {
    TraceValidatingCommitValidationListener commitValidationListener =
        new TraceValidatingCommitValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(commitValidationListener)) {
      PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
      PushOneCommit.Result r = push.to("refs/heads/master");
      r.assertOkStatus();
      assertThat(commitValidationListener.traceId).isNull();
      assertThat(commitValidationListener.isLoggingForced).isFalse();

      // The logging tag with the project name is also set if tracing is off.
      assertThat(commitValidationListener.tags.get("project")).containsExactly(project.get());
    }
  }

  @Test
  public void pushWithTrace() throws Exception {
    TraceValidatingCommitValidationListener commitValidationListener =
        new TraceValidatingCommitValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(commitValidationListener)) {
      PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
      push.setPushOptions(ImmutableList.of("trace"));
      PushOneCommit.Result r = push.to("refs/heads/master");
      r.assertOkStatus();
      assertThat(commitValidationListener.traceId).isNotNull();
      assertThat(commitValidationListener.isLoggingForced).isTrue();
      assertThat(commitValidationListener.tags.get("project")).containsExactly(project.get());
    }
  }

  @Test
  public void pushWithTraceAndProvidedTraceId() throws Exception {
    TraceValidatingCommitValidationListener commitValidationListener =
        new TraceValidatingCommitValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(commitValidationListener)) {
      PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
      push.setPushOptions(ImmutableList.of("trace=issue/123"));
      PushOneCommit.Result r = push.to("refs/heads/master");
      r.assertOkStatus();
      assertThat(commitValidationListener.traceId).isEqualTo("issue/123");
      assertThat(commitValidationListener.isLoggingForced).isTrue();
      assertThat(commitValidationListener.tags.get("project")).containsExactly(project.get());
    }
  }

  @Test
  public void pushForReviewWithoutTrace() throws Exception {
    TraceValidatingCommitValidationListener commitValidationListener =
        new TraceValidatingCommitValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(commitValidationListener)) {
      PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
      PushOneCommit.Result r = push.to("refs/for/master");
      r.assertOkStatus();
      assertThat(commitValidationListener.traceId).isNull();
      assertThat(commitValidationListener.isLoggingForced).isFalse();

      // The logging tag with the project name is also set if tracing is off.
      assertThat(commitValidationListener.tags.get("project")).containsExactly(project.get());
    }
  }

  @Test
  public void pushForReviewWithTrace() throws Exception {
    TraceValidatingCommitValidationListener commitValidationListener =
        new TraceValidatingCommitValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(commitValidationListener)) {
      PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
      push.setPushOptions(ImmutableList.of("trace"));
      PushOneCommit.Result r = push.to("refs/for/master");
      r.assertOkStatus();
      assertThat(commitValidationListener.traceId).isNotNull();
      assertThat(commitValidationListener.isLoggingForced).isTrue();
      assertThat(commitValidationListener.tags.get("project")).containsExactly(project.get());
    }
  }

  @Test
  public void pushForReviewWithTraceAndProvidedTraceId() throws Exception {
    TraceValidatingCommitValidationListener commitValidationListener =
        new TraceValidatingCommitValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(commitValidationListener)) {
      PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
      push.setPushOptions(ImmutableList.of("trace=issue/123"));
      PushOneCommit.Result r = push.to("refs/for/master");
      r.assertOkStatus();
      assertThat(commitValidationListener.traceId).isEqualTo("issue/123");
      assertThat(commitValidationListener.isLoggingForced).isTrue();
      assertThat(commitValidationListener.tags.get("project")).containsExactly(project.get());
    }
  }

  @Test
  public void workQueueCopyLoggingContext() throws Exception {
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
    assertForceLogging(false);
    try (TraceContext traceContext = TraceContext.open().forceLogging().addTag("foo", "bar")) {
      SortedMap<String, SortedSet<Object>> tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("foo");
      assertThat(tagMap.get("foo")).containsExactly("bar");
      assertForceLogging(true);

      workQueue
          .createQueue(1, "test-queue")
          .submit(
              () -> {
                // Verify that the tags and force logging flag have been propagated to the new
                // thread.
                SortedMap<String, SortedSet<Object>> threadTagMap =
                    LoggingContext.getInstance().getTags().asMap();
                expect.that(threadTagMap.keySet()).containsExactly("foo");
                expect.that(threadTagMap.get("foo")).containsExactly("bar");
                expect
                    .that(LoggingContext.getInstance().shouldForceLogging(null, null, false))
                    .isTrue();
              })
          .get();

      // Verify that tags and force logging flag in the outer thread are still set.
      tagMap = LoggingContext.getInstance().getTags().asMap();
      assertThat(tagMap.keySet()).containsExactly("foo");
      assertThat(tagMap.get("foo")).containsExactly("bar");
      assertForceLogging(true);
    }
    assertThat(LoggingContext.getInstance().getTags().isEmpty()).isTrue();
    assertForceLogging(false);
  }

  @Test
  public void performanceLoggingForRestCall() throws Exception {
    TestPerformanceLogger testPerformanceLogger = new TestPerformanceLogger();
    try (Registration registration =
        extensionRegistry.newRegistration().add(testPerformanceLogger)) {
      RestResponse response = adminRestSession.put("/projects/new10");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);

      // This assertion assumes that the server invokes the PerformanceLogger plugins before it
      // sends
      // the response to the client. If this assertion gets flaky it's likely that this got changed
      // on
      // server-side.
      assertThat(testPerformanceLogger.logEntries()).isNotEmpty();
    }
  }

  @Test
  public void performanceLoggingForPush() throws Exception {
    TestPerformanceLogger testPerformanceLogger = new TestPerformanceLogger();
    try (Registration registration =
        extensionRegistry.newRegistration().add(testPerformanceLogger)) {
      PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
      PushOneCommit.Result r = push.to("refs/heads/master");
      r.assertOkStatus();
      assertThat(testPerformanceLogger.logEntries()).isNotEmpty();
    }
  }

  @Test
  @GerritConfig(name = "tracing.performanceLogging", value = "false")
  public void noPerformanceLoggingIfDisabled() throws Exception {
    TestPerformanceLogger testPerformanceLogger = new TestPerformanceLogger();
    try (Registration registration =
        extensionRegistry.newRegistration().add(testPerformanceLogger)) {
      RestResponse response = adminRestSession.put("/projects/new11");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);

      PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
      PushOneCommit.Result r = push.to("refs/heads/master");
      r.assertOkStatus();

      assertThat(testPerformanceLogger.logEntries()).isEmpty();
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.projectPattern", value = "new12")
  public void traceProject() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new12");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isEqualTo("issue123");
      assertThat(projectCreationListener.isLoggingForced).isTrue();
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new12");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.projectPattern", value = "new.*")
  public void traceProjectMatchRegEx() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new13");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isEqualTo("issue123");
      assertThat(projectCreationListener.isLoggingForced).isTrue();
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new13");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.projectPattern", value = "foo.*")
  public void traceProjectNoMatch() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new13");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isNull();
      assertThat(projectCreationListener.isLoggingForced).isFalse();

      // The logging tag with the project name is also set if tracing is off.
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new13");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.projectPattern", value = "][")
  public void traceProjectInvalidRegEx() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new14");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isNull();
      assertThat(projectCreationListener.isLoggingForced).isFalse();

      // The logging tag with the project name is also set if tracing is off.
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new14");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.account", value = "1000000")
  public void traceAccount() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new15");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isEqualTo("issue123");
      assertThat(projectCreationListener.isLoggingForced).isTrue();
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new15");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.account", value = "1000001")
  public void traceAccountNoMatch() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new16");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isNull();
      assertThat(projectCreationListener.isLoggingForced).isFalse();

      // The logging tag with the project name is also set if tracing is off.
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new16");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.account", value = "999")
  public void traceAccountNotFound() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new17");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isNull();
      assertThat(projectCreationListener.isLoggingForced).isFalse();

      // The logging tag with the project name is also set if tracing is off.
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new17");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.account", value = "invalid")
  public void traceAccountInvalidId() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new18");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isNull();
      assertThat(projectCreationListener.isLoggingForced).isFalse();

      // The logging tag with the project name is also set if tracing is off.
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new18");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.requestType", value = "REST")
  public void traceRequestType() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new19");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isEqualTo("issue123");
      assertThat(projectCreationListener.isLoggingForced).isTrue();
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new19");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.requestType", value = "SSH")
  public void traceRequestTypeNoMatch() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new20");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isNull();
      assertThat(projectCreationListener.isLoggingForced).isFalse();

      // The logging tag with the project name is also set if tracing is off.
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new20");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.requestType", value = "FOO")
  public void traceProjectInvalidRequestType() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new21");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isNull();
      assertThat(projectCreationListener.isLoggingForced).isFalse();

      // The logging tag with the project name is also set if tracing is off.
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new21");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.account", value = "1000000")
  @GerritConfig(name = "tracing.issue123.projectPattern", value = "new.*")
  public void traceProjectForAccount() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new22");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isEqualTo("issue123");
      assertThat(projectCreationListener.isLoggingForced).isTrue();
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new22");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.account", value = "1000000")
  @GerritConfig(name = "tracing.issue123.projectPattern", value = "foo.*")
  public void traceProjectForAccountNoProjectMatch() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new23");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isNull();
      assertThat(projectCreationListener.isLoggingForced).isFalse();

      // The logging tag with the project name is also set if tracing is off.
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new23");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.account", value = "1000001")
  @GerritConfig(name = "tracing.issue123.projectPattern", value = "new.*")
  public void traceProjectForAccountNoAccountMatch() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new24");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isNull();
      assertThat(projectCreationListener.isLoggingForced).isFalse();

      // The logging tag with the project name is also set if tracing is off.
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new24");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.requestUriPattern", value = "/projects/.*")
  public void traceRequestUri() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new23");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isEqualTo("issue123");
      assertThat(projectCreationListener.isLoggingForced).isTrue();
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new23");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.requestUriPattern", value = "/projects/.*/foo")
  public void traceRequestUriNoMatch() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new23");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isNull();
      assertThat(projectCreationListener.isLoggingForced).isFalse();

      // The logging tag with the project name is also set if tracing is off.
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new23");
    }
  }

  @Test
  @GerritConfig(name = "tracing.issue123.requestUriPattern", value = "][")
  public void traceRequestUriInvalidRegEx() throws Exception {
    TraceValidatingProjectCreationValidationListener projectCreationListener =
        new TraceValidatingProjectCreationValidationListener();
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/new24");
      assertThat(response.getStatusCode()).isEqualTo(SC_CREATED);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(projectCreationListener.traceId).isNull();
      assertThat(projectCreationListener.isLoggingForced).isFalse();

      // The logging tag with the project name is also set if tracing is off.
      assertThat(projectCreationListener.tags.get("project")).containsExactly("new24");
    }
  }

  @Test
  @GerritConfig(name = "retry.retryWithTraceOnFailure", value = "true")
  public void autoRetryWithTrace() throws Exception {
    String changeId = createChange().getChangeId();
    approve(changeId);

    TraceSubmitRule traceSubmitRule = new TraceSubmitRule();
    traceSubmitRule.failAlways = true;
    try (Registration registration = extensionRegistry.newRegistration().add(traceSubmitRule)) {
      RestResponse response = adminRestSession.post("/changes/" + changeId + "/submit");
      assertThat(response.getStatusCode()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).startsWith("retry-on-failure-");
      assertThat(traceSubmitRule.traceId).startsWith("retry-on-failure-");
      assertThat(traceSubmitRule.isLoggingForced).isTrue();
    }
  }

  @Test
  @GerritConfig(name = "retry.retryWithTraceOnFailure", value = "true")
  public void noAutoRetryIfExceptionCausesNormalRetrying() throws Exception {
    String changeId = createChange().getChangeId();
    approve(changeId);

    TraceSubmitRule traceSubmitRule = new TraceSubmitRule();
    traceSubmitRule.failAlways = true;
    try (Registration registration =
        extensionRegistry
            .newRegistration()
            .add(traceSubmitRule)
            .add(
                new ExceptionHook() {
                  @Override
                  public boolean shouldRetry(String actionType, String actionName, Throwable t) {
                    return true;
                  }
                })) {
      RestResponse response = adminRestSession.post("/changes/" + changeId + "/submit");
      assertThat(response.getStatusCode()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(traceSubmitRule.traceId).isNull();
      assertThat(traceSubmitRule.isLoggingForced).isFalse();
    }
  }

  @Test
  public void noAutoRetryWithTraceIfDisabled() throws Exception {
    String changeId = createChange().getChangeId();
    approve(changeId);

    TraceSubmitRule traceSubmitRule = new TraceSubmitRule();
    traceSubmitRule.failOnce = true;
    try (Registration registration = extensionRegistry.newRegistration().add(traceSubmitRule)) {
      RestResponse response = adminRestSession.post("/changes/" + changeId + "/submit");
      assertThat(response.getStatusCode()).isEqualTo(SC_INTERNAL_SERVER_ERROR);
      assertThat(response.getHeader(RestApiServlet.X_GERRIT_TRACE)).isNull();
      assertThat(traceSubmitRule.traceId).isNull();
      assertThat(traceSubmitRule.isLoggingForced).isFalse();
    }
  }

  private void assertForceLogging(boolean expected) {
    assertThat(LoggingContext.getInstance().shouldForceLogging(null, null, false))
        .isEqualTo(expected);
  }

  private static class TraceValidatingProjectCreationValidationListener
      implements ProjectCreationValidationListener {
    String traceId;
    ImmutableSet<String> traceIds;
    Boolean isLoggingForced;
    ImmutableSetMultimap<String, String> tags;

    @Override
    public void validateNewProject(CreateProjectArgs args) throws ValidationException {
      this.traceId =
          Iterables.getFirst(LoggingContext.getInstance().getTagsAsMap().get("TRACE_ID"), null);
      this.traceIds = LoggingContext.getInstance().getTagsAsMap().get("TRACE_ID");
      this.isLoggingForced = LoggingContext.getInstance().shouldForceLogging(null, null, false);
      this.tags = LoggingContext.getInstance().getTagsAsMap();
    }
  }

  private static class TraceValidatingCommitValidationListener implements CommitValidationListener {
    String traceId;
    Boolean isLoggingForced;
    ImmutableSetMultimap<String, String> tags;

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      this.traceId =
          Iterables.getFirst(LoggingContext.getInstance().getTagsAsMap().get("TRACE_ID"), null);
      this.isLoggingForced = LoggingContext.getInstance().shouldForceLogging(null, null, false);
      this.tags = LoggingContext.getInstance().getTagsAsMap();
      return ImmutableList.of();
    }
  }

  private static class TraceChangeIndexedListener implements ChangeIndexedListener {
    ImmutableSetMultimap<String, String> tags;

    @Override
    public void onChangeIndexed(String projectName, int id) {
      this.tags = LoggingContext.getInstance().getTagsAsMap();
    }

    @Override
    public void onChangeDeleted(int id) {}
  }

  private static class TraceSubmitRule implements SubmitRule {
    String traceId;
    Boolean isLoggingForced;
    boolean failOnce;
    boolean failAlways;

    @Override
    public Optional<SubmitRecord> evaluate(ChangeData changeData) {
      this.traceId =
          Iterables.getFirst(LoggingContext.getInstance().getTagsAsMap().get("TRACE_ID"), null);
      this.isLoggingForced = LoggingContext.getInstance().shouldForceLogging(null, null, false);

      if (failOnce || failAlways) {
        failOnce = false;
        throw new IllegalStateException("forced failure from test");
      }

      SubmitRecord submitRecord = new SubmitRecord();
      submitRecord.status = SubmitRecord.Status.OK;
      return Optional.of(submitRecord);
    }
  }

  private static class TestPerformanceLogger implements PerformanceLogger {
    private List<PerformanceLogEntry> logEntries = new ArrayList<>();

    @Override
    public void log(String operation, long durationMs, Metadata metadata) {
      logEntries.add(PerformanceLogEntry.create(operation, metadata));
    }

    ImmutableList<PerformanceLogEntry> logEntries() {
      return ImmutableList.copyOf(logEntries);
    }
  }

  @AutoValue
  abstract static class PerformanceLogEntry {
    static PerformanceLogEntry create(String operation, Metadata metadata) {
      return new AutoValue_TraceIT_PerformanceLogEntry(operation, metadata);
    }

    abstract String operation();

    abstract Metadata metadata();
  }
}
