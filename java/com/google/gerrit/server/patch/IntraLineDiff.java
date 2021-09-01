// Copyright (C) 2010 The Android Open Source Project
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
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.CodedEnum;
import com.google.gerrit.jgit.diff.ReplaceEdit;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.IntraLineDiffProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.diff.Edit;

@AutoValue
public abstract class IntraLineDiff {
  public enum Status implements CodedEnum {
    EDIT_LIST('e'),
    DISABLED('D'),
    TIMEOUT('T'),
    ERROR('E');

    private final char code;

    Status(char code) {
      this.code = code;
    }

    @Override
    public char getCode() {
      return code;
    }
  }

  public abstract Status status();

  public abstract ImmutableList<Edit> edits();

  public static IntraLineDiff create(Status status) {
    return new AutoValue_IntraLineDiff(status, ImmutableList.of());
  }

  @VisibleForTesting
  public static IntraLineDiff create(List<Edit> edits) {
    return new AutoValue_IntraLineDiff(Status.EDIT_LIST, ImmutableList.copyOf(edits));
  }

  public enum Serializer implements CacheSerializer<IntraLineDiff> {
    INSTANCE;

    enum EditType {
      NORMAL,
      REPLACE
    }

    @Override
    public byte[] serialize(IntraLineDiff diff) {
      return IntraLineDiffProto.newBuilder()
          .setStatus(diff.status().name())
          .addAllEdits(
              diff.edits().stream().map(Serializer::editToProto).collect(Collectors.toList()))
          .build()
          .toByteArray();
    }

    @Override
    public IntraLineDiff deserialize(byte[] in) {
      IntraLineDiffProto proto = Protos.parseUnchecked(IntraLineDiffProto.parser(), in);
      if (proto.getEditsList().isEmpty()) {
        return IntraLineDiff.create(Status.valueOf(proto.getStatus()));
      }
      ImmutableList<Edit> edits =
          proto.getEditsList().stream()
              .map(e -> protoToEdit(e))
              .collect(ImmutableList.toImmutableList());
      return IntraLineDiff.create(edits);
    }

    private static IntraLineDiffProto.Edit editToProto(Edit edit) {
      IntraLineDiffProto.Edit.Builder builder =
          IntraLineDiffProto.Edit.newBuilder()
              .setEditType(EditType.NORMAL.name())
              .setBeginA(edit.getBeginA())
              .setEndA(edit.getEndA())
              .setBeginB(edit.getBeginB())
              .setEndB(edit.getEndB());
      if (edit instanceof ReplaceEdit) {
        ReplaceEdit r = (ReplaceEdit) edit;
        builder
            .setEditType(EditType.REPLACE.name())
            .addAllEdits(
                r.getInternalEdits().stream()
                    .map(e -> editToProto(e))
                    .collect(Collectors.toList()));
      }
      return builder.build();
    }

    private static Edit protoToEdit(IntraLineDiffProto.Edit proto) {
      Edit e = new Edit(proto.getBeginA(), proto.getEndA(), proto.getBeginB(), proto.getEndB());
      if (EditType.NORMAL.name().equals(proto.getEditType())) {
        return e;
      }
      List<Edit> internalEdits =
          proto.getEditsList().stream().map(Serializer::protoToEdit).collect(Collectors.toList());
      return new ReplaceEdit(e, internalEdits);
    }
  }
}
