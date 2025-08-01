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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.extensions.api.changes.ActionVisitor;
import com.google.gerrit.extensions.config.CapabilityDefinition;
import com.google.gerrit.extensions.config.DownloadScheme;
import com.google.gerrit.extensions.config.PluginProjectPermissionDefinition;
import com.google.gerrit.extensions.events.AccountActivationListener;
import com.google.gerrit.extensions.events.AccountIndexedListener;
import com.google.gerrit.extensions.events.AttentionSetListener;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.events.CommentAddedListener;
import com.google.gerrit.extensions.events.GitBatchRefUpdateListener;
import com.google.gerrit.extensions.events.GitReferenceUpdatedListener;
import com.google.gerrit.extensions.events.GroupIndexedListener;
import com.google.gerrit.extensions.events.ProjectIndexedListener;
import com.google.gerrit.extensions.events.ReviewerAddedListener;
import com.google.gerrit.extensions.events.ReviewerDeletedListener;
import com.google.gerrit.extensions.events.RevisionCreatedListener;
import com.google.gerrit.extensions.events.TopicEditedListener;
import com.google.gerrit.extensions.events.WorkInProgressStateChangedListener;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.PrivateInternals_DynamicMapImpl;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.webui.EditWebLink;
import com.google.gerrit.extensions.webui.FileHistoryWebLink;
import com.google.gerrit.extensions.webui.FileWebLink;
import com.google.gerrit.extensions.webui.PatchSetWebLink;
import com.google.gerrit.extensions.webui.ResolveConflictsWebLink;
import com.google.gerrit.server.ExceptionHook;
import com.google.gerrit.server.PluginPushOption;
import com.google.gerrit.server.ServerStateProvider;
import com.google.gerrit.server.ValidationOptionsListener;
import com.google.gerrit.server.account.AccountStateProvider;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.change.FilterIncludedIn;
import com.google.gerrit.server.change.ReviewerSuggestion;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.flow.FlowService;
import com.google.gerrit.server.git.ChangeMessageModifier;
import com.google.gerrit.server.git.receive.PushOptionsValidator;
import com.google.gerrit.server.git.validators.CommitValidationInfoListener;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.OnSubmitValidationListener;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;
import com.google.gerrit.server.logging.PerformanceLogger;
import com.google.gerrit.server.query.approval.ApprovalQueryBuilder.UserInOperandFactory;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.ChangeHasOperandFactory;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.ChangeIsOperandFactory;
import com.google.gerrit.server.restapi.change.OnPostReview;
import com.google.gerrit.server.rules.SubmitRule;
import com.google.gerrit.server.update.RetryListener;
import com.google.gerrit.server.validators.AccountActivationValidationListener;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.inject.Inject;
import com.google.inject.util.Providers;
import java.util.ArrayList;
import java.util.List;

public class ExtensionRegistry {
  public static final String PLUGIN_NAME = "myPlugin";

  private final DynamicSet<AccountIndexedListener> accountIndexedListeners;
  private final DynamicSet<ChangeIndexedListener> changeIndexedListeners;
  private final DynamicSet<GroupIndexedListener> groupIndexedListeners;
  private final DynamicSet<ProjectIndexedListener> projectIndexedListeners;
  private final DynamicSet<CommitValidationListener> commitValidationListeners;
  private final DynamicSet<TopicEditedListener> topicEditedListeners;
  private final DynamicSet<ExceptionHook> exceptionHooks;
  private final DynamicSet<PerformanceLogger> performanceLoggers;
  private final DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners;
  private final DynamicSet<LabelType> labelTypes;
  private final DynamicSet<SubmitRule> submitRules;
  private final DynamicSet<SubmitRequirement> submitRequirements;
  private final DynamicSet<ChangeMessageModifier> changeMessageModifiers;
  private final DynamicSet<ActionVisitor> actionVisitors;
  private final DynamicMap<DownloadScheme> downloadSchemes;
  private final DynamicSet<RefOperationValidationListener> refOperationValidationListeners;
  private final DynamicSet<CommentAddedListener> commentAddedListeners;
  private final DynamicSet<GitReferenceUpdatedListener> refUpdatedListeners;
  private final DynamicSet<GitBatchRefUpdateListener> batchRefUpdateListeners;
  private final DynamicSet<FileHistoryWebLink> fileHistoryWebLinks;
  private final DynamicSet<FilterIncludedIn> filterIncludedIns;
  private final DynamicSet<PatchSetWebLink> patchSetWebLinks;
  private final DynamicSet<ResolveConflictsWebLink> resolveConflictsWebLinks;
  private final DynamicSet<EditWebLink> editWebLinks;
  private final DynamicSet<FileWebLink> fileWebLinks;
  private final DynamicSet<RevisionCreatedListener> revisionCreatedListeners;
  private final DynamicSet<GroupBackend> groupBackends;
  private final DynamicSet<AccountActivationValidationListener>
      accountActivationValidationListeners;
  private final DynamicSet<AccountActivationListener> accountActivationListeners;
  private final DynamicSet<OnSubmitValidationListener> onSubmitValidationListeners;
  private final DynamicSet<WorkInProgressStateChangedListener> workInProgressStateChangedListeners;
  private final DynamicMap<CapabilityDefinition> capabilityDefinitions;
  private final DynamicMap<PluginProjectPermissionDefinition> pluginProjectPermissionDefinitions;
  private final DynamicMap<ProjectConfigEntry> pluginConfigEntries;
  private final DynamicSet<PluginPushOption> pluginPushOptions;
  private final DynamicSet<OnPostReview> onPostReviews;
  private final DynamicSet<ReviewerAddedListener> reviewerAddedListeners;
  private final DynamicSet<ReviewerDeletedListener> reviewerDeletedListeners;
  private final DynamicSet<ServerStateProvider> serverStateProviders;
  private final DynamicSet<AccountStateProvider> accountStateProviders;
  private final DynamicSet<AttentionSetListener> attentionSetListeners;
  private final DynamicSet<ValidationOptionsListener> validationOptionsListeners;
  private final DynamicSet<CommitValidationInfoListener> commitValidationInfoListeners;
  private final DynamicSet<RetryListener> retryListeners;
  private final DynamicSet<PushOptionsValidator> pushOptionsValidators;

  private final DynamicMap<ChangeHasOperandFactory> hasOperands;
  private final DynamicMap<ChangeIsOperandFactory> isOperands;
  private final DynamicMap<UserInOperandFactory> userInOperands;
  private final DynamicMap<ReviewerSuggestion> reviewerSuggestions;

  private final DynamicItem<FlowService> flowService;

  @Inject
  ExtensionRegistry(
      DynamicSet<AccountIndexedListener> accountIndexedListeners,
      DynamicSet<ChangeIndexedListener> changeIndexedListeners,
      DynamicSet<GroupIndexedListener> groupIndexedListeners,
      DynamicSet<ProjectIndexedListener> projectIndexedListeners,
      DynamicSet<CommitValidationListener> commitValidationListeners,
      DynamicSet<TopicEditedListener> topicEditedListeners,
      DynamicSet<ExceptionHook> exceptionHooks,
      DynamicSet<PerformanceLogger> performanceLoggers,
      DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners,
      DynamicSet<LabelType> labelTypes,
      DynamicSet<SubmitRule> submitRules,
      DynamicSet<SubmitRequirement> submitRequirements,
      DynamicSet<ChangeMessageModifier> changeMessageModifiers,
      DynamicSet<ActionVisitor> actionVisitors,
      DynamicMap<DownloadScheme> downloadSchemes,
      DynamicSet<RefOperationValidationListener> refOperationValidationListeners,
      DynamicSet<CommentAddedListener> commentAddedListeners,
      DynamicSet<GitReferenceUpdatedListener> refUpdatedListeners,
      DynamicSet<GitBatchRefUpdateListener> batchRefUpdateListeners,
      DynamicSet<FileHistoryWebLink> fileHistoryWebLinks,
      DynamicSet<FilterIncludedIn> filterIncludedIns,
      DynamicSet<PatchSetWebLink> patchSetWebLinks,
      DynamicSet<ResolveConflictsWebLink> resolveConflictsWebLinks,
      DynamicSet<EditWebLink> editWebLinks,
      DynamicSet<FileWebLink> fileWebLinks,
      DynamicSet<RevisionCreatedListener> revisionCreatedListeners,
      DynamicSet<GroupBackend> groupBackends,
      DynamicSet<AccountActivationValidationListener> accountActivationValidationListeners,
      DynamicSet<AccountActivationListener> accountActivationListeners,
      DynamicSet<OnSubmitValidationListener> onSubmitValidationListeners,
      DynamicSet<WorkInProgressStateChangedListener> workInProgressStateChangedListeners,
      DynamicMap<CapabilityDefinition> capabilityDefinitions,
      DynamicMap<PluginProjectPermissionDefinition> pluginProjectPermissionDefinitions,
      DynamicMap<ProjectConfigEntry> pluginConfigEntries,
      DynamicSet<PluginPushOption> pluginPushOption,
      DynamicSet<OnPostReview> onPostReviews,
      DynamicSet<ReviewerAddedListener> reviewerAddedListeners,
      DynamicSet<ReviewerDeletedListener> reviewerDeletedListeners,
      DynamicMap<ChangeHasOperandFactory> hasOperands,
      DynamicMap<ChangeIsOperandFactory> isOperands,
      DynamicMap<UserInOperandFactory> userInOperands,
      DynamicSet<ServerStateProvider> serverStateProviders,
      DynamicSet<AccountStateProvider> accountStateProviders,
      DynamicSet<AttentionSetListener> attentionSetListeners,
      DynamicSet<ValidationOptionsListener> validationOptionsListeners,
      DynamicSet<CommitValidationInfoListener> commitValidationInfoListeners,
      DynamicSet<RetryListener> retryListeners,
      DynamicSet<PushOptionsValidator> pushOptionsValidator,
      DynamicMap<ReviewerSuggestion> reviewerSuggestions,
      DynamicItem<FlowService> flowService) {
    this.accountIndexedListeners = accountIndexedListeners;
    this.changeIndexedListeners = changeIndexedListeners;
    this.groupIndexedListeners = groupIndexedListeners;
    this.projectIndexedListeners = projectIndexedListeners;
    this.commitValidationListeners = commitValidationListeners;
    this.topicEditedListeners = topicEditedListeners;
    this.exceptionHooks = exceptionHooks;
    this.performanceLoggers = performanceLoggers;
    this.projectCreationValidationListeners = projectCreationValidationListeners;
    this.labelTypes = labelTypes;
    this.submitRules = submitRules;
    this.submitRequirements = submitRequirements;
    this.changeMessageModifiers = changeMessageModifiers;
    this.actionVisitors = actionVisitors;
    this.downloadSchemes = downloadSchemes;
    this.refOperationValidationListeners = refOperationValidationListeners;
    this.commentAddedListeners = commentAddedListeners;
    this.refUpdatedListeners = refUpdatedListeners;
    this.batchRefUpdateListeners = batchRefUpdateListeners;
    this.fileHistoryWebLinks = fileHistoryWebLinks;
    this.filterIncludedIns = filterIncludedIns;
    this.patchSetWebLinks = patchSetWebLinks;
    this.editWebLinks = editWebLinks;
    this.fileWebLinks = fileWebLinks;
    this.resolveConflictsWebLinks = resolveConflictsWebLinks;
    this.revisionCreatedListeners = revisionCreatedListeners;
    this.groupBackends = groupBackends;
    this.accountActivationValidationListeners = accountActivationValidationListeners;
    this.accountActivationListeners = accountActivationListeners;
    this.onSubmitValidationListeners = onSubmitValidationListeners;
    this.workInProgressStateChangedListeners = workInProgressStateChangedListeners;
    this.capabilityDefinitions = capabilityDefinitions;
    this.pluginProjectPermissionDefinitions = pluginProjectPermissionDefinitions;
    this.pluginConfigEntries = pluginConfigEntries;
    this.pluginPushOptions = pluginPushOption;
    this.onPostReviews = onPostReviews;
    this.reviewerAddedListeners = reviewerAddedListeners;
    this.reviewerDeletedListeners = reviewerDeletedListeners;
    this.hasOperands = hasOperands;
    this.isOperands = isOperands;
    this.userInOperands = userInOperands;
    this.serverStateProviders = serverStateProviders;
    this.accountStateProviders = accountStateProviders;
    this.attentionSetListeners = attentionSetListeners;
    this.validationOptionsListeners = validationOptionsListeners;
    this.commitValidationInfoListeners = commitValidationInfoListeners;
    this.retryListeners = retryListeners;
    this.pushOptionsValidators = pushOptionsValidator;
    this.reviewerSuggestions = reviewerSuggestions;
    this.flowService = flowService;
  }

  public Registration newRegistration() {
    return new Registration();
  }

  @SuppressWarnings("FunctionalInterfaceClash")
  public class Registration implements AutoCloseable {
    private final List<RegistrationHandle> registrationHandles = new ArrayList<>();

    @CanIgnoreReturnValue
    public Registration add(AccountIndexedListener accountIndexedListener) {
      return add(accountIndexedListeners, accountIndexedListener);
    }

    @CanIgnoreReturnValue
    public Registration add(ChangeIndexedListener changeIndexedListener) {
      return add(changeIndexedListeners, changeIndexedListener);
    }

    @CanIgnoreReturnValue
    public Registration add(GroupIndexedListener groupIndexedListener) {
      return add(groupIndexedListeners, groupIndexedListener);
    }

    @CanIgnoreReturnValue
    public Registration add(ProjectIndexedListener projectIndexedListener) {
      return add(projectIndexedListeners, projectIndexedListener);
    }

    @CanIgnoreReturnValue
    public Registration add(CommitValidationListener commitValidationListener) {
      return add(commitValidationListeners, commitValidationListener);
    }

    @CanIgnoreReturnValue
    public Registration add(TopicEditedListener topicEditedListener) {
      return add(topicEditedListeners, topicEditedListener);
    }

    @CanIgnoreReturnValue
    public Registration add(ExceptionHook exceptionHook) {
      return add(exceptionHooks, exceptionHook);
    }

    @CanIgnoreReturnValue
    public Registration add(PerformanceLogger performanceLogger) {
      return add(performanceLoggers, performanceLogger);
    }

    @CanIgnoreReturnValue
    public Registration add(ProjectCreationValidationListener projectCreationListener) {
      return add(projectCreationValidationListeners, projectCreationListener);
    }

    @CanIgnoreReturnValue
    public Registration add(LabelType labelType) {
      return add(labelTypes, labelType);
    }

    @CanIgnoreReturnValue
    public Registration add(SubmitRule submitRule) {
      return add(submitRules, submitRule);
    }

    @CanIgnoreReturnValue
    public Registration add(SubmitRequirement submitRequirement) {
      return add(submitRequirements, submitRequirement);
    }

    @CanIgnoreReturnValue
    public Registration add(ChangeHasOperandFactory hasOperand, String exportName) {
      return add(hasOperands, hasOperand, exportName);
    }

    @CanIgnoreReturnValue
    public Registration add(ChangeIsOperandFactory isOperand, String exportName) {
      return add(isOperands, isOperand, exportName);
    }

    @CanIgnoreReturnValue
    public Registration add(UserInOperandFactory userInOperand, String exportName) {
      return add(userInOperands, userInOperand, exportName);
    }

    @CanIgnoreReturnValue
    public Registration add(ChangeMessageModifier changeMessageModifier) {
      return add(changeMessageModifiers, changeMessageModifier);
    }

    @CanIgnoreReturnValue
    public Registration add(ChangeMessageModifier changeMessageModifier, String exportName) {
      return add(changeMessageModifiers, changeMessageModifier, exportName);
    }

    @CanIgnoreReturnValue
    public Registration add(ActionVisitor actionVisitor) {
      return add(actionVisitors, actionVisitor);
    }

    @CanIgnoreReturnValue
    public Registration add(DownloadScheme downloadScheme, String exportName) {
      return add(downloadSchemes, downloadScheme, exportName);
    }

    @CanIgnoreReturnValue
    public Registration add(RefOperationValidationListener refOperationValidationListener) {
      return add(refOperationValidationListeners, refOperationValidationListener);
    }

    @CanIgnoreReturnValue
    public Registration add(CommentAddedListener commentAddedListener) {
      return add(commentAddedListeners, commentAddedListener);
    }

    @CanIgnoreReturnValue
    public Registration add(GitReferenceUpdatedListener refUpdatedListener) {
      return add(refUpdatedListeners, refUpdatedListener);
    }

    @CanIgnoreReturnValue
    public Registration add(GitBatchRefUpdateListener batchRefUpdateListener) {
      return add(batchRefUpdateListeners, batchRefUpdateListener);
    }

    @CanIgnoreReturnValue
    public Registration add(FileHistoryWebLink fileHistoryWebLink) {
      return add(fileHistoryWebLinks, fileHistoryWebLink);
    }

    @CanIgnoreReturnValue
    public Registration add(FilterIncludedIn filterIncludedIn) {
      return add(filterIncludedIns, filterIncludedIn);
    }

    @CanIgnoreReturnValue
    public Registration add(PatchSetWebLink patchSetWebLink) {
      return add(patchSetWebLinks, patchSetWebLink);
    }

    @CanIgnoreReturnValue
    public Registration add(ResolveConflictsWebLink resolveConflictsWebLink) {
      return add(resolveConflictsWebLinks, resolveConflictsWebLink);
    }

    @CanIgnoreReturnValue
    public Registration add(EditWebLink editWebLink) {
      return add(editWebLinks, editWebLink);
    }

    @CanIgnoreReturnValue
    public Registration add(FileWebLink fileWebLink) {
      return add(fileWebLinks, fileWebLink);
    }

    @CanIgnoreReturnValue
    public Registration add(RevisionCreatedListener revisionCreatedListener) {
      return add(revisionCreatedListeners, revisionCreatedListener);
    }

    @CanIgnoreReturnValue
    public Registration add(GroupBackend groupBackend) {
      return add(groupBackends, groupBackend);
    }

    @CanIgnoreReturnValue
    public Registration add(
        AccountActivationValidationListener accountActivationValidationListener) {
      return add(accountActivationValidationListeners, accountActivationValidationListener);
    }

    @CanIgnoreReturnValue
    public Registration add(AccountActivationListener accountDeactivatedListener) {
      return add(accountActivationListeners, accountDeactivatedListener);
    }

    @CanIgnoreReturnValue
    public Registration add(OnSubmitValidationListener onSubmitValidationListener) {
      return add(onSubmitValidationListeners, onSubmitValidationListener);
    }

    @CanIgnoreReturnValue
    public Registration add(WorkInProgressStateChangedListener workInProgressStateChangedListener) {
      return add(workInProgressStateChangedListeners, workInProgressStateChangedListener);
    }

    @CanIgnoreReturnValue
    public Registration add(ServerStateProvider serverStateProvider) {
      return add(serverStateProviders, serverStateProvider);
    }

    @CanIgnoreReturnValue
    public Registration add(AccountStateProvider accountStateProvider) {
      return add(accountStateProviders, accountStateProvider);
    }

    @CanIgnoreReturnValue
    public Registration add(AttentionSetListener attentionSetListener) {
      return add(attentionSetListeners, attentionSetListener);
    }

    @CanIgnoreReturnValue
    public Registration add(ValidationOptionsListener validationOptionsListener) {
      return add(validationOptionsListeners, validationOptionsListener);
    }

    @CanIgnoreReturnValue
    public Registration add(CommitValidationInfoListener commitValidationInfoListener) {
      return add(commitValidationInfoListeners, commitValidationInfoListener);
    }

    @CanIgnoreReturnValue
    public Registration add(RetryListener retryListener) {
      return add(retryListeners, retryListener);
    }

    @CanIgnoreReturnValue
    public Registration add(PushOptionsValidator pushOptionsValidator) {
      return add(pushOptionsValidators, pushOptionsValidator);
    }

    @CanIgnoreReturnValue
    public Registration add(CapabilityDefinition capabilityDefinition, String exportName) {
      return add(capabilityDefinitions, capabilityDefinition, exportName);
    }

    @CanIgnoreReturnValue
    public Registration add(
        PluginProjectPermissionDefinition pluginProjectPermissionDefinition, String exportName) {
      return add(pluginProjectPermissionDefinitions, pluginProjectPermissionDefinition, exportName);
    }

    @CanIgnoreReturnValue
    public Registration add(ProjectConfigEntry pluginConfigEntry, String exportName) {
      return add(pluginConfigEntries, pluginConfigEntry, exportName);
    }

    @CanIgnoreReturnValue
    public Registration add(PluginPushOption pluginPushOption) {
      return add(pluginPushOptions, pluginPushOption);
    }

    @CanIgnoreReturnValue
    public Registration add(OnPostReview onPostReview) {
      return add(onPostReviews, onPostReview);
    }

    @CanIgnoreReturnValue
    public Registration add(ReviewerAddedListener reviewerAddedListener) {
      return add(reviewerAddedListeners, reviewerAddedListener);
    }

    @CanIgnoreReturnValue
    public Registration add(ReviewerDeletedListener reviewerDeletedListener) {
      return add(reviewerDeletedListeners, reviewerDeletedListener);
    }

    @CanIgnoreReturnValue
    public Registration add(ReviewerSuggestion reviewerSuggestion, String exportName) {
      return add(reviewerSuggestions, reviewerSuggestion, exportName);
    }

    @CanIgnoreReturnValue
    public Registration set(FlowService flowService) {
      return set(ExtensionRegistry.this.flowService, flowService);
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
              .put(PLUGIN_NAME, exportName, Providers.of(extension));
      registrationHandles.add(registrationHandle);
      return this;
    }

    private <T> Registration set(DynamicItem<T> dynamicItem, T extension) {
      RegistrationHandle registrationHandle = dynamicItem.set(extension, "gerrit");
      registrationHandles.add(registrationHandle);
      return this;
    }

    @Override
    public void close() {
      registrationHandles.forEach(h -> h.remove());
    }
  }
}
