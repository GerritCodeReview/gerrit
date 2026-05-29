// Copyright (C) 2020 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Shorts;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.server.cache.proto.Cache;
import java.util.Optional;

/** Helper to (de)serialize values for caches. */
public class LabelTypeSerializer {
  private static final Converter<String, LabelFunction> FUNCTION_CONVERTER =
      Enums.stringConverter(LabelFunction.class);

  public static LabelType deserialize(Cache.LabelTypeProto proto) {
    return LabelType.builder(
            proto.getName(),
            proto.getValuesList().stream()
                .map(LabelValueSerializer::deserialize)
                .collect(toImmutableList()))
        .setDescription(Optional.of(proto.getDescription()))
        .setFunction(FUNCTION_CONVERTER.convert(proto.getFunction()))
        .setAllowPostSubmit(proto.getAllowPostSubmit())
        .setIgnoreSelfApproval(proto.getIgnoreSelfApproval())
        .setDefaultValue(Shorts.saturatedCast(proto.getDefaultValue()))
        .setCopyCondition(Strings.emptyToNull(proto.getCopyCondition()))
        .setMaxNegative(Shorts.saturatedCast(proto.getMaxNegative()))
        .setMaxPositive(Shorts.saturatedCast(proto.getMaxPositive()))
        .setRefPatterns(proto.getRefPatternsList())
        .setCanOverride(proto.getCanOverride())
        .build();
  }

  public static Cache.LabelTypeProto serialize(LabelType autoValue) {
    return Cache.LabelTypeProto.newBuilder()
        .setName(autoValue.getName())
        .addAllValues(
            autoValue.getValues().stream()
                .map(LabelValueSerializer::serialize)
                .collect(toImmutableList()))
        .setDescription(autoValue.getDescription().orElse(""))
        .setFunction(FUNCTION_CONVERTER.reverse().convert(autoValue.getFunction()))
        .setCopyCondition(autoValue.getCopyCondition().orElse(""))
        .setAllowPostSubmit(autoValue.isAllowPostSubmit())
        .setIgnoreSelfApproval(autoValue.isIgnoreSelfApproval())
        .setDefaultValue(Shorts.saturatedCast(autoValue.getDefaultValue()))
        .setMaxNegative(Shorts.saturatedCast(autoValue.getMaxNegative()))
        .setMaxPositive(Shorts.saturatedCast(autoValue.getMaxPositive()))
        .addAllRefPatterns(
            autoValue.getRefPatterns() == null ? ImmutableList.of() : autoValue.getRefPatterns())
        .setCanOverride(autoValue.isCanOverride())
        .build();
  }

  private LabelTypeSerializer() {}
}
