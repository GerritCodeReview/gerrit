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
import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.entities.SubmitRequirementExpression;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.cache.proto.Cache.SubmitRequirementProto;
import com.google.protobuf.Descriptors.FieldDescriptor;
import java.util.Optional;

/** Serializer for {@link com.google.gerrit.entities.SubmitRequirement}. */
public class SubmitRequirementSerializer {
  private static final FieldDescriptor SR_HIDE_APPLICABILITY_EXPRESSION_FIELD =
      SubmitRequirementProto.getDescriptor().findFieldByNumber(7);

  public static SubmitRequirement deserialize(Cache.SubmitRequirementProto proto) {
    SubmitRequirement.Builder builder =
        SubmitRequirement.builder()
            .setName(proto.getName())
            .setDescription(Optional.ofNullable(Strings.emptyToNull(proto.getDescription())))
            .setApplicabilityExpression(
                SubmitRequirementExpression.of(proto.getApplicabilityExpression()))
            .setSubmittabilityExpression(
                SubmitRequirementExpression.create(proto.getSubmittabilityExpression()))
            .setOverrideExpression(SubmitRequirementExpression.of(proto.getOverrideExpression()))
            .setAllowOverrideInChildProjects(proto.getAllowOverrideInChildProjects());
    if (proto.hasField(SR_HIDE_APPLICABILITY_EXPRESSION_FIELD)) {
      builder.setHideApplicabilityExpression(Optional.of(proto.getHideApplicabilityExpression()));
    }
    return builder.build();
  }

  public static Cache.SubmitRequirementProto serialize(SubmitRequirement submitRequirement) {
    SubmitRequirementExpression emptyExpression = SubmitRequirementExpression.create("");
    SubmitRequirementProto.Builder builder =
        SubmitRequirementProto.newBuilder()
            .setName(submitRequirement.name())
            .setDescription(submitRequirement.description().orElse(""))
            .setApplicabilityExpression(
                submitRequirement
                    .applicabilityExpression()
                    .orElse(emptyExpression)
                    .expressionString())
            .setSubmittabilityExpression(
                submitRequirement.submittabilityExpression().expressionString())
            .setOverrideExpression(
                submitRequirement.overrideExpression().orElse(emptyExpression).expressionString())
            .setAllowOverrideInChildProjects(submitRequirement.allowOverrideInChildProjects());
    if (submitRequirement.hideApplicabilityExpression().isPresent()) {
      builder.setHideApplicabilityExpression(submitRequirement.hideApplicabilityExpression().get());
    }
    return builder.build();
  }
}
