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

package com.google.gerrit.server.flow.converters;

import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.entities.converter.SafeProtoConverter;
import com.google.gerrit.server.flow.FlowStageEvaluationStatus;
import com.google.protobuf.Parser;
import java.time.Instant;

@Immutable
public enum FlowStageEvaluationStatusProtoConverter
    implements
        SafeProtoConverter<
            com.google.gerrit.server.flow.proto.FlowStageEvaluationStatus,
            FlowStageEvaluationStatus> {
  INSTANCE;

  @Override
  public com.google.gerrit.server.flow.proto.FlowStageEvaluationStatus toProto(
      FlowStageEvaluationStatus status) {
    var builder =
        com.google.gerrit.server.flow.proto.FlowStageEvaluationStatus.newBuilder()
            .setState(
                com.google.gerrit.server.flow.proto.FlowStageEvaluationStatus.State.forNumber(
                    status.state().getValue()));
    if (status.message().isPresent()) {
      builder.setMessage(status.message().get());
    }
    if (status.startTime().isPresent()) {
      builder.setStartTimeMillis(status.startTime().get().toEpochMilli());
    }
    if (status.endTime().isPresent()) {
      builder.setEndTimeMillis(status.endTime().get().toEpochMilli());
    }
    return builder.build();
  }

  @Override
  public FlowStageEvaluationStatus fromProto(
      com.google.gerrit.server.flow.proto.FlowStageEvaluationStatus proto) {
    var builder =
        FlowStageEvaluationStatus.builder()
            .state(FlowStageEvaluationStatus.State.valueOf(proto.getState().name()));
    if (proto.hasMessage()) {
      builder.message(proto.getMessage());
    }
    if (proto.hasStartTimeMillis()) {
      builder.startTime(Instant.ofEpochMilli(proto.getStartTimeMillis()));
    }
    if (proto.hasEndTimeMillis()) {
      builder.endTime(Instant.ofEpochMilli(proto.getEndTimeMillis()));
    }
    return builder.build();
  }

  @Override
  public Parser<com.google.gerrit.server.flow.proto.FlowStageEvaluationStatus> getParser() {
    return com.google.gerrit.server.flow.proto.FlowStageEvaluationStatus.parser();
  }

  @Override
  public Class<com.google.gerrit.server.flow.proto.FlowStageEvaluationStatus> getProtoClass() {
    return com.google.gerrit.server.flow.proto.FlowStageEvaluationStatus.class;
  }

  @Override
  public Class<FlowStageEvaluationStatus> getEntityClass() {
    return FlowStageEvaluationStatus.class;
  }
}
