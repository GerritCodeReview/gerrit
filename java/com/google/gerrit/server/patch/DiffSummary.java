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
import com.google.common.collect.ImmutableList;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.DiffSummaryProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.query.change.ChangeData.ChangedLines;

@AutoValue
public abstract class DiffSummary {
  public abstract ImmutableList<String> paths();

  public abstract int insertions();

  public abstract int deletions();

  public static DiffSummary create(ImmutableList<String> paths, int insertions, int deletions) {
    return new AutoValue_DiffSummary(paths, insertions, deletions);
  }

  public ChangedLines getChangedLines() {
    return new ChangedLines(insertions(), deletions());
  }

  public enum Serializer implements CacheSerializer<DiffSummary> {
    INSTANCE;

    @Override
    public byte[] serialize(DiffSummary diffSummary) {
      return DiffSummaryProto.newBuilder()
          .setInsertions(diffSummary.insertions())
          .setDeletions(diffSummary.deletions())
          .addAllPaths(diffSummary.paths())
          .build()
          .toByteArray();
    }

    @Override
    public DiffSummary deserialize(byte[] in) {
      DiffSummaryProto proto = Protos.parseUnchecked(DiffSummaryProto.parser(), in);
      return DiffSummary.create(
          proto.getPathsList().stream().collect(ImmutableList.toImmutableList()),
          proto.getInsertions(),
          proto.getDeletions());
    }
  }
}
