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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.restapi.RestApiErrorHandler;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Test;

public class ErrorHandlerIT extends AbstractDaemonTest {
  @Inject private DynamicSet<RestApiErrorHandler> errorHandlers;
  @Inject private DynamicSet<ProjectCreationValidationListener> projectCreationValidationListerners;

  @Test
  public void convertMessage() throws Exception {
    String message = "Not Found -  Did you mean '/changes/1/detail'?";
    RegistrationHandle handle =
        errorHandlers.add(
            new RestApiErrorHandler() {
              @Override
              public String handleError(
                  HttpServletRequest req, HttpServletResponse res, String msg, Throwable err) {
                return message;
              }
            });
    try {
      RestResponse response = adminRestSession.get("/changes/1/dtail");
      response.assertNotFound();
      assertThat(response.getEntityContent()).isEqualTo(message);
    } finally {
      handle.remove();
    }
  }

  @Test
  public void convertStatus() throws Exception {
    int status = 510;
    RegistrationHandle handle1 =
        projectCreationValidationListerners.add(
            new ProjectCreationValidationListener() {
              @Override
              public void validateNewProject(CreateProjectArgs args) throws ValidationException {
                if (args.getProjectName().contains("-")) {
                  throw new ValidationException("project name cannot contain '-'");
                }
              }
            });
    RegistrationHandle handle2 =
        errorHandlers.add(
            new RestApiErrorHandler() {
              @Override
              public String handleError(
                  HttpServletRequest req, HttpServletResponse res, String msg, Throwable err) {
                if (err != null && err.getCause() instanceof ValidationException) {
                  res.setStatus(status);
                  return "Plugin error: " + ((ValidationException) err.getCause()).getMessage();
                }
                return msg;
              }
            });
    try {
      RestResponse response = adminRestSession.put("/projects/new-project");
      assertThat(response.getStatusCode()).isEqualTo(status);
      assertThat(response.getEntityContent())
          .isEqualTo("Plugin error: project name cannot contain '-'");
    } finally {
      handle1.remove();
      handle2.remove();
    }
  }
}
