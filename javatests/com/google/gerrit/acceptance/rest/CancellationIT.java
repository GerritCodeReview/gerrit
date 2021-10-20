// Copyright (C) 2021 The Android Open Source Project
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
import static com.google.gerrit.httpd.restapi.RestApiServlet.SC_CLIENT_CLOSED_REQUEST;
import static org.apache.http.HttpStatus.SC_REQUEST_TIMEOUT;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.httpd.restapi.RestApiServlet;
import com.google.gerrit.server.cancellation.RequestCancelledException;
import com.google.gerrit.server.cancellation.RequestStateProvider;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.message.BasicHeader;
import org.junit.Test;

public class CancellationIT extends AbstractDaemonTest {
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void handleClientDisconnected() throws Exception {
    ProjectCreationValidationListener projectCreationListener =
        new ProjectCreationValidationListener() {
          @Override
          public void validateNewProject(CreateProjectArgs args) throws ValidationException {
            // Simulate a request cancellation by throwing RequestCancelledException. In contrast to
            // an actual request cancellation this allows us to verify the HTTP status code that is
            // set when a request is cancelled.
            throw new RequestCancelledException(
                RequestStateProvider.Reason.CLIENT_CLOSED_REQUEST, /* cancellationMessage= */ null);
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/" + name("new"));
      assertThat(response.getStatusCode()).isEqualTo(SC_CLIENT_CLOSED_REQUEST);
      assertThat(response.getEntityContent()).isEqualTo("Client Closed Request");
    }
  }

  @Test
  public void handleClientDeadlineExceeded() throws Exception {
    ProjectCreationValidationListener projectCreationListener =
        new ProjectCreationValidationListener() {
          @Override
          public void validateNewProject(CreateProjectArgs args) throws ValidationException {
            // Simulate an exceeded deadline by throwing RequestCancelledException.
            throw new RequestCancelledException(
                RequestStateProvider.Reason.CLIENT_PROVIDED_DEADLINE_EXCEEDED,
                /* cancellationMessage= */ null);
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/" + name("new"));
      assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
      assertThat(response.getEntityContent()).isEqualTo("Client Provided Deadline Exceeded");
    }
  }

  @Test
  public void handleServerDeadlineExceeded() throws Exception {
    ProjectCreationValidationListener projectCreationListener =
        new ProjectCreationValidationListener() {
          @Override
          public void validateNewProject(CreateProjectArgs args) throws ValidationException {
            // Simulate an exceeded deadline by throwing RequestCancelledException.
            throw new RequestCancelledException(
                RequestStateProvider.Reason.SERVER_DEADLINE_EXCEEDED,
                /* cancellationMessage= */ null);
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/" + name("new"));
      assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
      assertThat(response.getEntityContent()).isEqualTo("Server Deadline Exceeded");
    }
  }

  @Test
  public void handleRequestCancellationWithMessage() throws Exception {
    ProjectCreationValidationListener projectCreationListener =
        new ProjectCreationValidationListener() {
          @Override
          public void validateNewProject(CreateProjectArgs args) throws ValidationException {
            // Simulate an exceeded deadline by throwing RequestCancelledException.
            throw new RequestCancelledException(
                RequestStateProvider.Reason.SERVER_DEADLINE_EXCEEDED, "deadline = 10m");
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/" + name("new"));
      assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
      assertThat(response.getEntityContent())
          .isEqualTo("Server Deadline Exceeded\n\ndeadline = 10m");
    }
  }

  @Test
  public void handleWrappedRequestCancelledException() throws Exception {
    ProjectCreationValidationListener projectCreationListener =
        new ProjectCreationValidationListener() {
          @Override
          public void validateNewProject(CreateProjectArgs args) throws ValidationException {
            // Simulate an exceeded deadline by throwing RequestCancelledException.
            throw new RuntimeException(
                new RequestCancelledException(
                    RequestStateProvider.Reason.SERVER_DEADLINE_EXCEEDED, "deadline = 10m"));
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      RestResponse response = adminRestSession.put("/projects/" + name("new"));
      assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
      assertThat(response.getEntityContent())
          .isEqualTo("Server Deadline Exceeded\n\ndeadline = 10m");
    }
  }

  @Test
  public void abortIfClientProvidedDeadlineExceeded() throws Exception {
    RestResponse response =
        adminRestSession.putWithHeaders(
            "/projects/" + name("new"), new BasicHeader(RestApiServlet.X_GERRIT_DEADLINE, "1ms"));
    assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
    assertThat(response.getEntityContent())
        .isEqualTo("Client Provided Deadline Exceeded\n\nclient.timeout=1ms");
  }

  @Test
  public void requestRejectedIfInvalidDeadlineIsProvided_missingTimeUnit() throws Exception {
    RestResponse response =
        adminRestSession.putWithHeaders(
            "/projects/" + name("new"), new BasicHeader(RestApiServlet.X_GERRIT_DEADLINE, "1"));
    response.assertBadRequest();
    assertThat(response.getEntityContent()).isEqualTo("Invalid deadline. Missing time unit: 1");
  }

  @Test
  public void requestRejectedIfInvalidDeadlineIsProvided_invalidTimeUnit() throws Exception {
    RestResponse response =
        adminRestSession.putWithHeaders(
            "/projects/" + name("new"), new BasicHeader(RestApiServlet.X_GERRIT_DEADLINE, "1x"));
    response.assertBadRequest();
    assertThat(response.getEntityContent())
        .isEqualTo("Invalid deadline. Invalid time unit value: 1x");
  }

  @Test
  public void requestRejectedIfInvalidDeadlineIsProvided_invalidValue() throws Exception {
    RestResponse response =
        adminRestSession.putWithHeaders(
            "/projects/" + name("new"),
            new BasicHeader(RestApiServlet.X_GERRIT_DEADLINE, "invalid"));
    response.assertBadRequest();
    assertThat(response.getEntityContent()).isEqualTo("Invalid deadline. Invalid value: invalid");
  }

  @Test
  public void requestSucceedsWithinDeadline() throws Exception {
    RestResponse response =
        adminRestSession.putWithHeaders(
            "/projects/" + name("new"), new BasicHeader(RestApiServlet.X_GERRIT_DEADLINE, "10m"));
    response.assertCreated();
  }

  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  public void abortIfServerDeadlineExceeded() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
    assertThat(response.getEntityContent()).isEqualTo("Server Deadline Exceeded\n\ntimeout=1ms");
  }

  @Test
  @GerritConfig(name = "deadline.foo.timeout", value = "1ms")
  @GerritConfig(name = "deadline.bar.timeout", value = "100ms")
  public void stricterDeadlineTakesPrecedence() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
    assertThat(response.getEntityContent())
        .isEqualTo("Server Deadline Exceeded\n\nfoo.timeout=1ms");
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.requestType", value = "REST")
  public void abortIfServerDeadlineExceeded_requestType() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
    assertThat(response.getEntityContent())
        .isEqualTo("Server Deadline Exceeded\n\ndefault.timeout=1ms");
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.requestUriPattern", value = "/projects/.*")
  public void abortIfServerDeadlineExceeded_requestUriPattern() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
    assertThat(response.getEntityContent())
        .isEqualTo("Server Deadline Exceeded\n\ndefault.timeout=1ms");
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(
      name = "deadline.default.excludedRequestUriPattern",
      value = "/projects/non-matching")
  public void abortIfServerDeadlineExceeded_excludedRequestUriPattern() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
    assertThat(response.getEntityContent())
        .isEqualTo("Server Deadline Exceeded\n\ndefault.timeout=1ms");
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.requestUriPattern", value = "/projects/.*")
  @GerritConfig(
      name = "deadline.default.excludedRequestUriPattern",
      value = "/projects/non-matching")
  public void abortIfServerDeadlineExceeded_requestUriPatternAndExcludedRequestUriPattern()
      throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
    assertThat(response.getEntityContent())
        .isEqualTo("Server Deadline Exceeded\n\ndefault.timeout=1ms");
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.projectPattern", value = ".*new.*")
  public void abortIfServerDeadlineExceeded_projectPattern() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
    assertThat(response.getEntityContent())
        .isEqualTo("Server Deadline Exceeded\n\ndefault.timeout=1ms");
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.account", value = "1000000")
  public void abortIfServerDeadlineExceeded_account() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
    assertThat(response.getEntityContent())
        .isEqualTo("Server Deadline Exceeded\n\ndefault.timeout=1ms");
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.requestType", value = "SSH")
  public void nonMatchingServerDeadlineIsIgnored_requestType() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.requestUriPattern", value = "/changes/.*")
  public void nonMatchingServerDeadlineIsIgnored_requestUriPattern() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.excludedRequestUriPattern", value = "/projects/.*")
  public void nonMatchingServerDeadlineIsIgnored_excludedRequestUriPattern() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.requestUriPattern", value = "/projects/.*")
  @GerritConfig(name = "deadline.default.excludedRequestUriPattern", value = "/projects/.*new")
  public void nonMatchingServerDeadlineIsIgnored_requestUriPatternAndExcludedRequestUriPattern()
      throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.projectPattern", value = ".*foo.*")
  public void nonMatchingServerDeadlineIsIgnored_projectPattern() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.account", value = "999")
  public void nonMatchingServerDeadlineIsIgnored_account() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.isAdvisory", value = "true")
  public void advisoryServerDeadlineIsIgnored() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.test.timeout", value = "1ms")
  @GerritConfig(name = "deadline.test.isAdvisory", value = "true")
  @GerritConfig(name = "deadline.default.timeout", value = "2ms")
  public void nonAdvisoryDeadlineIsAppliedIfStricterAdvisoryDeadlineExists() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
    assertThat(response.getEntityContent())
        .isEqualTo("Server Deadline Exceeded\n\ndefault.timeout=2ms");
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1")
  public void invalidServerDeadlineIsIgnored_missingTimeUnit() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1x")
  public void invalidServerDeadlineIsIgnored_invalidTimeUnit() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "invalid")
  public void invalidServerDeadlineIsIgnored_invalidValue() throws Exception {
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.requestType", value = "INVALID")
  public void invalidServerDeadlineIsIgnored_invalidRequestType() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.requestUriPattern", value = "][")
  public void invalidServerDeadlineIsIgnored_invalidRequestUriPattern() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.excludedRequestUriPattern", value = "][")
  public void invalidServerDeadlineIsIgnored_invalidExcludedRequestUriPattern() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.projectPattern", value = "][")
  public void invalidServerDeadlineIsIgnored_invalidProjectPattern() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.account", value = "invalid")
  public void invalidServerDeadlineIsIgnored_invalidAccount() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.requestType", value = "REST")
  public void deadlineConfigWithoutTimeoutIsIgnored() throws Exception {
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "0ms")
  @GerritConfig(name = "deadline.default.requestType", value = "REST")
  public void deadlineConfigWithZeroTimeoutIsIgnored() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "500ms")
  public void exceededDeadlineForOneRequestDoesntAbortFollowUpRequest() throws Exception {
    ProjectCreationValidationListener projectCreationValidationListener =
        new ProjectCreationValidationListener() {
          @Override
          public void validateNewProject(CreateProjectArgs args) throws ValidationException {
            try {
              Thread.sleep(1000);
            } catch (InterruptedException e) {
              throw new RuntimeException("interrupted during sleep", e);
            }
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationValidationListener)) {
      RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new"));
      assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
      assertThat(response.getEntityContent())
          .isEqualTo("Server Deadline Exceeded\n\ndefault.timeout=500ms");
    }
    // verify that the exceeded deadline for the previous request, isn't applied to a new request
    RestResponse response = adminRestSession.putWithHeaders("/projects/" + name("new2"));
    response.assertCreated();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  public void clientProvidedDeadlineOverridesServerDeadline() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response =
        adminRestSession.putWithHeaders(
            "/projects/" + name("new"), new BasicHeader(RestApiServlet.X_GERRIT_DEADLINE, "2ms"));
    assertThat(response.getStatusCode()).isEqualTo(SC_REQUEST_TIMEOUT);
    assertThat(response.getEntityContent())
        .isEqualTo("Client Provided Deadline Exceeded\n\nclient.timeout=2ms");
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  public void clientCanDisableDeadlineBySettingZeroAsDeadline() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    RestResponse response =
        adminRestSession.putWithHeaders(
            "/projects/" + name("new"), new BasicHeader(RestApiServlet.X_GERRIT_DEADLINE, "0"));
    response.assertCreated();
  }

  @Test
  public void handleClientDisconnectedForPush() throws Exception {
    CommitValidationListener commitValidationListener =
        new CommitValidationListener() {
          @Override
          public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
              throws CommitValidationException {
            // Simulate a request cancellation by throwing RequestCancelledException. In contrast to
            // an actual request cancellation this allows us verify the error message that is sent
            // to the client.
            throw new RequestCancelledException(
                RequestStateProvider.Reason.CLIENT_CLOSED_REQUEST, /* cancellationMessage= */ null);
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(commitValidationListener)) {
      PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
      PushOneCommit.Result r = push.to("refs/heads/master");
      r.assertErrorStatus("Client Closed Request");
    }
  }

  @Test
  public void handleClientDeadlineExceededForPush() throws Exception {
    CommitValidationListener commitValidationListener =
        new CommitValidationListener() {
          @Override
          public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
              throws CommitValidationException {
            // Simulate an exceeded deadline by throwing RequestCancelledException.
            throw new RequestCancelledException(
                RequestStateProvider.Reason.CLIENT_PROVIDED_DEADLINE_EXCEEDED,
                /* cancellationMessage= */ null);
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(commitValidationListener)) {
      PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
      PushOneCommit.Result r = push.to("refs/heads/master");
      r.assertErrorStatus("Client Provided Deadline Exceeded");
    }
  }

  @Test
  public void handleServerDeadlineExceededForPush() throws Exception {
    CommitValidationListener commitValidationListener =
        new CommitValidationListener() {
          @Override
          public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
              throws CommitValidationException {
            // Simulate an exceeded deadline by throwing RequestCancelledException.
            throw new RequestCancelledException(
                RequestStateProvider.Reason.SERVER_DEADLINE_EXCEEDED,
                /* cancellationMessage= */ null);
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(commitValidationListener)) {
      PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
      PushOneCommit.Result r = push.to("refs/heads/master");
      r.assertErrorStatus("Server Deadline Exceeded");
    }
  }

  @Test
  public void handleWrappedRequestCancelledExceptionForPush() throws Exception {
    CommitValidationListener commitValidationListener =
        new CommitValidationListener() {
          @Override
          public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
              throws CommitValidationException {
            // Simulate an exceeded deadline by throwing RequestCancelledException.
            throw new RuntimeException(
                new RequestCancelledException(
                    RequestStateProvider.Reason.SERVER_DEADLINE_EXCEEDED, "deadline = 10m"));
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(commitValidationListener)) {
      PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
      PushOneCommit.Result r = push.to("refs/heads/master");
      r.assertErrorStatus("Server Deadline Exceeded (deadline = 10m)");
    }
  }

  @Test
  public void handleRequestCancellationWithMessageForPush() throws Exception {
    CommitValidationListener commitValidationListener =
        new CommitValidationListener() {
          @Override
          public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
              throws CommitValidationException {
            // Simulate an exceeded deadline by throwing RequestCancelledException.
            throw new RequestCancelledException(
                RequestStateProvider.Reason.SERVER_DEADLINE_EXCEEDED, "deadline = 10m");
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(commitValidationListener)) {
      PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
      PushOneCommit.Result r = push.to("refs/heads/master");
      r.assertErrorStatus("Server Deadline Exceeded (deadline = 10m)");
    }
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  public void abortPushIfServerDeadlineExceeded() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertErrorStatus("Server Deadline Exceeded (default.timeout=1ms)");
  }

  @Test
  @GerritConfig(name = "receive.timeout", value = "1ms")
  public void abortPushIfTimeoutExceeded() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertErrorStatus("Server Deadline Exceeded (receive.timeout=1ms)");
  }

  @Test
  @GerritConfig(name = "receive.timeout", value = "1ms")
  @GerritConfig(name = "deadline.default.timeout", value = "10s")
  public void receiveTimeoutTakesPrecedence() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertErrorStatus("Server Deadline Exceeded (receive.timeout=1ms)");
  }

  @Test
  public void abortPushIfClientProvidedDeadlineExceeded() throws Exception {
    List<String> pushOptions = new ArrayList<>();
    pushOptions.add("deadline=1ms");
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    push.setPushOptions(pushOptions);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertErrorStatus("Client Provided Deadline Exceeded (client.timeout=1ms)");
  }

  @Test
  public void pushRejectedIfInvalidDeadlineIsProvided_missingTimeUnit() throws Exception {
    List<String> pushOptions = new ArrayList<>();
    pushOptions.add("deadline=1");
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    push.setPushOptions(pushOptions);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertErrorStatus("Invalid deadline. Missing time unit: 1");
  }

  @Test
  public void pushRejectedIfInvalidDeadlineIsProvided_invalidTimeUnit() throws Exception {
    List<String> pushOptions = new ArrayList<>();
    pushOptions.add("deadline=1x");
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    push.setPushOptions(pushOptions);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertErrorStatus("Invalid deadline. Invalid time unit value: 1x");
  }

  @Test
  public void pushRejectedIfInvalidDeadlineIsProvided_invalidValue() throws Exception {
    List<String> pushOptions = new ArrayList<>();
    pushOptions.add("deadline=invalid");
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    push.setPushOptions(pushOptions);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertErrorStatus("Invalid deadline. Invalid value: invalid");
  }

  @Test
  public void pushSucceedsWithinDeadline() throws Exception {
    List<String> pushOptions = new ArrayList<>();
    pushOptions.add("deadline=10m");
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    push.setPushOptions(pushOptions);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  public void clientProvidedDeadlineOnPushOverridesServerDeadline() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    List<String> pushOptions = new ArrayList<>();
    pushOptions.add("deadline=2ms");
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    push.setPushOptions(pushOptions);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertErrorStatus("Client Provided Deadline Exceeded (client.timeout=2ms)");
  }

  @Test
  @GerritConfig(name = "receive.timeout", value = "1ms")
  public void clientProvidedDeadlineOnPushDoesntOverrideServerTimeout() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    List<String> pushOptions = new ArrayList<>();
    pushOptions.add("deadline=10m");
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    push.setPushOptions(pushOptions);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertErrorStatus("Server Deadline Exceeded (receive.timeout=1ms)");
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  public void clientCanDisableDeadlineOnPushBySettingZeroAsDeadline() throws Exception {
    testTicker.useFakeTicker().setAutoIncrementStep(Duration.ofMillis(2));
    List<String> pushOptions = new ArrayList<>();
    pushOptions.add("deadline=0");
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    push.setPushOptions(pushOptions);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();
  }
}
