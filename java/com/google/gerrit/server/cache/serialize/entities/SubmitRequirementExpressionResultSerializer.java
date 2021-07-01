package com.google.gerrit.server.cache.serialize.entities;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.server.cache.proto.Cache.SubmitRequirementExpressionProto;
import com.google.gerrit.server.cache.proto.Cache.SubmitRequirementExpressionResultProto;

public class SubmitRequirementExpressionResultSerializer {
  public static SubmitRequirementExpressionResult deserialize(
      SubmitRequirementExpressionResultProto proto) {
    return SubmitRequirementExpressionResult.create(
        SubmitRequirementExpression.create(proto.getExpression().getExpression()),
        SubmitRequirementExpressionResult.Status.valueOf(proto.getStatus()),
        proto.getPassingAtomsList().stream().collect(ImmutableList.toImmutableList()),
        proto.getFailingAtomsList().stream().collect(ImmutableList.toImmutableList()));
  }

  public static SubmitRequirementExpressionResultProto serialize(
      SubmitRequirementExpressionResult r) {
    SubmitRequirementExpressionResultProto.Builder builder =
        SubmitRequirementExpressionResultProto.newBuilder();
    builder.setExpression(
        SubmitRequirementExpressionProto.newBuilder()
            .setExpression(r.expression().expressionString())
            .build());
    builder.setStatus(r.status().name());
    builder.addAllPassingAtoms(r.passingAtoms());
    builder.addAllFailingAtoms(r.failingAtoms());
    return builder.build();
  }
}
