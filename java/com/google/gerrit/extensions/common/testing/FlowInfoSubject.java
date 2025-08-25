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

package com.google.gerrit.extensions.common.testing;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.gerrit.extensions.common.testing.AccountInfoSubject.accounts;
import static com.google.gerrit.truth.ListSubject.elements;
import static com.google.gerrit.truth.OptionalSubject.optionals;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.ComparableSubject;
import com.google.common.truth.FailureMetadata;
import com.google.common.truth.StringSubject;
import com.google.common.truth.Subject;
import com.google.gerrit.extensions.common.FlowExpressionInfo;
import com.google.gerrit.extensions.common.FlowInfo;
import com.google.gerrit.extensions.common.FlowInput;
import com.google.gerrit.extensions.common.FlowStageInfo;
import com.google.gerrit.extensions.common.FlowStageState;
import com.google.gerrit.server.flow.Flow;
import com.google.gerrit.server.flow.FlowStage;
import com.google.gerrit.server.restapi.flow.FlowJson;
import com.google.gerrit.truth.ListSubject;
import com.google.gerrit.truth.OptionalSubject;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

/** A Truth subject for {@link FlowInfo} instances. */
public class FlowInfoSubject extends Subject {
  private final FailureMetadata metadata;
  private final FlowInfo flowInfo;

  public static FlowInfoSubject assertThat(FlowInfo flowInfo) {
    return assertAbout(flows()).that(flowInfo);
  }

  public static Factory<FlowInfoSubject, FlowInfo> flows() {
    return FlowInfoSubject::new;
  }

  private FlowInfoSubject(FailureMetadata metadata, FlowInfo flowInfo) {
    super(metadata, flowInfo);
    this.metadata = metadata;
    this.flowInfo = flowInfo;
  }

  public StringSubject hasUuidThat() {
    return check("uuid()").that(flowInfo().uuid);
  }

  public AccountInfoSubject hasOwnerThat() {
    return check("owner()").about(accounts()).that(flowInfo().owner);
  }

  public ComparableSubject<Instant> hasCreatedThat() {
    return check("created()").that(flowInfo().created.toInstant());
  }

  public ListSubject<FlowStageInfoSubject, FlowStageInfo> hasStagesThat() {
    return check("stages()")
        .about(elements())
        .thatCustom(flowInfo().stages, FlowStageInfoSubject.flowStages());
  }

  public OptionalSubject<ComparableSubject<Instant>, ?> hasLastEvaluated() {
    return check("lastEvaluated()")
        .about(optionals())
        .thatCustom(
            Optional.ofNullable(flowInfo().lastEvaluated).map(Timestamp::toInstant),
            (builder, value) -> new ComparableSubject<>(metadata, value) {});
  }

  /**
   * Asserts that the properties of this {@link FlowInfo} match with the properties of the given
   * {@link FlowInput} instance.
   */
  public void matches(FlowInput flowInput) {
    hasUuidThat().isNotEmpty();
    hasLastEvaluated().isEmpty();

    hasStagesThat().hasSize(flowInput.stageExpressions.size());

    for (int i = 0; i < flowInput.stageExpressions.size(); i++) {
      FlowExpressionInfo flowExpressionInfo = flowInput.stageExpressions.get(i);
      FlowStageInfoSubject stageSubject = hasStagesThat().element(i);
      stageSubject.hasStateThat().isEqualTo(FlowStageState.PENDING);
      stageSubject.hasExpressionThat().hasConditionThat().isEqualTo(flowExpressionInfo.condition);
      if (flowInput.stageExpressions.get(i).action != null) {
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
                    : ImmutableList.of());
      } else {
        stageSubject.hasExpressionThat().hasNoAction();
      }
    }
  }

  /**
   * Asserts that the properties of this {@link FlowInfo} match with the properties of the given
   * {@link Flow} instance.
   */
  public void matches(Flow flow) {
    hasUuidThat().isEqualTo(flow.key().uuid());
    hasOwnerThat().hasAccountIdThat().isEqualTo(flow.ownerId());
    hasCreatedThat().isEqualTo(flow.createdOn());
    hasLastEvaluated().isEqualTo(flow.lastEvaluatedOn());

    hasStagesThat().hasSize(flow.stages().size());

    for (int i = 0; i < flow.stages().size(); i++) {
      FlowStage flowStage = flow.stages().get(i);

      FlowStageInfoSubject stageSubject = hasStagesThat().element(i);
      stageSubject.hasStateThat().isEqualTo(FlowJson.mapState(flowStage.status().state()));
      stageSubject
          .hasExpressionThat()
          .hasConditionThat()
          .isEqualTo(flowStage.expression().condition());

      if (flowStage.expression().action().isPresent()) {
        stageSubject
            .hasExpressionThat()
            .hasActionThat()
            .hasNameThat()
            .isEqualTo(flowStage.expression().action().get().name());
        stageSubject
            .hasExpressionThat()
            .hasActionThat()
            .hasParametersThat()
            .isEqualTo(flowStage.expression().action().get().parameters());
      } else {
        stageSubject.hasExpressionThat().hasNoAction();
      }
    }
  }

  private FlowInfo flowInfo() {
    isNotNull();
    return flowInfo;
  }
}
