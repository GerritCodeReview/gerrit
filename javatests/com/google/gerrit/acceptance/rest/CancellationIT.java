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
        .isEqualTo("Client Provided Deadline Exceeded\n\ntimeout=1ms");
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
  @GerritConfig(name = "receive.timeout", value = "1ms")
  public void abortPushIfTimeoutExceeded() throws Exception {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertErrorStatus("Server Deadline Exceeded (timeout=1ms)");
  }

  @Test
  public void abortPushIfClientProvidedDeadlineExceeded() throws Exception {
    List<String> pushOptions = new ArrayList<>();
    pushOptions.add("deadline=1ms");
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    push.setPushOptions(pushOptions);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertErrorStatus("Client Provided Deadline Exceeded (timeout=1ms)");
  }

  @Test
  public void abortPushIfClientProvidedDeadlineExceeded_millisecondsAssumedByDefault()
      throws Exception {
    List<String> pushOptions = new ArrayList<>();
    pushOptions.add("deadline=1");
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    push.setPushOptions(pushOptions);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertErrorStatus("Client Provided Deadline Exceeded (timeout=1ms)");
  }

  @Test
  public void pushRejectedIfInvalidDeadlineIsProvided() throws Exception {
    List<String> pushOptions = new ArrayList<>();
    pushOptions.add("deadline=1x");
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    push.setPushOptions(pushOptions);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertErrorStatus("Invalid deadline. Invalid time unit value: 1x");
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
  @GerritConfig(name = "receive.timeout", value = "1ms")
  public void clientProvidedDeadlineOnPushDoesntOverrideServerTimeout() throws Exception {
    List<String> pushOptions = new ArrayList<>();
    pushOptions.add("deadline=10m");
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    push.setPushOptions(pushOptions);
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertErrorStatus("Server Deadline Exceeded (timeout=1ms)");
  }
}
