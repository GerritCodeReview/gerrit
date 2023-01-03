package com.google.gerrit.entities.converter;

import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.Entities.Submit_Requirement;
import com.google.protobuf.Parser;

@Immutable
public enum SubmitRequirementProtoConverter
    implements ProtoConverter<Entities.Submit_Requirement, SubmitRequirement> {
  INSTANCE;

  @Override
  public Submit_Requirement toProto(SubmitRequirement sr) {
    Submit_Requirement.Builder builder = Submit_Requirement.newBuilder().setName(sr.name());
    if (sr.description().isPresent()) {
      builder.setDescription(sr.description().get());
    }
    if (sr.applicabilityExpression().isPresent()) {
      builder.setApplicabilityExpression(sr.applicabilityExpression().get().expressionString());
    }
    builder.setSubmittabilityExpression(sr.submittabilityExpression().expressionString());
    if (sr.overrideExpression().isPresent()) {
      builder.setOverrideExpression(sr.overrideExpression().get().expressionString());
    }
    builder.setCanOverrideInChildProjects(sr.allowOverrideInChildProjects());
    return builder.build();
  }

  @Override
  public SubmitRequirement fromProto(Submit_Requirement proto) {
    SubmitRequirement.Builder builder = SubmitRequirement.builder().setName(proto.getName());
    if (proto.hasApplicabilityExpression()) {
      builder.setApplicabilityExpression(
          SubmitRequirementExpression.of(proto.getApplicabilityExpression()));
    }
    builder.setSubmittabilityExpression(
        SubmitRequirementExpression.create(proto.getSubmittabilityExpression()));
    if (proto.hasOverrideExpression()) {
      builder.setOverrideExpression(SubmitRequirementExpression.of(proto.getOverrideExpression()));
    }
    builder.setAllowOverrideInChildProjects(proto.getCanOverrideInChildProjects());
    return builder.build();
  }

  @Override
  public Parser<Submit_Requirement> getParser() {
    return Entities.Submit_Requirement.parser();
  }
}
