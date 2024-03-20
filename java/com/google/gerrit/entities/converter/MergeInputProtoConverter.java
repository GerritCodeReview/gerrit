/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gerrit.entities.converter;

import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.gerrit.proto.Entities;
import com.google.protobuf.Parser;

/**
 * Proto converter between {@link MergeInput} and {@link
 * com.google.gerrit.proto.Entities.MergeInput}.
 */
@Immutable
public enum MergeInputProtoConverter implements ProtoConverter<Entities.MergeInput, MergeInput> {
  INSTANCE;

  @Override
  public Entities.MergeInput toProto(MergeInput mergeInput) {
    Entities.MergeInput.Builder builder = Entities.MergeInput.newBuilder();
    if (mergeInput.source != null) {
      builder.setSource(mergeInput.source);
    }
    if (mergeInput.sourceBranch != null) {
      builder.setSourceBranch(mergeInput.sourceBranch);
    }
    if (mergeInput.strategy != null) {
      builder.setStrategy(mergeInput.strategy);
    }
    builder.setAllowConflicts(mergeInput.allowConflicts);
    return builder.build();
  }

  @Override
  public MergeInput fromProto(Entities.MergeInput proto) {
    MergeInput mergeInput = new MergeInput();
    if (proto.hasSource()) {
      mergeInput.source = proto.getSource();
    }
    if (proto.hasSourceBranch()) {
      mergeInput.sourceBranch = proto.getSourceBranch();
    }
    if (proto.hasStrategy()) {
      mergeInput.strategy = proto.getStrategy();
    }
    if (proto.hasAllowConflicts()) {
      mergeInput.allowConflicts = proto.getAllowConflicts();
    }
    return mergeInput;
  }

  @Override
  public Parser<Entities.MergeInput> getParser() {
    return Entities.MergeInput.parser();
  }
}
