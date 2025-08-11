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

import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.entities.converter.SafeProtoConverter;
import com.google.gerrit.server.flow.FlowAction;
import com.google.protobuf.Parser;

@Immutable
public enum FlowActionProtoConverter
    implements SafeProtoConverter<com.google.gerrit.server.flow.proto.FlowAction, FlowAction> {
  INSTANCE;

  @Override
  public com.google.gerrit.server.flow.proto.FlowAction toProto(FlowAction action) {
    return com.google.gerrit.server.flow.proto.FlowAction.newBuilder()
        .setName(action.name())
        .putAllParameters(action.parameters())
        .build();
  }

  @Override
  public FlowAction fromProto(com.google.gerrit.server.flow.proto.FlowAction proto) {
    return FlowAction.builder()
        .name(proto.getName())
        .parameters(ImmutableMap.copyOf(proto.getParameters()))
        .build();
  }

  @Override
  public Parser<com.google.gerrit.server.flow.proto.FlowAction> getParser() {
    return com.google.gerrit.server.flow.proto.FlowAction.parser();
  }

  @Override
  public Class<com.google.gerrit.server.flow.proto.FlowAction> getProtoClass() {
    return com.google.gerrit.server.flow.proto.FlowAction.class;
  }

  @Override
  public Class<FlowAction> getEntityClass() {
    return FlowAction.class;
  }
}
