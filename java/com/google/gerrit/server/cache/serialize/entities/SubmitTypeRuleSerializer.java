// Copyright (C) 2026 The Android Open Source Project
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

import com.google.gerrit.entities.SubmitTypeRule;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.server.cache.proto.Cache;

/** Serializer for {@link com.google.gerrit.entities.SubmitTypeRule}. */
public class SubmitTypeRuleSerializer {
  public static SubmitTypeRule deserialize(Cache.SubmitTypeRuleProto proto) {
    return SubmitTypeRule.builder()
        .setType(SubmitType.valueOf(proto.getSubmitType()))
        .setApplicabilityExpression(proto.getApplicabilityExpr())
        .build();
  }

  public static Cache.SubmitTypeRuleProto serialize(SubmitTypeRule override) {
    return Cache.SubmitTypeRuleProto.newBuilder()
        .setSubmitType(override.type().name())
        .setApplicabilityExpr(override.applicabilityExpression())
        .build();
  }
}
