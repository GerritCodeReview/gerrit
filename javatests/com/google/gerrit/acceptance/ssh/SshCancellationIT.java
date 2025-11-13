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

package com.google.gerrit.acceptance.ssh;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.server.cancellation.RequestCancelledException;
import com.google.gerrit.server.cancellation.RequestStateProvider;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import org.junit.Test;

@UseSsh
public class SshCancellationIT extends AbstractDaemonTest {
  @Inject private ExtensionRegistry extensionRegistry;

  @Test
  public void handleClientDisconnected() throws Exception {
    ProjectCreationValidationListener projectCreationListener =
        new ProjectCreationValidationListener() {
          @Override
          public void validateNewProject(CreateProjectArgs args) throws ValidationException {
            throw new RequestCancelledException(
                RequestStateProvider.Reason.CLIENT_CLOSED_REQUEST, /* cancellationMessage= */ null);
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      adminSshSession.exec("gerrit create-project " + name("new"));
      adminSshSession.assertFailure("Client Closed Request");
    }
  }

  @Test
  public void handleClientDeadlineExceeded() throws Exception {
    ProjectCreationValidationListener projectCreationListener =
        new ProjectCreationValidationListener() {
          @Override
          public void validateNewProject(CreateProjectArgs args) throws ValidationException {
            throw new RequestCancelledException(
                RequestStateProvider.Reason.CLIENT_PROVIDED_DEADLINE_EXCEEDED,
                /* cancellationMessage= */ null);
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      adminSshSession.exec("gerrit create-project " + name("new"));
      adminSshSession.assertFailure("Client Provided Deadline Exceeded");
    }
  }

  @Test
  public void handleServerDeadlineExceeded() throws Exception {
    ProjectCreationValidationListener projectCreationListener =
        new ProjectCreationValidationListener() {
          @Override
          public void validateNewProject(CreateProjectArgs args) throws ValidationException {
            throw new RequestCancelledException(
                RequestStateProvider.Reason.SERVER_DEADLINE_EXCEEDED,
                /* cancellationMessage= */ null);
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      adminSshSession.exec("gerrit create-project " + name("new"));
      adminSshSession.assertFailure("Server Deadline Exceeded");
    }
  }

  @Test
  public void handleRequestCancellationWithMessage() throws Exception {
    ProjectCreationValidationListener projectCreationListener =
        new ProjectCreationValidationListener() {
          @Override
          public void validateNewProject(CreateProjectArgs args) throws ValidationException {
            throw new RequestCancelledException(
                RequestStateProvider.Reason.SERVER_DEADLINE_EXCEEDED, "deadline = 10m");
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      adminSshSession.exec("gerrit create-project " + name("new"));
      adminSshSession.assertFailure("Server Deadline Exceeded (deadline = 10m)");
    }
  }

  @Test
  public void handleWrappedRequestCancelledException() throws Exception {
    ProjectCreationValidationListener projectCreationListener =
        new ProjectCreationValidationListener() {
          @Override
          public void validateNewProject(CreateProjectArgs args) throws ValidationException {
            throw new RuntimeException(
                new RequestCancelledException(
                    RequestStateProvider.Reason.SERVER_DEADLINE_EXCEEDED, "deadline = 10m"));
          }
        };
    try (Registration registration =
        extensionRegistry.newRegistration().add(projectCreationListener)) {
      adminSshSession.exec("gerrit create-project " + name("new"));
      adminSshSession.assertFailure("Server Deadline Exceeded (deadline = 10m)");
    }
  }

  @Test
  public void abortIfClientProvidedDeadlineExceeded() throws Exception {
    adminSshSession.exec("gerrit create-project --deadline 1ms " + name("new"));
    adminSshSession.assertFailure("Client Provided Deadline Exceeded (client.timeout=1ms)");
  }

  @Test
  public void requestRejectedIfInvalidDeadlineIsProvided_missingTimeUnit() throws Exception {
    adminSshSession.exec("gerrit create-project --deadline 1 " + name("new"));
    adminSshSession.assertFailure("Invalid deadline. Missing time unit: 1");
  }

  @Test
  public void requestRejectedIfInvalidDeadlineIsProvided_invalidTimeUnit() throws Exception {
    adminSshSession.exec("gerrit create-project --deadline 1x " + name("new"));
    adminSshSession.assertFailure("Invalid deadline. Invalid time unit value: 1x");
  }

  @Test
  public void requestRejectedIfInvalidDeadlineIsProvided_invalidValue() throws Exception {
    adminSshSession.exec("gerrit create-project --deadline invalid " + name("new"));
    adminSshSession.assertFailure("Invalid deadline. Invalid time unit value: invalid");
  }

  @Test
  public void requestSucceedsWithinDeadline() throws Exception {
    adminSshSession.exec("gerrit create-project --deadline 10m " + name("new"));
    adminSshSession.assertSuccess();
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  public void abortIfServerDeadlineExceeded() throws Exception {
    adminSshSession.exec("gerrit create-project " + name("new"));
    adminSshSession.assertFailure("Server Deadline Exceeded (default.timeout=1ms)");
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  public void clientProvidedDeadlineOverridesServerDeadline() throws Exception {
    adminSshSession.exec("gerrit create-project --deadline 2ms " + name("new"));
    adminSshSession.assertFailure("Client Provided Deadline Exceeded (client.timeout=2ms)");
  }

  @Test
  @GerritConfig(name = "deadline.default.timeout", value = "1ms")
  public void clientCanDisableDeadlineBySettingZeroAsDeadline() throws Exception {
    adminSshSession.exec("gerrit create-project --deadline 0 " + name("new"));
    adminSshSession.assertSuccess();
  }
}
