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
import com.google.gerrit.entities.converter.ProtoConverter;
import com.google.gerrit.entities.converter.SafeProtoConverter;
import com.google.gerrit.server.flow.FlowAction;
import com.google.gerrit.server.flow.FlowExpression;
import com.google.protobuf.Parser;

@Immutable
public enum FlowExpressionProtoConverter
    implements
        SafeProtoConverter<com.google.gerrit.server.flow.proto.FlowExpression, FlowExpression> {
  INSTANCE;

  private final ProtoConverter<com.google.gerrit.server.flow.proto.FlowAction, FlowAction>
      flowActionConverter = FlowActionProtoConverter.INSTANCE;

  @Override
  public com.google.gerrit.server.flow.proto.FlowExpression toProto(FlowExpression expression) {
    return com.google.gerrit.server.flow.proto.FlowExpression.newBuilder()
        .setCondition(expression.condition())
        .setAction(flowActionConverter.toProto(expression.action()))
        .build();
  }

  @Override
  public FlowExpression fromProto(com.google.gerrit.server.flow.proto.FlowExpression proto) {
    return FlowExpression.builder()
        .condition(proto.getCondition())
        .action(flowActionConverter.fromProto(proto.getAction()))
        .build();
  }

  @Override
  public Parser<com.google.gerrit.server.flow.proto.FlowExpression> getParser() {
    return com.google.gerrit.server.flow.proto.FlowExpression.parser();
  }

  @Override
  public Class<com.google.gerrit.server.flow.proto.FlowExpression> getProtoClass() {
    return com.google.gerrit.server.flow.proto.FlowExpression.class;
  }

  @Override
  public Class<FlowExpression> getEntityClass() {
    return FlowExpression.class;
  }
}
