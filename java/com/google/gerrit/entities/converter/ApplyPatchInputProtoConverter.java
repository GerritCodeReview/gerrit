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
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.proto.Entities;
import com.google.protobuf.Parser;

/**
 * Proto converter between {@link ApplyPatchInput} and {@link
 * com.google.gerrit.proto.Entities.ApplyPatchInput}.
 */
@Immutable
public enum ApplyPatchInputProtoConverter
    implements ProtoConverter<Entities.ApplyPatchInput, ApplyPatchInput> {
  INSTANCE;

  @Override
  public Entities.ApplyPatchInput toProto(ApplyPatchInput applyPatchInput) {
    Entities.ApplyPatchInput.Builder builder = Entities.ApplyPatchInput.newBuilder();
    if (applyPatchInput.patch != null) {
      builder.setPatch(applyPatchInput.patch);
    }
    if (applyPatchInput.allowConflicts != null) {
      builder.setAllowConflicts(applyPatchInput.allowConflicts);
    }
    return builder.build();
  }

  @Override
  public ApplyPatchInput fromProto(Entities.ApplyPatchInput proto) {
    ApplyPatchInput applyPatchInput = new ApplyPatchInput();
    if (proto.hasPatch()) {
      applyPatchInput.patch = proto.getPatch();
    }
    if (proto.hasAllowConflicts()) {
      applyPatchInput.allowConflicts = proto.getAllowConflicts();
    }
    return applyPatchInput;
  }

  @Override
  public Parser<Entities.ApplyPatchInput> getParser() {
    return Entities.ApplyPatchInput.parser();
  }
}
