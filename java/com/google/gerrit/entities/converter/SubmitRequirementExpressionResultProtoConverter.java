package com.google.gerrit.entities.converter;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementExpressionResult.Status;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.proto.Entities.Submit_Requirement_Expression_Result;
import com.google.protobuf.Parser;
import java.util.Optional;

@Immutable
public enum SubmitRequirementExpressionResultProtoConverter
    implements
        ProtoConverter<
            Entities.Submit_Requirement_Expression_Result, SubmitRequirementExpressionResult> {
  INSTANCE;

  @Override
  public Submit_Requirement_Expression_Result toProto(SubmitRequirementExpressionResult sr) {
    Submit_Requirement_Expression_Result.Builder builder =
        Submit_Requirement_Expression_Result.newBuilder();
    builder.setExpression(sr.expression().expressionString());
    builder.setStatus(Submit_Requirement_Expression_Result.Status.valueOf(sr.status().name()));
    if (sr.errorMessage().isPresent()) {
      builder.setErrorMessage(sr.errorMessage().get());
    }
    builder.addAllPassingAtoms(sr.passingAtoms());
    builder.addAllFailingAtoms(sr.failingAtoms());
    return builder.build();
  }

  @Override
  public SubmitRequirementExpressionResult fromProto(Submit_Requirement_Expression_Result proto) {
    return SubmitRequirementExpressionResult.create(
        SubmitRequirementExpression.create(proto.getExpression()),
        Status.valueOf(proto.getStatus().name()),
        proto.getPassingAtomsList().stream().collect(ImmutableList.toImmutableList()),
        proto.getFailingAtomsList().stream().collect(ImmutableList.toImmutableList()),
        proto.hasErrorMessage() ? Optional.of(proto.getErrorMessage()) : Optional.empty());
  }

  @Override
  public Parser<Submit_Requirement_Expression_Result> getParser() {
    return Submit_Requirement_Expression_Result.parser();
  }
}
