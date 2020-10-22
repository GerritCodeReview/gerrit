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
//
//
//

package com.google.gerrit.server.patch.filediff;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.collect.Multimaps.toMultimap;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.server.patch.DiffMappings;
import com.google.gerrit.server.patch.GitPositionTransformer;
import com.google.gerrit.server.patch.GitPositionTransformer.Mapping;
import com.google.gerrit.server.patch.GitPositionTransformer.OmitPositionOnConflict;
import com.google.gerrit.server.patch.GitPositionTransformer.Position;
import com.google.gerrit.server.patch.GitPositionTransformer.PositionedEntity;
import com.google.gerrit.server.patch.GitPositionTransformer.Range;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Transformer of edits regarding their base trees. An edit describes a difference between {@code
 * treeA} and {@code treeB}. This class allows to describe the edit as a difference between {@code
 * treeA'} and {@code treeB'} given the transformation of {@code treeA} to {@code treeA'} and {@code
 * treeB} to {@code treeB'}. Edits which can't be transformed due to conflicts with the
 * transformation are omitted.
 */
class EditTransformer {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitPositionTransformer positionTransformer =
      new GitPositionTransformer(OmitPositionOnConflict.INSTANCE);
  private List<ContextAwareEdit> edits;

  /**
   * Creates a new {@code EditTransformer} for the edits contained in the specified {@code
   * FileEdits}s.
   *
   * @param fileEdits a list of {@code FileEdits}s containing the edits
   */
  public EditTransformer(ImmutableList<FileEdits> fileEdits) {
    // TODO(ghareeb): Can we replace FileEdits with another entity from the new refactored
    // diff cache implementation? e.g. one of the GitFileDiffCache entities
    edits = fileEdits.stream().flatMap(EditTransformer::toEdits).collect(toImmutableList());
  }

  /**
   * Transforms the references of side A of the edits. If the edits describe differences between
   * {@code treeA} and {@code treeB} and the specified {@code FileEdits}s define a transformation
   * from {@code treeA} to {@code treeA'}, the resulting edits will be defined as differences
   * between {@code treeA'} and {@code treeB}. Edits which can't be transformed due to conflicts
   * with the transformation are omitted.
   *
   * @param transformingEntries a list of {@code FileEdits}s defining the transformation of {@code
   *     treeA} to {@code treeA'}
   */
  public void transformReferencesOfSideA(ImmutableList<FileEdits> transformingEntries) {
    transformEdits(transformingEntries, SideAStrategy.INSTANCE);
  }

  /**
   * Transforms the references of side B of the edits. If the edits describe differences between
   * {@code treeA} and {@code treeB} and the specified {@code FileEdits}s define a transformation
   * from {@code treeB} to {@code treeB'}, the resulting edits will be defined as differences
   * between {@code treeA} and {@code treeB'}. Edits which can't be transformed due to conflicts
   * with the transformation are omitted.
   *
   * @param transformingEntries a list of {@code PatchListEntry}s defining the transformation of
   *     {@code treeB} to {@code treeB'}
   */
  public void transformReferencesOfSideB(ImmutableList<FileEdits> transformingEntries) {
    transformEdits(transformingEntries, SideBStrategy.INSTANCE);
  }

  /**
   * Returns the transformed edits per file path they modify in {@code treeB'}.
   *
   * @return the transformed edits per file path
   */
  public Multimap<String, ContextAwareEdit> getEditsPerFilePath() {
    return edits.stream()
        .collect(
            toMultimap(
                c -> {
                  String path =
                      c.getNewFilePath().isPresent()
                          ? c.getNewFilePath().get()
                          : c.getOldFilePath().get();
                  return path;
                },
                Function.identity(),
                ArrayListMultimap::create));
  }

  public static Stream<ContextAwareEdit> toEdits(FileEdits in) {
    List<Edit> edits = in.edits();
    if (edits.isEmpty()) {
      return Stream.of(ContextAwareEdit.createForNoContentEdit(in.oldPath(), in.newPath()));
    }

    return edits.stream().map(edit -> ContextAwareEdit.create(in.oldPath(), in.newPath(), edit));
  }

  private void transformEdits(List<FileEdits> inputs, SideStrategy sideStrategy) {
    ImmutableList<PositionedEntity<ContextAwareEdit>> positionedEdits =
        edits.stream()
            .map(edit -> toPositionedEntity(edit, sideStrategy))
            .collect(toImmutableList());
    ImmutableSet<Mapping> mappings =
        inputs.stream().map(DiffMappings::toMapping).collect(toImmutableSet());

    edits =
        positionTransformer.transform(positionedEdits, mappings).stream()
            .map(PositionedEntity::getEntityAtUpdatedPosition)
            .collect(toImmutableList());
  }

  private static PositionedEntity<ContextAwareEdit> toPositionedEntity(
      ContextAwareEdit edit, SideStrategy sideStrategy) {
    return PositionedEntity.create(
        edit, sideStrategy::extractPosition, sideStrategy::createEditAtNewPosition);
  }

  @AutoValue
  abstract static class ContextAwareEdit {
    static ContextAwareEdit create(Optional<String> oldPath, Optional<String> newPath, Edit edit) {
      // TODO(ghareeb): Look if the new FileEdits class is capable of representing renames/copies
      // and in this case we can get rid of the ContextAwareEdit class.
      return create(
          oldPath, newPath, edit.beginA(), edit.endA(), edit.beginB(), edit.endB(), false);
    }

    static ContextAwareEdit createForNoContentEdit(
        Optional<String> oldPath, Optional<String> newPath) {
      // Remove the warning in createEditAtNewPosition() if we switch to an empty range instead of
      // (-1:-1, -1:-1) in the future.
      return create(oldPath, newPath, -1, -1, -1, -1, false);
    }

    static ContextAwareEdit create(
        Optional<String> oldFilePath,
        Optional<String> newFilePath,
        int beginA,
        int endA,
        int beginB,
        int endB,
        boolean filePathAdjusted) {
      boolean implicitRename =
          newFilePath.isPresent()
              && oldFilePath.isPresent()
              && !Objects.equals(oldFilePath.get(), newFilePath.get())
              && filePathAdjusted;
      return new AutoValue_EditTransformer_ContextAwareEdit(
          oldFilePath, newFilePath, beginA, endA, beginB, endB, implicitRename);
    }

    public abstract Optional<String> getOldFilePath();

    public abstract Optional<String> getNewFilePath();

    public abstract int getBeginA();

    public abstract int getEndA();

    public abstract int getBeginB();

    public abstract int getEndB();

    // Used for equals(), for which this value is important.
    public abstract boolean isImplicitRename();

    public Optional<org.eclipse.jgit.diff.Edit> toEdit() {
      if (getBeginA() < 0) {
        return Optional.empty();
      }

      return Optional.of(
          new org.eclipse.jgit.diff.Edit(getBeginA(), getEndA(), getBeginB(), getEndB()));
    }
  }

  private interface SideStrategy {
    Position extractPosition(ContextAwareEdit edit);

    ContextAwareEdit createEditAtNewPosition(ContextAwareEdit edit, Position newPosition);
  }

  private enum SideAStrategy implements SideStrategy {
    INSTANCE;

    @Override
    public Position extractPosition(ContextAwareEdit edit) {
      String filePath =
          edit.getOldFilePath().isPresent()
              ? edit.getOldFilePath().get()
              : edit.getNewFilePath().get();
      return Position.builder()
          .filePath(filePath)
          .lineRange(Range.create(edit.getBeginA(), edit.getEndA()))
          .build();
    }

    @Override
    public ContextAwareEdit createEditAtNewPosition(ContextAwareEdit edit, Position newPosition) {
      // Use an empty range at Gerrit "file level" if no target range is available. Such an empty
      // range should not occur right now but this should be a safe fallback if something changes
      // in the future.
      Range updatedRange = newPosition.lineRange().orElseGet(() -> Range.create(-1, -1));
      if (!newPosition.lineRange().isPresent()) {
        logger.atWarning().log(
            "Position %s has an empty range which is unexpected for the edits-due-to-rebase"
                + " computation. This is likely a regression!",
            newPosition);
      }
      // Same as for the range above. PATCHSET_LEVEL is a safe fallback.
      String updatedFilePath = newPosition.filePath().orElse(Patch.PATCHSET_LEVEL);
      if (!newPosition.filePath().isPresent()) {
        logger.atWarning().log(
            "Position %s has an empty file path which is unexpected for the edits-due-to-rebase"
                + " computation. This is likely a regression!",
            newPosition);
      }
      return ContextAwareEdit.create(
          Optional.of(updatedFilePath),
          edit.getNewFilePath(),
          updatedRange.start(),
          updatedRange.end(),
          edit.getBeginB(),
          edit.getEndB(),
          !Objects.equals(edit.getOldFilePath(), Optional.of(updatedFilePath)));
    }
  }

  private enum SideBStrategy implements SideStrategy {
    INSTANCE;

    @Override
    public Position extractPosition(ContextAwareEdit edit) {
      String filePath =
          edit.getNewFilePath().isPresent()
              ? edit.getNewFilePath().get()
              : edit.getOldFilePath().get();
      return Position.builder()
          .filePath(filePath)
          .lineRange(Range.create(edit.getBeginB(), edit.getEndB()))
          .build();
    }

    @Override
    public ContextAwareEdit createEditAtNewPosition(ContextAwareEdit edit, Position newPosition) {
      // Use an empty range at Gerrit "file level" if no target range is available. Such an empty
      // range should not occur right now but this should be a safe fallback if something changes
      // in the future.
      Range updatedRange = newPosition.lineRange().orElseGet(() -> Range.create(-1, -1));
      // Same as far the range above. PATCHSET_LEVEL is a safe fallback.
      Optional<String> updatedFilePath =
          Optional.of(newPosition.filePath().orElse(Patch.PATCHSET_LEVEL));
      return ContextAwareEdit.create(
          edit.getOldFilePath(),
          updatedFilePath,
          edit.getBeginA(),
          edit.getEndA(),
          updatedRange.start(),
          updatedRange.end(),
          !Objects.equals(edit.getNewFilePath(), updatedFilePath));
    }
  }
}
