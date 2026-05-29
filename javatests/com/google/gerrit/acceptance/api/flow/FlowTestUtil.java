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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.TestExtensions.TestFlowService;
import com.google.gerrit.acceptance.testsuite.change.TestChange;
import com.google.gerrit.extensions.api.changes.ChangeIdentifier;
import com.google.gerrit.extensions.common.FlowActionInfo;
import com.google.gerrit.extensions.common.FlowExpressionInfo;
import com.google.gerrit.extensions.common.FlowInput;
import com.google.gerrit.server.flow.FlowAction;
import com.google.gerrit.server.flow.FlowCreation;
import com.google.gerrit.server.flow.FlowExpression;

/**
 * Methods to create and assert flow entities that are shared between the different flow integration
 * tests.
 */
public class FlowTestUtil {
  /** Creates a {@link FlowInput} with arbitrary test data that contains one stage. */
  public static FlowInput createTestFlowInputWithOneStage(
      AccountCreator accountCreator, ChangeIdentifier changeIdentifier) throws Exception {
    return createTestFlowInput(accountCreator, changeIdentifier, 1);
  }

  /** Creates a {@link FlowInput} with arbitrary test data that contains multiple stages. */
  public static FlowInput createTestFlowInputWithMultipleStages(
      AccountCreator accountCreator, ChangeIdentifier changeIdentifier) throws Exception {
    return createTestFlowInput(accountCreator, changeIdentifier, 3);
  }

  /** Creates a {@link FlowInput} with arbitrary test data that contains n stages. */
  public static FlowInput createTestFlowInputWithNStages(
      AccountCreator accountCreator, ChangeIdentifier changeIdentifier, int numberOfStages)
      throws Exception {
    return createTestFlowInput(accountCreator, changeIdentifier, numberOfStages);
  }

  /** Creates a {@link FlowInput} with arbitrary test data that contains an invalid condition. */
  public static FlowInput createTestFlowInputWithInvalidCondition(
      AccountCreator accountCreator, ChangeIdentifier changeIdentifier) throws Exception {
    FlowInput flowInput = createTestFlowInput(accountCreator, changeIdentifier, 1);
    flowInput.stageExpressions.get(0).condition = TestFlowService.INVALID_CONDITION;
    return flowInput;
  }

  /** Creates a {@link FlowInput} with arbitrary test data and as many stages as specified. */
  private static FlowInput createTestFlowInput(
      AccountCreator accountCreator, ChangeIdentifier changeIdentifier, int numberOfStages)
      throws Exception {
    FlowInput flowInput = new FlowInput();

    ImmutableList.Builder<FlowExpressionInfo> stageExpressionsBuilder = ImmutableList.builder();
    for (int i = 0; i < numberOfStages; i++) {
      FlowExpressionInfo flowExpressionInfo = new FlowExpressionInfo();
      flowExpressionInfo.condition =
          String.format("com.google.gerrit[change:%s label:Verified+%s]", changeIdentifier.id(), i);

      FlowActionInfo flowActionInfo = new FlowActionInfo();
      flowActionInfo.name = "AddReviewer";
      flowActionInfo.parameters =
          ImmutableList.of(accountCreator.createValid("reviewer" + i).email());
      flowExpressionInfo.action = flowActionInfo;

      stageExpressionsBuilder.add(flowExpressionInfo);
    }

    flowInput.stageExpressions = stageExpressionsBuilder.build();

    return flowInput;
  }

  /** Creates a {@link FlowCreation} with arbitrary test data that contains one stage. */
  public static FlowCreation createTestFlowCreationWithOneStage(
      AccountCreator accountCreator, TestChange change) throws Exception {
    return createTestFlowCreation(accountCreator, change, 1);
  }

  /** Creates a {@link FlowCreation} with arbitrary test data that contains multiple stages. */
  public static FlowCreation createTestFlowCreationWithMultipleStages(
      AccountCreator accountCreator, TestChange change) throws Exception {
    return createTestFlowCreation(accountCreator, change, 3);
  }

  /** Creates a {@link FlowCreation} with arbitrary test data and as many stages as specified. */
  public static FlowCreation createTestFlowCreation(
      AccountCreator accountCreator, TestChange change, int numberOfStages) throws Exception {
    FlowCreation.Builder flowCreationBuilder =
        FlowCreation.builder()
            .projectName(change.project())
            .changeId(change.numericChangeId())
            .ownerId(accountCreator.createValid("owner").id());

    for (int i = 0; i < numberOfStages; i++) {
      flowCreationBuilder.addStageExpression(
          FlowExpression.builder()
              .condition(
                  String.format("com.google.gerrit[change:%s label:Verified+%s]", change.id(), i))
              .action(
                  FlowAction.builder()
                      .name("AddReviewer")
                      .addParameter(accountCreator.createValid("reviewer" + i).email())
                      .build())
              .build());
    }

    return flowCreationBuilder.build();
  }

  /**
   * Private constructor to prevent instantiation of this class.
   *
   * <p>This class contains only static methods and hence never needs to be instantiated.
   */
  private FlowTestUtil() {}
}
