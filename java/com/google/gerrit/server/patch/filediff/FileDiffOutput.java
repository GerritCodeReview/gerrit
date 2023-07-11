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

package com.google.gerrit.server.patch.filediff;

import static com.google.gerrit.server.patch.DiffUtil.stringSize;

import com.google.auto.value.AutoValue;
import com.google.common.base.Converter;
import com.google.common.base.Enums;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.Patch.FileMode;
import com.google.gerrit.entities.Patch.PatchType;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.FileDiffOutputProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.patch.ComparisonType;
import com.google.protobuf.Descriptors.FieldDescriptor;
import java.io.Serializable;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;

/** File diff for a single file path. Produced as output of the {@link FileDiffCache}. */
@AutoValue
public abstract class FileDiffOutput implements Serializable {
  private static final long serialVersionUID = 1L;

  /**
   * The 20 bytes SHA-1 object ID of the old git commit used in the diff, or {@link
   * ObjectId#zeroId()} if {@link #newCommitId()} was a root commit.
   */
  public abstract ObjectId oldCommitId();

  /** The 20 bytes SHA-1 object ID of the new git commit used in the diff. */
  public abstract ObjectId newCommitId();

  /** Comparison type of old and new commits: against another patchset, parent or auto-merge. */
  public abstract ComparisonType comparisonType();

  /**
   * The file path at the old commit. Returns an empty Optional if {@link #changeType()} is equal to
   * {@link ChangeType#ADDED}.
   */
  public abstract Optional<String> oldPath();

  /**
   * The file path at the new commit. Returns an empty optional if {@link #changeType()} is equal to
   * {@link ChangeType#DELETED}.
   */
  public abstract Optional<String> newPath();

  public String getDefaultPath() {
    return oldPath().isPresent() ? oldPath().get() : newPath().get();
  }

  /**
   * The file mode of the old file at the old git tree diff identified by {@link #oldCommitId()}
   * ()}.
   */
  public abstract Optional<Patch.FileMode> oldMode();

  /**
   * The file mode of the new file at the new git tree diff identified by {@link #newCommitId()}
   * ()}.
   */
  public abstract Optional<Patch.FileMode> newMode();

  /** The change type of the underlying file, e.g. added, deleted, renamed, etc... */
  public abstract Patch.ChangeType changeType();

  /** The patch type of the underlying file, e.g. unified, binary , etc... */
  public abstract Optional<Patch.PatchType> patchType();

  /**
   * A list of strings representation of the header lines of the {@link
   * org.eclipse.jgit.patch.FileHeader} that is produced as output of the diff.
   */
  public abstract ImmutableList<String> headerLines();

  /** The list of edits resulting from the diff hunks of the file. */
  public abstract ImmutableList<TaggedEdit> edits();

  /** The file size at the new commit. */
  public abstract long size();

  /** Difference in file size between the old and new commits. */
  public abstract long sizeDelta();

  /**
   * Returns {@code true} if the diff computation was not able to compute a diff, i.e. for diffs
   * taking a very long time to compute. We cache negative result in this case.
   */
  public abstract Optional<Boolean> negative();

  public abstract Builder toBuilder();

  /** A boolean indicating if all underlying edits of the file diff are due to rebase. */
  public boolean allEditsDueToRebase() {
    return !edits().isEmpty() && edits().stream().allMatch(TaggedEdit::dueToRebase);
  }

  /** Returns the number of inserted lines for the file diff. */
  public int insertions() {
    int ins = 0;
    for (TaggedEdit e : edits()) {
      if (!e.dueToRebase()) {
        ins += e.edit().endB() - e.edit().beginB();
      }
    }
    return ins;
  }

  /** Returns the number of deleted lines for the file diff. */
  public int deletions() {
    int del = 0;
    for (TaggedEdit e : edits()) {
      if (!e.dueToRebase()) {
        del += e.edit().endA() - e.edit().beginA();
      }
    }
    return del;
  }

  /** Returns an entity representing an unchanged file between two commits. */
  public static FileDiffOutput empty(String filePath, ObjectId oldCommitId, ObjectId newCommitId) {
    return builder()
        .oldCommitId(oldCommitId)
        .newCommitId(newCommitId)
        .comparisonType(ComparisonType.againstOtherPatchSet()) // not important
        .oldPath(Optional.empty())
        .newPath(Optional.of(filePath))
        .changeType(ChangeType.MODIFIED)
        .headerLines(ImmutableList.of())
        .edits(ImmutableList.of())
        .size(0)
        .sizeDelta(0)
        .build();
  }

  /**
   * Create a negative file diff. We use this to cache negative diffs for entries that result in
   * timeouts.
   */
  public static FileDiffOutput createNegative(
      String filePath, ObjectId oldCommitId, ObjectId newCommitId) {
    return empty(filePath, oldCommitId, newCommitId)
        .toBuilder()
        .negative(Optional.of(true))
        .build();
  }

  /** Returns true if this entity represents an unchanged file between two commits. */
  public boolean isEmpty() {
    return headerLines().isEmpty() && edits().isEmpty();
  }

  /**
   * Returns {@code true} if the diff computation was not able to compute a diff. We cache negative
   * result in this case.
   */
  public boolean isNegative() {
    return negative().isPresent() && negative().get();
  }

  public static Builder builder() {
    return new AutoValue_FileDiffOutput.Builder();
  }

  public int weight() {
    int result = 0;
    if (oldPath().isPresent()) {
      result += stringSize(oldPath().get());
    }
    if (newPath().isPresent()) {
      result += stringSize(newPath().get());
    }
    result += 20 + 20; // old and new commit IDs
    result += 4; // comparison type
    result += 4; // changeType
    if (patchType().isPresent()) {
      result += 4;
    }
    result += 4 + 4; // insertions and deletions
    result += 4 + 4; // size and size delta
    result += 20 * edits().size(); // each edit is 4 Integers + boolean = 4 * 4 + 4 = 20
    for (String s : headerLines()) {
      s += stringSize(s);
    }
    if (negative().isPresent()) {
      result += 1;
    }
    return result;
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder oldCommitId(ObjectId value);

    public abstract Builder newCommitId(ObjectId value);

    public abstract Builder comparisonType(ComparisonType value);

    public abstract Builder oldPath(Optional<String> value);

    public abstract Builder newPath(Optional<String> value);

    public abstract Builder oldMode(Optional<Patch.FileMode> oldMode);

    public abstract Builder newMode(Optional<Patch.FileMode> newMode);

    public abstract Builder changeType(ChangeType value);

    public abstract Builder patchType(Optional<PatchType> value);

    public abstract Builder headerLines(ImmutableList<String> value);

    public abstract Builder edits(ImmutableList<TaggedEdit> value);

    public abstract Builder size(long value);

    public abstract Builder sizeDelta(long value);

    public abstract Builder negative(Optional<Boolean> value);

    public abstract FileDiffOutput build();
  }

  public enum Serializer implements CacheSerializer<FileDiffOutput> {
    INSTANCE;

    private static final Converter<String, FileMode> FILE_MODE_CONVERTER =
        Enums.stringConverter(Patch.FileMode.class);

    private static final FieldDescriptor OLD_PATH_DESCRIPTOR =
        FileDiffOutputProto.getDescriptor().findFieldByNumber(1);

    private static final FieldDescriptor NEW_PATH_DESCRIPTOR =
        FileDiffOutputProto.getDescriptor().findFieldByNumber(2);

    private static final FieldDescriptor PATCH_TYPE_DESCRIPTOR =
        FileDiffOutputProto.getDescriptor().findFieldByNumber(4);

    private static final FieldDescriptor NEGATIVE_DESCRIPTOR =
        FileDiffOutputProto.getDescriptor().findFieldByNumber(12);

    private static final FieldDescriptor OLD_MODE_DESCRIPTOR =
        FileDiffOutputProto.getDescriptor().findFieldByNumber(13);

    private static final FieldDescriptor NEW_MODE_DESCRIPTOR =
        FileDiffOutputProto.getDescriptor().findFieldByNumber(14);

    @Override
    public byte[] serialize(FileDiffOutput fileDiff) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      FileDiffOutputProto.Builder builder =
          FileDiffOutputProto.newBuilder()
              .setOldCommit(idConverter.toByteString(fileDiff.oldCommitId().toObjectId()))
              .setNewCommit(idConverter.toByteString(fileDiff.newCommitId().toObjectId()))
              .setComparisonType(fileDiff.comparisonType().toProto())
              .setSize(fileDiff.size())
              .setSizeDelta(fileDiff.sizeDelta())
              .addAllHeaderLines(fileDiff.headerLines())
              .setChangeType(fileDiff.changeType().name())
              .addAllEdits(
                  fileDiff.edits().stream()
                      .map(
                          e ->
                              FileDiffOutputProto.TaggedEdit.newBuilder()
                                  .setEdit(
                                      FileDiffOutputProto.Edit.newBuilder()
                                          .setBeginA(e.edit().beginA())
                                          .setEndA(e.edit().endA())
                                          .setBeginB(e.edit().beginB())
                                          .setEndB(e.edit().endB())
                                          .build())
                                  .setDueToRebase(e.dueToRebase())
                                  .build())
                      .collect(Collectors.toList()));

      if (fileDiff.oldPath().isPresent()) {
        builder.setOldPath(fileDiff.oldPath().get());
      }

      if (fileDiff.newPath().isPresent()) {
        builder.setNewPath(fileDiff.newPath().get());
      }

      if (fileDiff.patchType().isPresent()) {
        builder.setPatchType(fileDiff.patchType().get().name());
      }

      if (fileDiff.negative().isPresent()) {
        builder.setNegative(fileDiff.negative().get());
      }

      if (fileDiff.oldMode().isPresent()) {
        builder.setOldMode(FILE_MODE_CONVERTER.reverse().convert(fileDiff.oldMode().get()));
      }
      if (fileDiff.newMode().isPresent()) {
        builder.setNewMode(FILE_MODE_CONVERTER.reverse().convert(fileDiff.newMode().get()));
      }

      return Protos.toByteArray(builder.build());
    }

    @Override
    public FileDiffOutput deserialize(byte[] in) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      FileDiffOutputProto proto = Protos.parseUnchecked(FileDiffOutputProto.parser(), in);
      FileDiffOutput.Builder builder = FileDiffOutput.builder();
      builder
          .oldCommitId(idConverter.fromByteString(proto.getOldCommit()))
          .newCommitId(idConverter.fromByteString(proto.getNewCommit()))
          .comparisonType(ComparisonType.fromProto(proto.getComparisonType()))
          .size(proto.getSize())
          .sizeDelta(proto.getSizeDelta())
          .headerLines(proto.getHeaderLinesList().stream().collect(ImmutableList.toImmutableList()))
          .changeType(ChangeType.valueOf(proto.getChangeType()))
          .edits(
              proto.getEditsList().stream()
                  .map(
                      e ->
                          TaggedEdit.create(
                              Edit.create(
                                  e.getEdit().getBeginA(),
                                  e.getEdit().getEndA(),
                                  e.getEdit().getBeginB(),
                                  e.getEdit().getEndB()),
                              e.getDueToRebase()))
                  .collect(ImmutableList.toImmutableList()));

      if (proto.hasField(OLD_PATH_DESCRIPTOR)) {
        builder.oldPath(Optional.of(proto.getOldPath()));
      }
      if (proto.hasField(NEW_PATH_DESCRIPTOR)) {
        builder.newPath(Optional.of(proto.getNewPath()));
      }
      if (proto.hasField(PATCH_TYPE_DESCRIPTOR)) {
        builder.patchType(Optional.of(Patch.PatchType.valueOf(proto.getPatchType())));
      }
      if (proto.hasField(NEGATIVE_DESCRIPTOR)) {
        builder.negative(Optional.of(proto.getNegative()));
      }
      if (proto.hasField(OLD_MODE_DESCRIPTOR)) {
        builder.oldMode(Optional.of(FILE_MODE_CONVERTER.convert(proto.getOldMode())));
      }
      if (proto.hasField(NEW_MODE_DESCRIPTOR)) {
        builder.newMode(Optional.of(FILE_MODE_CONVERTER.convert(proto.getNewMode())));
      }
      return builder.build();
    }
  }
}
