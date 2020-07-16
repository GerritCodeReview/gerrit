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
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Shorts;
import com.google.gerrit.entities.LabelFunction;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.server.cache.proto.Cache;

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
        .setFunction(FUNCTION_CONVERTER.convert(proto.getFunction()))
        .setAllowPostSubmit(proto.getAllowPostSubmit())
        .setIgnoreSelfApproval(proto.getIgnoreSelfApproval())
        .setDefaultValue(Shorts.saturatedCast(proto.getDefaultValue()))
        .setCopyAnyScore(proto.getCopyAnyScore())
        .setCopyMinScore(proto.getCopyMinScore())
        .setCopyMaxScore(proto.getCopyMaxScore())
        .setCopyAllScoresOnMergeFirstParentUpdate(proto.getCopyAllScoresOnMergeFirstParentUpdate())
        .setCopyAllScoresOnTrivialRebase(proto.getCopyAllScoresOnTrivialRebase())
        .setCopyAllScoresIfNoCodeChange(proto.getCopyAllScoresIfNoCodeChange())
        .setCopyAllScoresIfNoChange(proto.getCopyAllScoresIfNoChange())
        .setCopyValues(
            proto.getCopyValuesList().stream()
                .map(Shorts::saturatedCast)
                .collect(toImmutableList()))
        .setMaxNegative(Shorts.saturatedCast(proto.getMaxNegative()))
        .setMaxPositive(Shorts.saturatedCast(proto.getMaxPositive()))
        .setRefPatterns(proto.getRefPatternsList())
        .build();
  }

  public static Cache.LabelTypeProto serialize(LabelType autoValue) {
    return Cache.LabelTypeProto.newBuilder()
        .setName(autoValue.getName())
        .addAllValues(
            autoValue.getValues().stream()
                .map(LabelValueSerializer::serialize)
                .collect(toImmutableList()))
        .setFunction(FUNCTION_CONVERTER.reverse().convert(autoValue.getFunction()))
        .setCopyAnyScore(autoValue.isCopyAnyScore())
        .setCopyMinScore(autoValue.isCopyMinScore())
        .setCopyMaxScore(autoValue.isCopyMaxScore())
        .setCopyAllScoresOnMergeFirstParentUpdate(
            autoValue.isCopyAllScoresOnMergeFirstParentUpdate())
        .setCopyAllScoresOnTrivialRebase(autoValue.isCopyAllScoresOnTrivialRebase())
        .setCopyAllScoresIfNoCodeChange(autoValue.isCopyAllScoresIfNoCodeChange())
        .setCopyAllScoresIfNoChange(autoValue.isCopyAllScoresIfNoChange())
        .addAllCopyValues(
            autoValue.getCopyValues().stream().map(c -> (int) c).collect(toImmutableList()))
        .setAllowPostSubmit(autoValue.isAllowPostSubmit())
        .setIgnoreSelfApproval(autoValue.isIgnoreSelfApproval())
        .setDefaultValue(Shorts.saturatedCast(autoValue.getDefaultValue()))
        .setMaxNegative(Shorts.saturatedCast(autoValue.getMaxNegative()))
        .setMaxPositive(Shorts.saturatedCast(autoValue.getMaxPositive()))
        .addAllRefPatterns(
            autoValue.getRefPatterns() == null ? ImmutableList.of() : autoValue.getRefPatterns())
        .build();
  }

  private LabelTypeSerializer() {}
}
