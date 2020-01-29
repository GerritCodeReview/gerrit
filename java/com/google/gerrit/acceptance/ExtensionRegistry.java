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
import com.google.gerrit.extensions.events.AccountIndexedListener;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.events.GroupIndexedListener;
import com.google.gerrit.extensions.events.ProjectIndexedListener;
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.gerrit.extensions.events.WorkInProgressStateChangedListener;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.webui.FileHistoryWebLink;
import com.google.gerrit.extensions.webui.PatchSetWebLink;
import com.google.gerrit.server.ExceptionHook;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.change.ChangeETagComputation;
import com.google.gerrit.server.git.ChangeMessageModifier;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.OnSubmitValidationListener;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;
import com.google.gerrit.server.logging.PerformanceLogger;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.gerrit.server.validators.AccountActivationValidationListener;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.inject.Inject;
import com.google.inject.util.Providers;
import java.util.ArrayList;
import java.util.List;

public class ExtensionRegistry {
  private final DynamicSet<AccountIndexedListener> accountIndexedListeners;
  private final DynamicSet<ChangeIndexedListener> changeIndexedListeners;
  private final DynamicSet<GroupIndexedListener> groupIndexedListeners;
  private final DynamicSet<ProjectIndexedListener> projectIndexedListeners;
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
  private final DynamicSet<CommentAddedListener> commentAddedListeners;
  private final DynamicSet<GitReferenceUpdatedListener> refUpdatedListeners;
  private final DynamicSet<FileHistoryWebLink> fileHistoryWebLinks;
  private final DynamicSet<PatchSetWebLink> patchSetWebLinks;
  private final DynamicSet<RevisionCreatedListener> revisionCreatedListeners;
  private final DynamicSet<GroupBackend> groupBackends;
  private final DynamicSet<AccountActivationValidationListener>
      accountActivationValidationListeners;
  private final DynamicSet<OnSubmitValidationListener> onSubmitValidationListeners;
  private final DynamicSet<WorkInProgressStateChangedListener> workInProgressStateChangedListeners;

  @Inject
  ExtensionRegistry(
      DynamicSet<AccountIndexedListener> accountIndexedListeners,
      DynamicSet<ChangeIndexedListener> changeIndexedListeners,
      DynamicSet<GroupIndexedListener> groupIndexedListeners,
      DynamicSet<ProjectIndexedListener> projectIndexedListeners,
      DynamicSet<CommitValidationListener> commitValidationListeners,
      DynamicSet<ExceptionHook> exceptionHooks,
      DynamicSet<PerformanceLogger> performanceLoggers,
      DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners,
      DynamicSet<SubmitRule> submitRules,
      DynamicSet<ChangeMessageModifier> changeMessageModifiers,
      DynamicSet<ChangeETagComputation> changeETagComputations,
      DynamicSet<ActionVisitor> actionVisitors,
      DynamicMap<DownloadScheme> downloadSchemes,
      DynamicSet<RefOperationValidationListener> refOperationValidationListeners,
      DynamicSet<CommentAddedListener> commentAddedListeners,
      DynamicSet<GitReferenceUpdatedListener> refUpdatedListeners,
      DynamicSet<FileHistoryWebLink> fileHistoryWebLinks,
      DynamicSet<PatchSetWebLink> patchSetWebLinks,
      DynamicSet<RevisionCreatedListener> revisionCreatedListeners,
      DynamicSet<GroupBackend> groupBackends,
      DynamicSet<AccountActivationValidationListener> accountActivationValidationListeners,
      DynamicSet<OnSubmitValidationListener> onSubmitValidationListeners,
      DynamicSet<WorkInProgressStateChangedListener> workInProgressStateChangedListeners) {
    this.accountIndexedListeners = accountIndexedListeners;
    this.changeIndexedListeners = changeIndexedListeners;
    this.groupIndexedListeners = groupIndexedListeners;
    this.projectIndexedListeners = projectIndexedListeners;
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
    this.commentAddedListeners = commentAddedListeners;
    this.refUpdatedListeners = refUpdatedListeners;
    this.fileHistoryWebLinks = fileHistoryWebLinks;
    this.patchSetWebLinks = patchSetWebLinks;
    this.revisionCreatedListeners = revisionCreatedListeners;
    this.groupBackends = groupBackends;
    this.accountActivationValidationListeners = accountActivationValidationListeners;
    this.onSubmitValidationListeners = onSubmitValidationListeners;
    this.workInProgressStateChangedListeners = workInProgressStateChangedListeners;
  }

  public Registration newRegistration() {
    return new Registration();
  }

  @SuppressWarnings("FunctionalInterfaceClash")
  public class Registration implements AutoCloseable {
    private final List<RegistrationHandle> registrationHandles = new ArrayList<>();

    public Registration add(AccountIndexedListener accountIndexedListener) {
      return add(accountIndexedListeners, accountIndexedListener);
    }

    public Registration add(ChangeIndexedListener changeIndexedListener) {
      return add(changeIndexedListeners, changeIndexedListener);
    }

    public Registration add(GroupIndexedListener groupIndexedListener) {
      return add(groupIndexedListeners, groupIndexedListener);
    }

    public Registration add(ProjectIndexedListener projectIndexedListener) {
      return add(projectIndexedListeners, projectIndexedListener);
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

    public Registration add(ChangeMessageModifier changeMessageModifier, String exportName) {
      return add(changeMessageModifiers, changeMessageModifier, exportName);
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

    public Registration add(CommentAddedListener commentAddedListener) {
      return add(commentAddedListeners, commentAddedListener);
    }

    public Registration add(GitReferenceUpdatedListener refUpdatedListener) {
      return add(refUpdatedListeners, refUpdatedListener);
    }

    public Registration add(FileHistoryWebLink fileHistoryWebLink) {
      return add(fileHistoryWebLinks, fileHistoryWebLink);
    }

    public Registration add(PatchSetWebLink patchSetWebLink) {
      return add(patchSetWebLinks, patchSetWebLink);
    }

    public Registration add(RevisionCreatedListener revisionCreatedListener) {
      return add(revisionCreatedListeners, revisionCreatedListener);
    }

    public Registration add(GroupBackend groupBackend) {
      return add(groupBackends, groupBackend);
    }

    public Registration add(
        AccountActivationValidationListener accountActivationValidationListener) {
      return add(accountActivationValidationListeners, accountActivationValidationListener);
    }

    public Registration add(OnSubmitValidationListener onSubmitValidationListener) {
      return add(onSubmitValidationListeners, onSubmitValidationListener);
    }

    public Registration add(WorkInProgressStateChangedListener workInProgressStateChangedListener) {
      return add(workInProgressStateChangedListeners, workInProgressStateChangedListener);
    }

    private <T> Registration add(DynamicSet<T> dynamicSet, T extension) {
      return add(dynamicSet, extension, "gerrit");
    }

    private <T> Registration add(DynamicSet<T> dynamicSet, T extension, String exportname) {
      RegistrationHandle registrationHandle = dynamicSet.add(exportname, extension);
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
