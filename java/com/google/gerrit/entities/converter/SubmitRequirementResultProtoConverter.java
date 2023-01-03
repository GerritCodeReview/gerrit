package com.google.gerrit.entities.converter;

import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.Entities.Submit_Requirement_Result;
import com.google.protobuf.Parser;
import java.util.Optional;

@Immutable
public enum SubmitRequirementResultProtoConverter
    implements ProtoConverter<Entities.Submit_Requirement_Result, SubmitRequirementResult> {
  INSTANCE;

  @Override
  public Submit_Requirement_Result toProto(SubmitRequirementResult sr) {
    Submit_Requirement_Result.Builder builder = Submit_Requirement_Result.newBuilder();
    builder.setSubmitRequirement(
        SubmitRequirementProtoConverter.INSTANCE.toProto(sr.submitRequirement()));
    if (sr.applicabilityExpressionResult().isPresent()) {
      builder.setApplicabilityExpressionResult(
          SubmitRequirementExpressionResultProtoConverter.INSTANCE.toProto(
              sr.applicabilityExpressionResult().get()));
    }
    if (sr.submittabilityExpressionResult().isPresent()) {
      builder.setSubmittabilityExpressionResult(
          SubmitRequirementExpressionResultProtoConverter.INSTANCE.toProto(
              sr.submittabilityExpressionResult().get()));
    }
    if (sr.overrideExpressionResult().isPresent()) {
      builder.setOverrideExpressionResult(
          SubmitRequirementExpressionResultProtoConverter.INSTANCE.toProto(
              sr.overrideExpressionResult().get()));
    }
    builder.setPatchSetCommitId(ObjectIdProtoConverter.INSTANCE.toProto(sr.patchSetCommitId()));
    if (sr.legacy().isPresent()) {
      builder.setLegacy(sr.isLegacy());
    }
    if (sr.forced().isPresent()) {
      builder.setForced(sr.forced().get());
    }
    if (sr.hidden().isPresent()) {
      builder.setHidden(sr.hidden().get());
    }
    return builder.build();
  }

  @Override
  public SubmitRequirementResult fromProto(Submit_Requirement_Result proto) {
    SubmitRequirementResult.Builder builder = SubmitRequirementResult.builder();
    builder.submitRequirement(
        SubmitRequirementProtoConverter.INSTANCE.fromProto(proto.getSubmitRequirement()));
    if (proto.hasApplicabilityExpressionResult()) {
      builder.applicabilityExpressionResult(
          Optional.of(
              SubmitRequirementExpressionResultProtoConverter.INSTANCE.fromProto(
                  proto.getApplicabilityExpressionResult())));
    }
    if (proto.hasSubmittabilityExpressionResult()) {
      builder.submittabilityExpressionResult(
          Optional.of(
              SubmitRequirementExpressionResultProtoConverter.INSTANCE.fromProto(
                  proto.getSubmittabilityExpressionResult())));
    }
    if (proto.hasOverrideExpressionResult()) {
      builder.overrideExpressionResult(
          Optional.of(
              SubmitRequirementExpressionResultProtoConverter.INSTANCE.fromProto(
                  proto.getOverrideExpressionResult())));
    }
    builder.patchSetCommitId(
        ObjectIdProtoConverter.INSTANCE.fromProto(proto.getPatchSetCommitId()));
    if (proto.hasForced()) {
      builder.forced(Optional.of(proto.getForced()));
    }
    if (proto.hasHidden()) {
      builder.hidden(Optional.of(proto.getHidden()));
    }
    if (proto.hasLegacy()) {
      builder.legacy(Optional.of(proto.getLegacy()));
    }
    return builder.build();
  }

  @Override
  public Parser<Submit_Requirement_Result> getParser() {
    return Submit_Requirement_Result.parser();
  }
}
