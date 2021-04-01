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

import com.google.gerrit.entities.SubmitRequirement;
import com.google.gerrit.server.cache.proto.Cache;

/** Serializer for {@link com.google.gerrit.entities.SubmitRequirement}. */
public class SubmitRequirementSerializer {
  public static SubmitRequirement deserialize(Cache.SubmitRequirementProto proto) {
    return SubmitRequirement.builder()
        .setName(proto.getName())
        .setDescription(proto.getDescription())
        .setApplicabilityExpression(proto.getApplicabilityExpression())
        .setBlockingExpression(proto.getBlockingExpression())
        .setOverrideExpression(proto.getOverrideExpression())
        .setCanOverride(proto.getCanOverride())
        .build();
  }

  public static Cache.SubmitRequirementProto serialize(SubmitRequirement submitRequirement) {
    return Cache.SubmitRequirementProto.newBuilder()
        .setName(submitRequirement.name())
        .setDescription(submitRequirement.description())
        .setApplicabilityExpression(submitRequirement.applicabilityExpression())
        .setBlockingExpression(submitRequirement.blockingExpression())
        .setOverrideExpression(submitRequirement.overrideExpression())
        .setCanOverride(submitRequirement.canOverride())
        .build();
  }
}
