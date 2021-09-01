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
import com.google.gerrit.server.cache.proto.Cache.IntraLineDiffProto.Status;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.patch.IntraLineDiff.IntraLineEdit.EditType;
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

  abstract ImmutableList<IntraLineEdit> intraLineEdits();

  public ImmutableList<Edit> edits() {
    return intraLineEdits().stream()
        .map(IntraLineEdit::toJgitEdit)
        .collect(ImmutableList.toImmutableList());
  }

  public static IntraLineDiff create(Status status) {
    return new AutoValue_IntraLineDiff(status, ImmutableList.of());
  }

  @VisibleForTesting
  public static IntraLineDiff create(List<Edit> edits) {
    // Store the list of edits as IntraLineEdit(s) to preserve Immutability, since the JGit Edit
    // class is mutable.
    return create(
        edits.stream().map(IntraLineEdit::create).collect(ImmutableList.toImmutableList()));
  }

  static IntraLineDiff create(ImmutableList<IntraLineEdit> intraLineEdits) {
    return new AutoValue_IntraLineDiff(Status.EDIT_LIST, intraLineEdits);
  }

  public enum Serializer implements CacheSerializer<IntraLineDiff> {
    INSTANCE;

    @Override
    public byte[] serialize(IntraLineDiff diff) {
      return IntraLineDiffProto.newBuilder()
          .setStatus(IntraLineDiffProto.Status.valueOf(diff.status().name()))
          .addAllEdits(
              diff.intraLineEdits().stream()
                  .map(Serializer::editToProto)
                  .collect(Collectors.toList()))
          .build()
          .toByteArray();
    }

    @Override
    public IntraLineDiff deserialize(byte[] in) {
      IntraLineDiffProto proto = Protos.parseUnchecked(IntraLineDiffProto.parser(), in);
      // Handle unknown deserialized values. Convert to the default
      Status status = Status.ERROR; // default
      if (!proto.getStatus().equals(IntraLineDiffProto.Status.UNRECOGNIZED)) {
        status = Status.valueOf(proto.getStatus().name());
      }
      if (proto.getEditsList().isEmpty()) {
        // If edits are empty, IntraLineDiff was created with the IntraLineDiff(Status) constructor
        // hence use this for deserialization.
        return IntraLineDiff.create(status);
      }
      ImmutableList<IntraLineEdit> edits =
          proto.getEditsList().stream()
              .map(e -> protoToEdit(e))
              .collect(ImmutableList.toImmutableList());
      return IntraLineDiff.create(edits);
    }

    private static IntraLineDiffProto.Edit editToProto(IntraLineEdit edit) {
      return IntraLineDiffProto.Edit.newBuilder()
          .setEditType(IntraLineDiffProto.EditType.valueOf(edit.editType().name()))
          .setBeginA(edit.beginA())
          .setEndA(edit.endA())
          .setBeginB(edit.beginB())
          .setEndB(edit.endB())
          .addAllInternalEdits(
              edit.internalEdits().stream().map(e -> editToProto(e)).collect(Collectors.toList()))
          .build();
    }

    private static IntraLineEdit protoToEdit(IntraLineDiffProto.Edit proto) {
      // Handle unknown deserialized values. Convert to the default
      EditType editType = EditType.NORMAL; // default
      if (!proto.getEditType().equals(IntraLineDiffProto.EditType.UNRECOGNIZED)) {
        editType = EditType.valueOf(proto.getEditType().name());
      }
      return IntraLineEdit.builder()
          .editType(editType)
          .beginA(proto.getBeginA())
          .endA(proto.getEndA())
          .beginB(proto.getBeginB())
          .endB(proto.getEndB())
          .internalEdits(
              proto.getInternalEditsList().stream()
                  .map(Serializer::protoToEdit)
                  .collect(ImmutableList.toImmutableList()))
          .build();
    }
  }

  /**
   * An Immutable representation of the JGit {@link Edit} entity. This entity could either represent
   * an {@link Edit} or a {@link ReplaceEdit}.
   *
   * <p>We define this entity since {@link IntraLineDiff} should be immutable.
   */
  @AutoValue
  abstract static class IntraLineEdit {
    enum EditType {
      NORMAL,
      REPLACE
    }

    abstract EditType editType();

    abstract int beginA();

    abstract int endA();

    abstract int beginB();

    abstract int endB();

    /**
     * Contains the list of internal edits if this {@link IntraLineEdit} is of type {@link
     * EditType#REPLACE}. For edits of type {@link EditType#NORMAL} this list will be empty.
     */
    abstract ImmutableList<IntraLineEdit> internalEdits();

    /** Create an {@link IntraLineEdit} from a JGit {@link Edit}. */
    static IntraLineEdit create(Edit edit) {
      ImmutableList<IntraLineEdit> internalEdits =
          edit instanceof ReplaceEdit
              ? ((ReplaceEdit) edit)
                  .getInternalEdits().stream()
                      .map(IntraLineEdit::create)
                      .collect(ImmutableList.toImmutableList())
              : ImmutableList.of();
      return builder()
          .editType(edit instanceof ReplaceEdit ? EditType.REPLACE : EditType.NORMAL)
          .beginA(edit.getBeginA())
          .endA(edit.getEndA())
          .beginB(edit.getBeginB())
          .endB(edit.getEndB())
          .internalEdits(internalEdits)
          .build();
    }

    Edit toJgitEdit() {
      if (editType().equals(EditType.NORMAL)) {
        return new Edit(beginA(), endA(), beginB(), endB());
      }
      List<Edit> internalEdits =
          internalEdits().stream().map(IntraLineEdit::toJgitEdit).collect(Collectors.toList());
      return new ReplaceEdit(beginA(), endA(), beginB(), endB(), internalEdits);
    }

    static IntraLineEdit.Builder builder() {
      return new AutoValue_IntraLineDiff_IntraLineEdit.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder editType(EditType editType);

      abstract Builder beginA(int beginA);

      abstract Builder endA(int endA);

      abstract Builder beginB(int beginB);

      abstract Builder endB(int endB);

      abstract Builder internalEdits(ImmutableList<IntraLineEdit> internalEdits);

      abstract IntraLineEdit build();
    }
  }
}
