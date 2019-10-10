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

import com.google.gerrit.extensions.api.changes.ActionVisitor;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.ExceptionHook;
import com.google.gerrit.server.change.ChangeETagComputation;
import com.google.gerrit.server.git.ChangeMessageModifier;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;
import com.google.gerrit.server.logging.PerformanceLogger;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.inject.Inject;
import com.google.inject.util.Providers;
import java.util.ArrayList;
import java.util.List;

public class ExtensionRegistry {
  private final DynamicSet<ChangeIndexedListener> changeIndexedListeners;
  private final DynamicSet<CommitValidationListener> commitValidationListeners;
  private final DynamicSet<ExceptionHook> exceptionHooks;
  private final DynamicSet<PerformanceLogger> performanceLoggers;
  private final DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners;
  private final DynamicSet<SubmitRule> submitRules;
  private final DynamicSet<ChangeMessageModifier> changeMessageModifiers;
  private final DynamicSet<ChangeETagComputation> changeETagComputations;
  private final DynamicSet<ActionVisitor> actionVisitors;
  private final DynamicMap<DownloadScheme> downloadSchemes;
  private final DynamicSet<RefOperationValidationListener> refOperationValidationListeners;

  @Inject
  ExtensionRegistry(
      DynamicSet<ChangeIndexedListener> changeIndexedListeners,
      DynamicSet<CommitValidationListener> commitValidationListeners,
      DynamicSet<ExceptionHook> exceptionHooks,
      DynamicSet<PerformanceLogger> performanceLoggers,
      DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners,
      DynamicSet<SubmitRule> submitRules,
      DynamicSet<ChangeMessageModifier> changeMessageModifiers,
      DynamicSet<ChangeETagComputation> changeETagComputations,
      DynamicSet<ActionVisitor> actionVisitors,
      DynamicMap<DownloadScheme> downloadSchemes,
      DynamicSet<RefOperationValidationListener> refOperationValidationListeners) {
    this.changeIndexedListeners = changeIndexedListeners;
    this.commitValidationListeners = commitValidationListeners;
    this.exceptionHooks = exceptionHooks;
    this.performanceLoggers = performanceLoggers;
    this.projectCreationValidationListeners = projectCreationValidationListeners;
    this.submitRules = submitRules;
    this.changeMessageModifiers = changeMessageModifiers;
    this.changeETagComputations = changeETagComputations;
    this.actionVisitors = actionVisitors;
    this.downloadSchemes = downloadSchemes;
    this.refOperationValidationListeners = refOperationValidationListeners;
  }

  public Registration newRegistration() {
    return new Registration();
  }

  public class Registration implements AutoCloseable {
    private final List<RegistrationHandle> registrationHandles = new ArrayList<>();

    public Registration add(ChangeIndexedListener changeIndexedListener) {
      return add(changeIndexedListeners, changeIndexedListener);
    }

    public Registration add(CommitValidationListener commitValidationListener) {
      return add(commitValidationListeners, commitValidationListener);
    }

    public Registration add(ExceptionHook exceptionHook) {
      return add(exceptionHooks, exceptionHook);
    }

    public Registration add(PerformanceLogger performanceLogger) {
      return add(performanceLoggers, performanceLogger);
    }

    public Registration add(ProjectCreationValidationListener projectCreationListener) {
      return add(projectCreationValidationListeners, projectCreationListener);
    }

    public Registration add(SubmitRule submitRule) {
      return add(submitRules, submitRule);
    }

    public Registration add(ChangeMessageModifier changeMessageModifier) {
      return add(changeMessageModifiers, changeMessageModifier);
    }

    public Registration add(ChangeETagComputation changeETagComputation) {
      return add(changeETagComputations, changeETagComputation);
    }

    public Registration add(ActionVisitor actionVisitor) {
      return add(actionVisitors, actionVisitor);
    }

    public Registration add(DownloadScheme downloadScheme, String exportName) {
      return add(downloadSchemes, downloadScheme, exportName);
    }

    public Registration add(RefOperationValidationListener refOperationValidationListener) {
      return add(refOperationValidationListeners, refOperationValidationListener);
    }

    private <T> Registration add(DynamicSet<T> dynamicSet, T extension) {
      RegistrationHandle registrationHandle = dynamicSet.add("gerrit", extension);
      registrationHandles.add(registrationHandle);
      return this;
    }

    private <T> Registration add(DynamicMap<T> dynamicMap, T extension, String exportName) {
      RegistrationHandle registrationHandle =
          ((PrivateInternals_DynamicMapImpl<T>) dynamicMap)
              .put("myPlugin", exportName, Providers.of(extension));
      registrationHandles.add(registrationHandle);
      return this;
    }

    @Override
    public void close() {
      registrationHandles.forEach(h -> h.remove());
    }
  }
}
