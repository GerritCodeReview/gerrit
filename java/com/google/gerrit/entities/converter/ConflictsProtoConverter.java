// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.entities.converter;

import com.google.errorprone.annotations.Immutable;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.extensions.common.NoMergeBaseReason;
import com.google.gerrit.proto.Entities;
import com.google.protobuf.Parser;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

@Immutable
public enum ConflictsProtoConverter
    implements SafeProtoConverter<Entities.Conflicts, PatchSet.Conflicts> {
  INSTANCE;

  private final ProtoConverter<Entities.ObjectId, ObjectId> objectIdConverter =
      ObjectIdProtoConverter.INSTANCE;

  @Override
  public Entities.Conflicts toProto(PatchSet.Conflicts conflicts) {
    Entities.Conflicts.Builder builder = Entities.Conflicts.newBuilder();
    conflicts.base().ifPresent(base -> builder.setBase(objectIdConverter.toProto(base)));
    conflicts.ours().ifPresent(ours -> builder.setOurs(objectIdConverter.toProto(ours)));
    conflicts.theirs().ifPresent(theirs -> builder.setTheirs(objectIdConverter.toProto(theirs)));
    conflicts.mergeStrategy().ifPresent(mergeStrategy -> builder.setMergeStrategy(mergeStrategy));
    conflicts
        .noBaseReason()
        .ifPresent(
            noBaseReason ->
                builder.setNoBaseReason(
                    Entities.NoMergeBaseReason.forNumber(noBaseReason.getValue())));
    return builder.setContainsConflicts(conflicts.containsConflicts()).build();
  }

  @Override
  public PatchSet.Conflicts fromProto(Entities.Conflicts proto) {
    return PatchSet.Conflicts.create(
        proto.hasBase()
            ? Optional.of(objectIdConverter.fromProto(proto.getBase()))
            : Optional.empty(),
        proto.hasOurs()
            ? Optional.of(objectIdConverter.fromProto(proto.getOurs()))
            : Optional.empty(),
        proto.hasTheirs()
            ? Optional.of(objectIdConverter.fromProto(proto.getTheirs()))
            : Optional.empty(),
        proto.hasMergeStrategy() ? Optional.of(proto.getMergeStrategy()) : Optional.empty(),
        proto.hasNoBaseReason()
            ? Optional.of(NoMergeBaseReason.valueOf(proto.getNoBaseReason().name()))
            : Optional.empty(),
        proto.hasContainsConflicts() ? proto.getContainsConflicts() : false);
  }

  @Override
  public Parser<Entities.Conflicts> getParser() {
    return Entities.Conflicts.parser();
  }

  @Override
  public Class<Entities.Conflicts> getProtoClass() {
    return Entities.Conflicts.class;
  }

  @Override
  public Class<PatchSet.Conflicts> getEntityClass() {
    return PatchSet.Conflicts.class;
  }
}
