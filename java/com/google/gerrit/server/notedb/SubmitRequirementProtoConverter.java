// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.entities.converter.ProtoConverter;
import com.google.gerrit.server.cache.proto.Cache.SubmitRequirementResultProto;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.cache.serialize.entities.SubmitRequirementExpressionResultSerializer;
import com.google.gerrit.server.cache.serialize.entities.SubmitRequirementSerializer;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Parser;
import java.util.Optional;

@Immutable
public enum SubmitRequirementProtoConverter
    implements ProtoConverter<SubmitRequirementResultProto, SubmitRequirementResult> {
  INSTANCE;

  private static final FieldDescriptor SR_APPLICABILITY_EXPR_RESULT_FIELD =
      SubmitRequirementResultProto.getDescriptor().findFieldByNumber(2);
  private static final FieldDescriptor SR_SUBMITTABILITY_EXPR_RESULT_FIELD =
      SubmitRequirementResultProto.getDescriptor().findFieldByNumber(3);
  private static final FieldDescriptor SR_OVERRIDE_EXPR_RESULT_FIELD =
      SubmitRequirementResultProto.getDescriptor().findFieldByNumber(4);
  private static final FieldDescriptor SR_LEGACY_FIELD =
      SubmitRequirementResultProto.getDescriptor().findFieldByNumber(6);
  private static final FieldDescriptor SR_FORCED_FIELD =
      SubmitRequirementResultProto.getDescriptor().findFieldByNumber(7);
  private static final FieldDescriptor SR_HIDDEN_FIELD =
      SubmitRequirementResultProto.getDescriptor().findFieldByNumber(8);

  @Override
  public SubmitRequirementResultProto toProto(SubmitRequirementResult r) {
    SubmitRequirementResultProto.Builder builder = SubmitRequirementResultProto.newBuilder();
    builder
        .setSubmitRequirement(SubmitRequirementSerializer.serialize(r.submitRequirement()))
        .setCommit(ObjectIdConverter.create().toByteString(r.patchSetCommitId()));
    if (r.legacy().isPresent()) {
      builder.setLegacy(r.legacy().get());
    }
    if (r.forced().isPresent()) {
      builder.setForced(r.forced().get());
    }
    if (r.hidden().isPresent()) {
      builder.setHidden(r.hidden().get());
    }
    if (r.applicabilityExpressionResult().isPresent()) {
      builder.setApplicabilityExpressionResult(
          SubmitRequirementExpressionResultSerializer.serialize(
              r.applicabilityExpressionResult().get()));
    }
    if (r.submittabilityExpressionResult().isPresent()) {
      builder.setSubmittabilityExpressionResult(
          SubmitRequirementExpressionResultSerializer.serialize(
              r.submittabilityExpressionResult().get()));
    }
    if (r.overrideExpressionResult().isPresent()) {
      builder.setOverrideExpressionResult(
          SubmitRequirementExpressionResultSerializer.serialize(
              r.overrideExpressionResult().get()));
    }
    return builder.build();
  }

  @Override
  public SubmitRequirementResult fromProto(SubmitRequirementResultProto proto) {
    SubmitRequirementResult.Builder builder =
        SubmitRequirementResult.builder()
            .patchSetCommitId(ObjectIdConverter.create().fromByteString(proto.getCommit()))
            .submitRequirement(
                SubmitRequirementSerializer.deserialize(proto.getSubmitRequirement()));
    if (proto.hasField(SR_LEGACY_FIELD)) {
      builder.legacy(Optional.of(proto.getLegacy()));
    }
    if (proto.hasField(SR_FORCED_FIELD)) {
      builder.forced(Optional.of(proto.getForced()));
    }
    if (proto.hasField(SR_HIDDEN_FIELD)) {
      builder.hidden(Optional.of(proto.getHidden()));
    }
    if (proto.hasField(SR_APPLICABILITY_EXPR_RESULT_FIELD)) {
      builder.applicabilityExpressionResult(
          Optional.of(
              SubmitRequirementExpressionResultSerializer.deserialize(
                  proto.getApplicabilityExpressionResult())));
    }
    if (proto.hasField(SR_SUBMITTABILITY_EXPR_RESULT_FIELD)) {
      builder.submittabilityExpressionResult(
          SubmitRequirementExpressionResultSerializer.deserialize(
              proto.getSubmittabilityExpressionResult()));
    }
    if (proto.hasField(SR_OVERRIDE_EXPR_RESULT_FIELD)) {
      builder.overrideExpressionResult(
          Optional.of(
              SubmitRequirementExpressionResultSerializer.deserialize(
                  proto.getOverrideExpressionResult())));
    }
    return builder.build();
  }

  @Override
  public Parser<SubmitRequirementResultProto> getParser() {
    return SubmitRequirementResultProto.parser();
  }
}
