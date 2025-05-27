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

package com.google.gerrit.acceptance.api.flow;

import static com.google.gerrit.extensions.common.testing.FlowInfoSubject.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.FlowActionInfo;
import com.google.gerrit.extensions.common.FlowExpressionInfo;
import com.google.gerrit.extensions.common.FlowInfo;
import com.google.gerrit.extensions.common.FlowInput;
import com.google.gerrit.extensions.common.FlowStageStatus;
import com.google.gerrit.extensions.common.testing.FlowStageInfoSubject;
import com.google.gerrit.server.flow.Flow;
import com.google.gerrit.server.flow.FlowAction;
import com.google.gerrit.server.flow.FlowCreation;
import com.google.gerrit.server.flow.FlowExpression;
import com.google.gerrit.server.flow.FlowStage;
import com.google.gerrit.server.restapi.flow.FlowJson;
import java.time.Instant;

/**
 * Methods to create and assert flow entities that are shared between the different flow integration
 * tests.
 */
public class FlowTestUtil {
  public static FlowInput createTestFlowInput(AccountCreator accountCreator, Change.Id changeId)
      throws Exception {
    return createTestFlowInput(accountCreator, changeId, 1);
  }

  public static FlowInput createTestFlowInput(
      AccountCreator accountCreator, Change.Id changeId, int numberOfStages) throws Exception {
    FlowInput flowInput = new FlowInput();

    ImmutableList.Builder<FlowExpressionInfo> stageExpressionsBuilder = ImmutableList.builder();
    for (int i = 0; i < numberOfStages; i++) {
      FlowExpressionInfo flowExpressionInfo = new FlowExpressionInfo();
      flowExpressionInfo.condition =
          String.format("com.google.gerrit[change:%s label:Verified+%s]", changeId, i);

      FlowActionInfo flowActionInfo = new FlowActionInfo();
      flowActionInfo.name = "AddReviewer";
      flowActionInfo.parameters =
          ImmutableMap.of("user", accountCreator.createValid("reviewer" + i).email());
      flowExpressionInfo.action = flowActionInfo;

      stageExpressionsBuilder.add(flowExpressionInfo);
    }

    flowInput.stageExpressions = stageExpressionsBuilder.build();

    return flowInput;
  }

  public static void assertFlowInfoForNewlyCreatedFlow(
      FlowInfo flowInfo, FlowInput flowInput, TestAccount creator, Instant beforeInstant) {
    assertThat(flowInfo).hasUuidThat().isNotEmpty();
    assertThat(flowInfo).hasOwnerThat().hasAccountIdThat().isEqualTo(creator.id());
    assertThat(flowInfo).hasCreatedThat().isAtLeast(beforeInstant);
    assertThat(flowInfo).hasLastEvaluated().isEmpty();

    assertThat(flowInfo).hasStagesThat().hasSize(flowInput.stageExpressions.size());

    for (int i = 0; i < flowInput.stageExpressions.size(); i++) {
      FlowExpressionInfo flowExpressionInfo = flowInput.stageExpressions.get(i);
      FlowStageInfoSubject stageSubject = assertThat(flowInfo).hasStagesThat().element(i);
      stageSubject.hasStatusThat().isEqualTo(FlowStageStatus.PENDING);
      stageSubject.hasExpressionThat().hasConditionThat().isEqualTo(flowExpressionInfo.condition);
      stageSubject
          .hasExpressionThat()
          .hasActionThat()
          .hasNameThat()
          .isEqualTo(flowExpressionInfo.action.name);
      stageSubject
          .hasExpressionThat()
          .hasActionThat()
          .hasParametersThat()
          .isEqualTo(
              flowExpressionInfo.action.parameters != null
                  ? flowExpressionInfo.action.parameters
                  : ImmutableMap.of());
    }
  }

  public static FlowCreation createTestFlowCreation(
      AccountCreator accountCreator, Project.NameKey projectName, Change.Id changeId)
      throws Exception {
    return createTestFlowCreation(accountCreator, projectName, changeId, 1);
  }

  public static FlowCreation createTestFlowCreation(
      AccountCreator accountCreator,
      Project.NameKey projectName,
      Change.Id changeId,
      int numberOfStages)
      throws Exception {
    FlowCreation.Builder flowCreationBuilder =
        FlowCreation.builder()
            .projectName(projectName)
            .changeId(changeId)
            .ownerId(accountCreator.createValid("owner").id());

    for (int i = 0; i < numberOfStages; i++) {
      flowCreationBuilder.addStageExpression(
          FlowExpression.builder()
              .condition(
                  String.format("com.google.gerrit[change:%s label:Verified+%s]", changeId, i))
              .action(
                  FlowAction.builder()
                      .name("AddReviewer")
                      .addParameter("user", accountCreator.createValid("reviewer" + i).email())
                      .build())
              .build());
    }

    return flowCreationBuilder.build();
  }

  public static void assertFlowInfo(FlowInfo flowInfo, Flow flow) {
    assertThat(flowInfo).hasUuidThat().isEqualTo(flow.key().uuid());
    assertThat(flowInfo).hasOwnerThat().hasAccountIdThat().isEqualTo(flow.ownerId());
    assertThat(flowInfo).hasCreatedThat().isEqualTo(flow.createdOn());
    assertThat(flowInfo).hasLastEvaluated().isEqualTo(flow.lastEvaluatedOn());

    assertThat(flowInfo).hasStagesThat().hasSize(flow.stages().size());

    for (int i = 0; i < flow.stages().size(); i++) {
      FlowStage flowStage = flow.stages().get(i);

      FlowStageInfoSubject stageSubject = assertThat(flowInfo).hasStagesThat().element(i);
      stageSubject.hasStatusThat().isEqualTo(FlowJson.mapStatus(flowStage.status()));
      stageSubject
          .hasExpressionThat()
          .hasConditionThat()
          .isEqualTo(flowStage.expression().condition());
      stageSubject
          .hasExpressionThat()
          .hasActionThat()
          .hasNameThat()
          .isEqualTo(flowStage.expression().action().name());
      stageSubject
          .hasExpressionThat()
          .hasActionThat()
          .hasParametersThat()
          .isEqualTo(flowStage.expression().action().parameters());
    }
  }

  /**
   * Private constructor to prevent instantiation of this class.
   *
   * <p>This class contains only static methods and hence never needs to be instantiated.
   */
  private FlowTestUtil() {}
}
