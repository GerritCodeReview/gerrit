//  Copyright (C) 2020 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package com.google.gerrit.server.patch.gitfilediff;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.server.patch.DiffUtil.stringSize;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.Patch.PatchType;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.proto.Cache.GitFileDiffProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.patch.filediff.Edit;
import com.google.protobuf.Descriptors.FieldDescriptor;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.AbbreviatedObjectId;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.patch.FileHeader;

/**
 * Entity representing a modified file (added, deleted, modified, renamed, etc...) between two
 * different git commits.
 */
@AutoValue
public abstract class GitFileDiff {
  private static final Map<FileMode, Patch.FileMode> fileModeMap =
      ImmutableMap.<FileMode, Patch.FileMode>builder()
          .put(FileMode.TREE, Patch.FileMode.TREE)
          .put(FileMode.SYMLINK, Patch.FileMode.SYMLINK)
          .put(FileMode.GITLINK, Patch.FileMode.GITLINK)
          .put(FileMode.REGULAR_FILE, Patch.FileMode.REGULAR_FILE)
          .put(FileMode.EXECUTABLE_FILE, Patch.FileMode.EXECUTABLE_FILE)
          .put(FileMode.MISSING, Patch.FileMode.MISSING)
          .build();

  private static Patch.FileMode mapFileMode(FileMode jgitFileMode) {
    if (!fileModeMap.containsKey(jgitFileMode)) {
      throw new IllegalArgumentException("Unsupported type " + jgitFileMode);
    }
    return fileModeMap.get(jgitFileMode);
  }

  /**
   * Creates a {@link GitFileDiff} using the {@code diffEntry} and the {@code diffFormatter}
   * parameters.
   */
  static GitFileDiff create(DiffEntry diffEntry, DiffFormatter diffFormatter) throws IOException {
    FileHeader fileHeader = diffFormatter.toFileHeader(diffEntry);
    ImmutableList<Edit> edits =
        fileHeader.toEditList().stream().map(Edit::fromJGitEdit).collect(toImmutableList());

    return builder()
        .edits(edits)
        .oldId(diffEntry.getOldId())
        .newId(diffEntry.getNewId())
        .fileHeader(FileHeaderUtil.toString(fileHeader))
        .oldPath(FileHeaderUtil.getOldPath(fileHeader))
        .newPath(FileHeaderUtil.getNewPath(fileHeader))
        .changeType(Optional.of(FileHeaderUtil.getChangeType(fileHeader)))
        .patchType(Optional.of(FileHeaderUtil.getPatchType(fileHeader)))
        .oldMode(Optional.of(mapFileMode(diffEntry.getOldMode())))
        .newMode(Optional.of(mapFileMode(diffEntry.getNewMode())))
        .build();
  }

  /**
   * Represents an empty file diff, which means that the file was not modified between the two git
   * trees identified by {@link #oldId()} and {@link #newId()}.
   *
   * @param newFilePath the file name at the {@link #newId()} git tree.
   */
  static GitFileDiff empty(
      AbbreviatedObjectId oldId, AbbreviatedObjectId newId, String newFilePath) {
    return builder()
        .oldId(oldId)
        .newId(newId)
        .newPath(Optional.of(newFilePath))
        .edits(ImmutableList.of())
        .fileHeader("")
        .build();
  }

  /** An {@link ImmutableList} of the modified regions in the file. */
  public abstract ImmutableList<Edit> edits();

  /** A string representation of the {@link org.eclipse.jgit.patch.FileHeader}. */
  public abstract String fileHeader();

  /** The file name at the old git tree identified by {@link #oldId()} */
  public abstract Optional<String> oldPath();

  /** The file name at the new git tree identified by {@link #newId()} */
  public abstract Optional<String> newPath();

  /** The 20 bytes SHA-1 object ID of the old git tree of the diff. */
  public abstract AbbreviatedObjectId oldId();

  /** The 20 bytes SHA-1 object ID of the new git tree of the diff. */
  public abstract AbbreviatedObjectId newId();

  /** The file mode of the old file at the old git tree diff identified by {@link #oldId()}. */
  public abstract Optional<Patch.FileMode> oldMode();

  /** The file mode of the new file at the new git tree diff identified by {@link #newId()}. */
  public abstract Optional<Patch.FileMode> newMode();

  /** The change type associated with the file. */
  public abstract Optional<ChangeType> changeType();

  /** The patch type associated with the file. */
  public abstract Optional<PatchType> patchType();

  /**
   * Returns true if the object was created using the {@link #empty(AbbreviatedObjectId,
   * AbbreviatedObjectId, String)} method.
   */
  public boolean isEmpty() {
    return edits().isEmpty();
  }

  /** Returns the size of the object in bytes. */
  public int weight() {
    int result = 20 * 2; // oldId and newId
    result += 16 * edits().size(); // each edit contains 4 integers (hence 16 bytes)
    result += stringSize(fileHeader());
    if (oldPath().isPresent()) {
      result += stringSize(oldPath().get());
    }
    if (newPath().isPresent()) {
      result += stringSize(newPath().get());
    }
    if (changeType().isPresent()) {
      result += 4;
    }
    if (patchType().isPresent()) {
      result += 4;
    }
    if (oldMode().isPresent()) {
      result += 4;
    }
    if (newMode().isPresent()) {
      result += 4;
    }
    return result;
  }

  public static Builder builder() {
    return new AutoValue_GitFileDiff.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder edits(ImmutableList<Edit> value);

    public abstract Builder fileHeader(String value);

    public abstract Builder oldPath(Optional<String> value);

    public abstract Builder newPath(Optional<String> value);

    public abstract Builder oldId(AbbreviatedObjectId value);

    public abstract Builder newId(AbbreviatedObjectId value);

    public abstract Builder oldMode(Optional<Patch.FileMode> value);

    public abstract Builder newMode(Optional<Patch.FileMode> value);

    public abstract Builder changeType(Optional<ChangeType> value);

    public abstract Builder patchType(Optional<PatchType> value);

    public abstract GitFileDiff build();
  }

  public enum Serializer implements CacheSerializer<GitFileDiff> {
    INSTANCE;

    private static final FieldDescriptor OLD_PATH_DESCRIPTOR =
        GitFileDiffProto.getDescriptor().findFieldByNumber(3);

    private static final FieldDescriptor NEW_PATH_DESCRIPTOR =
        GitFileDiffProto.getDescriptor().findFieldByNumber(4);

    private static final FieldDescriptor OLD_MODE_DESCRIPTOR =
        GitFileDiffProto.getDescriptor().findFieldByNumber(7);

    private static final FieldDescriptor NEW_MODE_DESCRIPTOR =
        GitFileDiffProto.getDescriptor().findFieldByNumber(8);

    private static final FieldDescriptor CHANGE_TYPE_DESCRIPTOR =
        GitFileDiffProto.getDescriptor().findFieldByNumber(9);

    private static final FieldDescriptor PATCH_TYPE_DESCRIPTOR =
        GitFileDiffProto.getDescriptor().findFieldByNumber(10);

    @Override
    public byte[] serialize(GitFileDiff gitFileDiff) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      GitFileDiffProto.Builder builder =
          GitFileDiffProto.newBuilder()
              .setFileHeader(gitFileDiff.fileHeader())
              .setOldId(idConverter.toByteString(gitFileDiff.oldId().toObjectId()))
              .setNewId(idConverter.toByteString(gitFileDiff.newId().toObjectId()));
      gitFileDiff
          .edits()
          .forEach(
              e ->
                  builder.addEdits(
                      GitFileDiffProto.Edit.newBuilder()
                          .setBeginA(e.beginA())
                          .setEndA(e.endA())
                          .setBeginB(e.beginB())
                          .setEndB(e.endB())));
      if (gitFileDiff.oldPath().isPresent()) {
        builder.setOldPath(gitFileDiff.oldPath().get());
      }
      if (gitFileDiff.newPath().isPresent()) {
        builder.setNewPath(gitFileDiff.newPath().get());
      }
      if (gitFileDiff.oldMode().isPresent()) {
        builder.setOldMode(gitFileDiff.oldMode().get().name());
      }
      if (gitFileDiff.newMode().isPresent()) {
        builder.setNewMode(gitFileDiff.newMode().get().name());
      }
      if (gitFileDiff.changeType().isPresent()) {
        builder.setChangeType(gitFileDiff.changeType().get().name());
      }
      if (gitFileDiff.patchType().isPresent()) {
        builder.setPatchType(gitFileDiff.patchType().get().name());
      }
      return Protos.toByteArray(builder.build());
    }

    @Override
    public GitFileDiff deserialize(byte[] in) {
      ObjectIdConverter idConverter = ObjectIdConverter.create();
      GitFileDiffProto proto = Protos.parseUnchecked(GitFileDiffProto.parser(), in);
      GitFileDiff.Builder builder = GitFileDiff.builder();
      builder
          .edits(
              proto.getEditsList().stream()
                  .map(e -> Edit.create(e.getBeginA(), e.getEndA(), e.getBeginB(), e.getEndB()))
                  .collect(toImmutableList()))
          .fileHeader(proto.getFileHeader())
          .oldId(AbbreviatedObjectId.fromObjectId(idConverter.fromByteString(proto.getOldId())))
          .newId(AbbreviatedObjectId.fromObjectId(idConverter.fromByteString(proto.getNewId())));

      if (proto.hasField(OLD_PATH_DESCRIPTOR)) {
        builder.oldPath(Optional.of(proto.getOldPath()));
      }
      if (proto.hasField(NEW_PATH_DESCRIPTOR)) {
        builder.newPath(Optional.of(proto.getNewPath()));
      }
      if (proto.hasField(OLD_MODE_DESCRIPTOR)) {
        builder.oldMode(Optional.of(Patch.FileMode.valueOf(proto.getOldMode())));
      }
      if (proto.hasField(NEW_MODE_DESCRIPTOR)) {
        builder.newMode(Optional.of(Patch.FileMode.valueOf(proto.getNewMode())));
      }
      if (proto.hasField(CHANGE_TYPE_DESCRIPTOR)) {
        builder.changeType(Optional.of(ChangeType.valueOf(proto.getChangeType())));
      }
      if (proto.hasField(PATCH_TYPE_DESCRIPTOR)) {
        builder.patchType(Optional.of(PatchType.valueOf(proto.getPatchType())));
      }
      return builder.build();
    }
  }
}
