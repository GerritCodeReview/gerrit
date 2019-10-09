// Copyright (C) 2019 The Android Open Source Project
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

import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.ExceptionHook;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.logging.PerformanceLogger;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class ExtensionRegistry {
  private final DynamicSet<ChangeIndexedListener> changeIndexedListeners;
  private final DynamicSet<CommitValidationListener> commitValidationListeners;
  private final DynamicSet<ExceptionHook> exceptionHooks;
  private final DynamicSet<PerformanceLogger> performanceLoggers;
  private final DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners;
  private final DynamicSet<SubmitRule> submitRules;

  @Inject
  ExtensionRegistry(
      DynamicSet<ChangeIndexedListener> changeIndexedListeners,
      DynamicSet<CommitValidationListener> commitValidationListeners,
      DynamicSet<ExceptionHook> exceptionHooks,
      DynamicSet<PerformanceLogger> performanceLoggers,
      DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners,
      DynamicSet<SubmitRule> submitRules) {
    this.changeIndexedListeners = changeIndexedListeners;
    this.commitValidationListeners = commitValidationListeners;
    this.exceptionHooks = exceptionHooks;
    this.performanceLoggers = performanceLoggers;
    this.projectCreationValidationListeners = projectCreationValidationListeners;
    this.submitRules = submitRules;
  }

  public Registration newRegistration() {
    return new Registration();
  }

  public class Registration implements AutoCloseable {
    private final List<RegistrationHandle> registrationHandles = new ArrayList<>();

    public Registration add(ChangeIndexedListener changeIndexedListener) {
      return register(changeIndexedListeners, changeIndexedListener);
    }

    public Registration add(CommitValidationListener commitValidationListener) {
      return register(commitValidationListeners, commitValidationListener);
    }

    public Registration add(ExceptionHook exceptionHook) {
      return register(exceptionHooks, exceptionHook);
    }

    public Registration add(PerformanceLogger performanceLogger) {
      return register(performanceLoggers, performanceLogger);
    }

    public Registration add(ProjectCreationValidationListener projectCreationListener) {
      return register(projectCreationValidationListeners, projectCreationListener);
    }

    public Registration add(SubmitRule submitRule) {
      return register(submitRules, submitRule);
    }

    private <T> Registration register(DynamicSet<T> dynamicSet, T extension) {
      RegistrationHandle registrationHandle = dynamicSet.add("gerrit", extension);
      registrationHandles.add(registrationHandle);
      return this;
    }

    @Override
    public void close() {
      registrationHandles.forEach(h -> h.remove());
    }
  }
}
