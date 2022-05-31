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

package com.google.gerrit.server.cache.serialize.entities;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.entities.SubmitRequirementExpressionResult;
import com.google.gerrit.entities.SubmitRequirementExpressionResult.Status;
import com.google.gerrit.entities.converter.SafeEnumStringConverter;
import com.google.gerrit.server.cache.proto.Cache.SubmitRequirementExpressionResultProto;
import java.util.Optional;

/**
 * Serializer of a {@link SubmitRequirementExpressionResult} to {@link
 * SubmitRequirementExpressionResultProto}.
 */
public class SubmitRequirementExpressionResultSerializer {

  private static final SafeEnumStringConverter<Status> STATUS_CONVERTER =
      new SafeEnumStringConverter(SubmitRequirementExpressionResult.Status.class);

  public static SubmitRequirementExpressionResult deserialize(
      SubmitRequirementExpressionResultProto proto) {
    return SubmitRequirementExpressionResult.create(
        SubmitRequirementExpression.create(proto.getExpression()),
        STATUS_CONVERTER.convert(proto.getStatus()),
        proto.getPassingAtomsList().stream().collect(ImmutableList.toImmutableList()),
        proto.getFailingAtomsList().stream().collect(ImmutableList.toImmutableList()),
        Optional.ofNullable(Strings.emptyToNull(proto.getErrorMessage())));
  }

  public static SubmitRequirementExpressionResultProto serialize(
      SubmitRequirementExpressionResult r) {
    return SubmitRequirementExpressionResultProto.newBuilder()
        .setExpression(r.expression().expressionString())
        .setStatus(STATUS_CONVERTER.reverseConvert(r.status()))
        .addAllPassingAtoms(r.passingAtoms())
        .addAllFailingAtoms(r.failingAtoms())
        .setErrorMessage(r.errorMessage().orElse(""))
        .build();
  }
}
