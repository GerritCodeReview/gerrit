// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.flow;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.Objects.requireNonNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.FlowActionInfo;
import com.google.gerrit.extensions.common.FlowExpressionInfo;
import com.google.gerrit.extensions.common.FlowInfo;
import com.google.gerrit.extensions.common.FlowInput;
import com.google.gerrit.extensions.common.FlowStageInfo;
import com.google.gerrit.extensions.common.FlowStageStatus;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.flow.Flow;
import com.google.gerrit.server.flow.FlowAction;
import com.google.gerrit.server.flow.FlowCreation;
import com.google.gerrit.server.flow.FlowExpression;
import com.google.gerrit.server.flow.FlowStage;

/**
 * Produces flow-related entities, like {@link FlowInfo}s, which are serialized to JSON afterwards.
 */
public class FlowJson {
  /** Formats the given {@link Flow} instance as a {@link FlowInfo}. */
  public static FlowInfo format(Flow flow) {
    requireNonNull(flow, "flow");

    FlowInfo flowInfo = new FlowInfo();
    flowInfo.uuid = flow.key().uuid();
    flowInfo.owner = new AccountInfo(flow.ownerId().get());
    flowInfo.setCreated(flow.createdOn());
    flowInfo.stages = flow.stages().stream().map(FlowJson::format).collect(toImmutableList());

    if (flow.lastEvaluatedOn().isPresent()) {
      flowInfo.setLastEvaluated(flow.lastEvaluatedOn().get());
    }

    return flowInfo;
  }

  /** Formats the given {@link FlowStage} instance as a {@link FlowStageInfo}. */
  private static FlowStageInfo format(FlowStage flowStage) {
    requireNonNull(flowStage, "flowStage");

    FlowStageInfo flowStageInfo = new FlowStageInfo();
    flowStageInfo.expression = format(flowStage.expression());
    flowStageInfo.status = mapStatus(flowStage.status());
    flowStageInfo.message = flowStage.message().orElse(null);
    return flowStageInfo;
  }

  /** Formats the given {@link FlowExpression} instance as a {@link FlowExpressionInfo}. */
  private static FlowExpressionInfo format(FlowExpression flowExpression) {
    requireNonNull(flowExpression, "flowExpression");

    FlowExpressionInfo flowExpressionInfo = new FlowExpressionInfo();
    flowExpressionInfo.condition = flowExpression.condition();
    flowExpressionInfo.action = format(flowExpression.action());
    return flowExpressionInfo;
  }

  /** Formats the given {@link FlowAction} instance as a {@link FlowActionInfo}. */
  private static FlowActionInfo format(FlowAction flowAction) {
    requireNonNull(flowAction, "flowAction");

    FlowActionInfo flowActionInfo = new FlowActionInfo();
    flowActionInfo.name = flowAction.name();
    flowActionInfo.parameters = flowAction.parameters();
    return flowActionInfo;
  }

  /**
   * Maps the given {@link com.google.gerrit.server.flow.FlowStage.Status} to a {@link
   * FlowStageStatus}.
   */
  @VisibleForTesting
  public static FlowStageStatus mapStatus(FlowStage.Status flowStageStatus) {
    requireNonNull(flowStageStatus, "flowStageStatus");

    return switch (flowStageStatus) {
      case DONE -> FlowStageStatus.DONE;
      case PENDING -> FlowStageStatus.PENDING;
      case FAILED -> FlowStageStatus.FAILED;
      case TERMINATED -> FlowStageStatus.TERMINATED;
    };
  }

  /**
   * Create a {@link FlowCreation} from the given {@link FlowInput}.
   *
   * @throws BadRequestException thrown if mandatory properties are missing
   */
  public static FlowCreation createFlowCreation(
      Project.NameKey projectName, Change.Id changeId, Account.Id ownerId, FlowInput flowInput)
      throws BadRequestException {
    requireNonNull(projectName, "projectName");
    requireNonNull(changeId, "changeId");
    requireNonNull(ownerId, "ownerId");
    requireNonNull(flowInput, "flowInput");

    if (flowInput.stageExpressions == null || flowInput.stageExpressions.isEmpty()) {
      throw new BadRequestException("at least one stage expression is required");
    }

    FlowCreation.Builder flowCreationBuilder =
        FlowCreation.builder().projectName(projectName).changeId(changeId).ownerId(ownerId);

    for (FlowExpressionInfo flowExpressionInfo : flowInput.stageExpressions) {
      flowCreationBuilder.addStageExpression(createFlowExpression(flowExpressionInfo));
    }

    return flowCreationBuilder.build();
  }

  /**
   * Create a {@link FlowExpression} from the given {@link FlowExpressionInfo}.
   *
   * @throws BadRequestException thrown if mandatory properties are missing
   */
  public static FlowExpression createFlowExpression(FlowExpressionInfo flowExpressionInfo)
      throws BadRequestException {
    requireNonNull(flowExpressionInfo, "flowExpressionInfo");

    if (Strings.isNullOrEmpty(flowExpressionInfo.condition)) {
      throw new BadRequestException("condition in stage expression is required");
    }
    if (flowExpressionInfo.action == null) {
      throw new BadRequestException("action in stage expression is required");
    }

    return FlowExpression.builder()
        .condition(flowExpressionInfo.condition)
        .action(createFlowAction(flowExpressionInfo.action))
        .build();
  }

  /**
   * Create a {@link FlowAction} from the given {@link FlowActionInfo}.
   *
   * @throws BadRequestException thrown if mandatory properties are missing
   */
  public static FlowAction createFlowAction(FlowActionInfo flowActionInfo)
      throws BadRequestException {
    requireNonNull(flowActionInfo, "flowActionInfo");

    if (Strings.isNullOrEmpty(flowActionInfo.name)) {
      throw new BadRequestException("name in action is required");
    }

    FlowAction.Builder flowActionBuilder = FlowAction.builder().name(flowActionInfo.name);

    if (flowActionInfo.parameters != null) {
      flowActionBuilder.parameters(flowActionInfo.parameters);
    }

    return flowActionBuilder.build();
  }
}
