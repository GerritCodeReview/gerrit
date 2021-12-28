// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.DiffSummaryKeyProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;

@AutoValue
public abstract class DiffSummaryKey {
  /**
   * The 20 bytes SHA-1 commit ID of the old commit used in the diff. This is set to {@link
   * Optional#empty()} if {@link #newId()} is a root commit or the diff is against the auto-merge.
   */
  @Nullable
  public abstract Optional<ObjectId> oldId();

  /**
   * The one-based parent number that indicates which parent {@link #oldId()} is for the {@link
   * #newId()} commit. This is set to {@link Optional#empty()} if {@link #oldId()} is the auto-merge
   * commit.
   */
  @Nullable
  public abstract Optional<Integer> parentNum();

  /** The 20 bytes SHA-1 commit ID of the new commit used in the diff. */
  public abstract ObjectId newId();

  public abstract Whitespace whitespace();

  public static DiffSummaryKey fromPatchListKey(PatchListKey plk) {
    return create(plk.getOldId(), plk.getParentNum(), plk.getNewId(), plk.getWhitespace());
  }

  @VisibleForTesting
  public static DiffSummaryKey create(
      @Nullable ObjectId oldId,
      @Nullable Integer parentNum,
      ObjectId newId,
      Whitespace whitespace) {
    return new AutoValue_DiffSummaryKey(
        Optional.ofNullable(oldId), Optional.ofNullable(parentNum), newId, whitespace);
  }

  PatchListKey toPatchListKey() {
    return new PatchListKey(oldId().orElse(null), parentNum().orElse(null), newId(), whitespace());
  }

  public enum Serializer implements CacheSerializer<DiffSummaryKey> {
    INSTANCE;

    @Override
    public byte[] serialize(DiffSummaryKey diffSummaryKey) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      DiffSummaryKeyProto.Builder builder =
          DiffSummaryKeyProto.newBuilder()
              .setNewId(idConverter.toByteString(diffSummaryKey.newId()))
              .setWhitespace(diffSummaryKey.whitespace().name());
      if (diffSummaryKey.oldId().isPresent()) {
        builder.setOldId(idConverter.toByteString(diffSummaryKey.oldId().get()));
      }
      if (diffSummaryKey.parentNum().isPresent()) {
        builder.setParentNum(diffSummaryKey.parentNum().get());
      }
      return Protos.toByteArray(builder.build());
    }

    @Override
    public DiffSummaryKey deserialize(byte[] in) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      DiffSummaryKeyProto proto = Protos.parseUnchecked(DiffSummaryKeyProto.parser(), in);
      return DiffSummaryKey.create(
          proto.getOldId().isEmpty() ? null : idConverter.fromByteString(proto.getOldId()),
          proto.getParentNum() == 0 ? null : proto.getParentNum(),
          idConverter.fromByteString(proto.getNewId()),
          Whitespace.valueOf(proto.getWhitespace()));
    }
  }

  @Override
  public final String toString() {
    StringBuilder n = new StringBuilder();
    n.append("DiffSummaryKey[");
    n.append(oldId().isPresent() ? oldId().get().name() : "BASE");
    n.append("..");
    n.append(newId().name());
    n.append(" ");
    if (parentNum().isPresent()) {
      n.append(parentNum().get());
      n.append(" ");
    }
    n.append(whitespace().name());
    n.append("]");
    return n.toString();
  }
}
