// Copyright (C) 2024 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.DIRECT_PUSH;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.PluginPushOption;
import com.google.gerrit.server.ValidationOptionsListener;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.flow.Flow;
import com.google.gerrit.server.flow.FlowCreation;
import com.google.gerrit.server.flow.FlowExpression;
import com.google.gerrit.server.flow.FlowKey;
import com.google.gerrit.server.flow.FlowPermissionDeniedException;
import com.google.gerrit.server.flow.FlowService;
import com.google.gerrit.server.flow.FlowStage;
import com.google.gerrit.server.flow.FlowStageEvaluationStatus;
import com.google.gerrit.server.flow.InvalidFlowException;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationInfo;
import com.google.gerrit.server.git.validators.CommitValidationInfoListener;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.update.RetryListener;
import com.google.gerrit.server.update.context.RefUpdateContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Class to host common test extension implementations.
 *
 * <p>To test the invocation of an extension point tests usually register a test implementation for
 * the extension that records the parameters with which it has been called.
 *
 * <p>If the same extension point is triggered by different actions, these test extension
 * implementations may be needed in different test classes. To avoid duplicating them in the test
 * classes, they can be added to this class and then be reused from the different tests.
 */
public class TestExtensions {
  public static class TestCommitValidationListener implements CommitValidationListener {
    public CommitReceivedEvent receiveEvent;

    @Override
    public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
        throws CommitValidationException {
      this.receiveEvent = receiveEvent;
      return ImmutableList.of();
    }
  }

  public static class TestValidationOptionsListener implements ValidationOptionsListener {
    public ImmutableListMultimap<String, String> validationOptions;

    @Override
    public void onPatchSetCreation(
        BranchNameKey projectAndBranch,
        PatchSet.Id patchSetId,
        ImmutableListMultimap<String, String> validationOptions) {
      this.validationOptions = validationOptions;
    }
  }

  public static class TestCommitValidationInfoListener implements CommitValidationInfoListener {
    public ImmutableMap<String, CommitValidationInfo> validationInfoByValidator;
    public CommitReceivedEvent receiveEvent;
    @Nullable public PatchSet.Id patchSetId;
    public boolean hasChangeModificationRefContext;
    public boolean hasDirectPushRefContext;

    @Override
    public void commitValidated(
        ImmutableMap<String, CommitValidationInfo> validationInfoByValidator,
        CommitReceivedEvent receiveEvent,
        PatchSet.Id patchSetId) {
      this.validationInfoByValidator = validationInfoByValidator;
      this.receiveEvent = receiveEvent;
      this.patchSetId = patchSetId;
      this.hasChangeModificationRefContext = RefUpdateContext.hasOpen(CHANGE_MODIFICATION);
      this.hasDirectPushRefContext = RefUpdateContext.hasOpen(DIRECT_PUSH);
    }
  }

  public static class TestPluginPushOption implements PluginPushOption {
    private final String name;
    private final String description;
    private final Boolean enabled;

    public TestPluginPushOption(String name, String description, Boolean enabled) {
      this.name = name;
      this.description = description;
      this.enabled = enabled;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public String getDescription() {
      return description;
    }

    @Override
    public boolean isOptionEnabled(ChangeNotes changeNotes) {
      return enabled;
    }

    @Override
    public boolean isOptionEnabled(Project.NameKey project, BranchNameKey branch) {
      return enabled;
    }
  }

  public static class TestRetryListener implements RetryListener {
    private List<Retry> retries = new ArrayList<>();

    @Override
    public void onRetry(String actionType, String actionName, long nextAttempt, Throwable cause) {
      this.retries.add(new Retry(actionType, actionName, nextAttempt, cause));
    }

    public ImmutableList<Retry> getRetries() {
      return ImmutableList.copyOf(retries);
    }

    public Retry getOnlyRetry() {
      return Iterables.getOnlyElement(retries);
    }

    public record Retry(String actionType, String actionName, long nextAttempt, Throwable cause) {
      public Retry {
        requireNonNull(actionType, "actionType");
        requireNonNull(actionName, "actionName");
        requireNonNull(cause, "cause");
      }
    }
  }

  /** Test implementation of a {@link FlowService} to be used by the flow integration tests. */
  public static class TestFlowService implements FlowService {
    public static final String INVALID_CONDITION = "invalid";

    private final Map<FlowKey, Flow> flows = new HashMap<>();

    /**
     * Whether any flow creation should be rejected with a {@link FlowPermissionDeniedException}.
     */
    private boolean rejectFlowCreation;

    /**
     * Whether any flow deletion should be rejected with a {@link FlowPermissionDeniedException}.
     */
    private boolean rejectFlowDeletion;

    /** Makes the flow service reject all flow creations. */
    public void rejectFlowCreation() {
      this.rejectFlowCreation = true;
    }

    /** Makes the flow service reject all flow deletions. */
    public void rejectFlowDeletion() {
      this.rejectFlowDeletion = true;
    }

    @Override
    public Flow createFlow(FlowCreation flowCreation)
        throws FlowPermissionDeniedException, InvalidFlowException, StorageException {
      if (rejectFlowCreation) {
        throw new FlowPermissionDeniedException("not allowed to create flow");
      }

      if (flowCreation.stageExpressions().stream()
          .map(FlowExpression::condition)
          .anyMatch(condition -> condition.endsWith(INVALID_CONDITION))) {
        throw new InvalidFlowException(String.format("invalid condition: %s", INVALID_CONDITION));
      }

      FlowKey flowKey =
          FlowKey.builder()
              .projectName(flowCreation.projectName())
              .changeId(flowCreation.changeId())
              .uuid(ChangeUtil.messageUuid())
              .build();
      Flow flow =
          Flow.builder(flowKey)
              .createdOn(Instant.now())
              .ownerId(flowCreation.ownerId())
              .stages(
                  flowCreation.stageExpressions().stream()
                      .map(
                          stageExpression ->
                              FlowStage.builder().expression(stageExpression).build())
                      .collect(toImmutableList()))
              .build();
      flows.put(flowKey, flow);
      return flow;
    }

    @Override
    public Optional<Flow> getFlow(FlowKey flowKey) throws StorageException {
      return Optional.ofNullable(flows.get(flowKey));
    }

    @Override
    public Optional<Flow> deleteFlow(FlowKey flowKey)
        throws FlowPermissionDeniedException, StorageException {
      if (rejectFlowDeletion) {
        throw new FlowPermissionDeniedException("not allowed to delete flow");
      }

      return Optional.ofNullable(flows.remove(flowKey));
    }

    @Override
    public ImmutableList<Flow> listFlows(Project.NameKey projectName, Change.Id changeId)
        throws StorageException {
      return flows.entrySet().stream()
          .filter(
              e ->
                  e.getKey().projectName().equals(projectName)
                      && e.getKey().changeId().equals(changeId))
          .map(Map.Entry::getValue)
          .collect(toImmutableList());
    }

    /**
     * Updates the specified flow.
     *
     * <p>Sets the {@code lastEvaluatedOn} timestamp in the flow and updates the statuses of the
     * stages.
     *
     * @param flowKey the key of the flow that should be updated
     * @param stageStates states to be set for the stages
     * @param stageMessages messages to be set for the stages
     * @throws IllegalStateException thrown if the specified flow is not found, or if the number of
     *     given states/messages doesn't match with the number of stages in the flow
     * @return the updated flow
     */
    public Flow evaluate(
        FlowKey flowKey,
        ImmutableList<FlowStageEvaluationStatus.State> stageStates,
        ImmutableList<Optional<String>> stageMessages)
        throws IllegalStateException {
      Optional<Flow> flow = getFlow(flowKey);
      if (flow.isEmpty()) {
        throw new IllegalStateException(String.format("Flow %s not found.", flowKey));
      }
      if (stageStates.size() != flow.get().stages().size()) {
        throw new IllegalStateException(
            String.format(
                "Invalid number of stage states: got %s, expected %s",
                stageStates.size(), flow.get().stages().size()));
      }
      if (stageMessages.size() != flow.get().stages().size()) {
        throw new IllegalStateException(
            String.format(
                "Invalid number of stage messages: got %s, expected %s",
                stageMessages.size(), flow.get().stages().size()));
      }

      List<FlowStage> stages = new ArrayList<>(flow.get().stages());
      for (int i = 0; i < flow.get().stages().size(); i++) {
        FlowStageEvaluationStatus.Builder updatedStatus =
            stages.get(i).status().toBuilder().state(stageStates.get(i));
        if (stageMessages.get(i).isPresent()) {
          updatedStatus.message(stageMessages.get(i).get());
        }
        FlowStage updatedStage = stages.get(i).toBuilder().status(updatedStatus.build()).build();
        stages.set(i, updatedStage);
      }

      Flow updatedFlow =
          flow.get().toBuilder()
              .lastEvaluatedOn(Instant.now())
              .stages(ImmutableList.copyOf(stages))
              .build();
      flows.put(flowKey, updatedFlow);
      return updatedFlow;
    }
  }

  /**
   * Private constructor to prevent instantiation of this class.
   *
   * <p>This class contains only static classes and hence never needs to be instantiated.
   */
  private TestExtensions() {}
}
