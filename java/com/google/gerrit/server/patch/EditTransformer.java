// Copyright (C) 2017 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.Multimaps.toMultimap;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.eclipse.jgit.diff.Edit;

/**
 * Transformer of edits regarding their base trees. An edit describes a difference between {@code
 * treeA} and {@code treeB}. This class allows to describe the edit as a difference between {@code
 * treeA'} and {@code treeB'} given the transformation of {@code treeA} to {@code treeA'} and {@code
 * treeB} to {@code treeB'}. Edits which can't be transformed due to conflicts with the
 * transformation are omitted.
 */
class EditTransformer {

  private List<ContextAwareEdit> edits;

  /**
   * Creates a new {@code EditTransformer} for the edits contained in the specified {@code
   * PatchListEntry}s.
   *
   * @param patchListEntries a list of {@code PatchListEntry}s containing the edits
   */
  public EditTransformer(List<PatchListEntry> patchListEntries) {
    edits = patchListEntries.stream().flatMap(EditTransformer::toEdits).collect(toImmutableList());
  }

  /**
   * Transforms the references of side A of the edits. If the edits describe differences between
   * {@code treeA} and {@code treeB} and the specified {@code PatchListEntry}s define a
   * transformation from {@code treeA} to {@code treeA'}, the resulting edits will be defined as
   * differences between {@code treeA'} and {@code treeB}. Edits which can't be transformed due to
   * conflicts with the transformation are omitted.
   *
   * @param transformationEntries a list of {@code PatchListEntry}s defining the transformation of
   *     {@code treeA} to {@code treeA'}
   */
  public void transformReferencesOfSideA(List<PatchListEntry> transformationEntries) {
    transformEdits(transformationEntries, SideAStrategy.INSTANCE);
  }

  /**
   * Transforms the references of side B of the edits. If the edits describe differences between
   * {@code treeA} and {@code treeB} and the specified {@code PatchListEntry}s define a
   * transformation from {@code treeB} to {@code treeB'}, the resulting edits will be defined as
   * differences between {@code treeA} and {@code treeB'}. Edits which can't be transformed due to
   * conflicts with the transformation are omitted.
   *
   * @param transformationEntries a list of {@code PatchListEntry}s defining the transformation of
   *     {@code treeB} to {@code treeB'}
   */
  public void transformReferencesOfSideB(List<PatchListEntry> transformationEntries) {
    transformEdits(transformationEntries, SideBStrategy.INSTANCE);
  }

  /**
   * Returns the transformed edits per file path they modify in {@code treeB'}.
   *
   * @return the transformed edits per file path
   */
  public Multimap<String, ContextAwareEdit> getEditsPerFilePath() {
    return edits
        .stream()
        .collect(
            toMultimap(
                ContextAwareEdit::getNewFilePath, Function.identity(), ArrayListMultimap::create));
  }

  public static Stream<ContextAwareEdit> toEdits(PatchListEntry patchListEntry) {
    ImmutableList<Edit> edits = patchListEntry.getEdits();
    if (edits.isEmpty()) {
      return Stream.of(ContextAwareEdit.createForNoContentEdit(patchListEntry));
    }

    return edits.stream().map(edit -> ContextAwareEdit.create(patchListEntry, edit));
  }

  private void transformEdits(List<PatchListEntry> transformingEntries, SideStrategy sideStrategy) {
    Map<String, List<ContextAwareEdit>> editsPerFilePath =
        edits.stream().collect(groupingBy(sideStrategy::getFilePath));
    Map<String, List<PatchListEntry>> transEntriesPerPath =
        transformingEntries.stream().collect(groupingBy(EditTransformer::getOldFilePath));

    edits =
        editsPerFilePath
            .entrySet()
            .stream()
            .flatMap(
                pathAndEdits -> {
                  List<PatchListEntry> transEntries =
                      transEntriesPerPath.getOrDefault(pathAndEdits.getKey(), ImmutableList.of());
                  return transformEdits(sideStrategy, pathAndEdits.getValue(), transEntries);
                })
            .collect(toList());
  }

  private static String getOldFilePath(PatchListEntry patchListEntry) {
    return MoreObjects.firstNonNull(patchListEntry.getOldName(), patchListEntry.getNewName());
  }

  private static Stream<ContextAwareEdit> transformEdits(
      SideStrategy sideStrategy,
      List<ContextAwareEdit> originalEdits,
      List<PatchListEntry> transformingEntries) {
    if (transformingEntries.isEmpty()) {
      return originalEdits.stream();
    }

    // TODO(aliceks): Find a way to prevent an explosion of the number of entries.
    return transformingEntries
        .stream()
        .flatMap(
            transEntry ->
                transformEdits(
                        sideStrategy, originalEdits, transEntry.getEdits(), transEntry.getNewName())
                    .stream());
  }

  private static List<ContextAwareEdit> transformEdits(
      SideStrategy sideStrategy,
      List<ContextAwareEdit> unorderedOriginalEdits,
      List<Edit> unorderedTransformingEdits,
      String adjustedFilePath) {
    List<ContextAwareEdit> originalEdits = new ArrayList<>(unorderedOriginalEdits);
    originalEdits.sort(comparing(sideStrategy::getBegin).thenComparing(sideStrategy::getEnd));
    List<Edit> transformingEdits = new ArrayList<>(unorderedTransformingEdits);
    transformingEdits.sort(comparing(Edit::getBeginA).thenComparing(Edit::getEndA));

    int shiftedAmount = 0;
    int transIndex = 0;
    int origIndex = 0;
    List<ContextAwareEdit> resultingEdits = new ArrayList<>(originalEdits.size());
    while (origIndex < originalEdits.size() && transIndex < transformingEdits.size()) {
      ContextAwareEdit originalEdit = originalEdits.get(origIndex);
      Edit transformingEdit = transformingEdits.get(transIndex);
      if (transformingEdit.getEndA() <= sideStrategy.getBegin(originalEdit)) {
        shiftedAmount = transformingEdit.getEndB() - transformingEdit.getEndA();
        transIndex++;
      } else if (sideStrategy.getEnd(originalEdit) <= transformingEdit.getBeginA()) {
        resultingEdits.add(sideStrategy.create(originalEdit, shiftedAmount, adjustedFilePath));
        origIndex++;
      } else {
        // Overlapping -> ignore.
        origIndex++;
      }
    }
    for (int i = origIndex; i < originalEdits.size(); i++) {
      resultingEdits.add(
          sideStrategy.create(originalEdits.get(i), shiftedAmount, adjustedFilePath));
    }
    return resultingEdits;
  }

  @AutoValue
  abstract static class ContextAwareEdit {
    static ContextAwareEdit create(PatchListEntry patchListEntry, Edit edit) {
      return create(
          patchListEntry.getOldName(),
          patchListEntry.getNewName(),
          edit.getBeginA(),
          edit.getEndA(),
          edit.getBeginB(),
          edit.getEndB(),
          false);
    }

    static ContextAwareEdit createForNoContentEdit(PatchListEntry patchListEntry) {
      return create(
          patchListEntry.getOldName(), patchListEntry.getNewName(), -1, -1, -1, -1, false);
    }

    static ContextAwareEdit create(
        String oldFilePath,
        String newFilePath,
        int beginA,
        int endA,
        int beginB,
        int endB,
        boolean filePathAdjusted) {
      String adjustedOldFilePath = MoreObjects.firstNonNull(oldFilePath, newFilePath);
      boolean implicitRename = !Objects.equals(oldFilePath, newFilePath) && filePathAdjusted;
      return new AutoValue_EditTransformer_ContextAwareEdit(
          adjustedOldFilePath, newFilePath, beginA, endA, beginB, endB, implicitRename);
    }

    public abstract String getOldFilePath();

    public abstract String getNewFilePath();

    public abstract int getBeginA();

    public abstract int getEndA();

    public abstract int getBeginB();

    public abstract int getEndB();

    // Used for equals(), for which this value is important.
    public abstract boolean isImplicitRename();

    public Optional<Edit> toEdit() {
      if (getBeginA() < 0) {
        return Optional.empty();
      }

      return Optional.of(new Edit(getBeginA(), getEndA(), getBeginB(), getEndB()));
    }
  }

  private interface SideStrategy {
    String getFilePath(ContextAwareEdit edit);

    int getBegin(ContextAwareEdit edit);

    int getEnd(ContextAwareEdit edit);

    ContextAwareEdit create(ContextAwareEdit edit, int shiftedAmount, String adjustedFilePath);
  }

  private enum SideAStrategy implements SideStrategy {
    INSTANCE;

    @Override
    public String getFilePath(ContextAwareEdit edit) {
      return edit.getOldFilePath();
    }

    @Override
    public int getBegin(ContextAwareEdit edit) {
      return edit.getBeginA();
    }

    @Override
    public int getEnd(ContextAwareEdit edit) {
      return edit.getEndA();
    }

    @Override
    public ContextAwareEdit create(
        ContextAwareEdit edit, int shiftedAmount, String adjustedFilePath) {
      return ContextAwareEdit.create(
          adjustedFilePath,
          edit.getNewFilePath(),
          edit.getBeginA() + shiftedAmount,
          edit.getEndA() + shiftedAmount,
          edit.getBeginB(),
          edit.getEndB(),
          !Objects.equals(edit.getOldFilePath(), adjustedFilePath));
    }
  }

  private enum SideBStrategy implements SideStrategy {
    INSTANCE;

    @Override
    public String getFilePath(ContextAwareEdit edit) {
      return edit.getNewFilePath();
    }

    @Override
    public int getBegin(ContextAwareEdit edit) {
      return edit.getBeginB();
    }

    @Override
    public int getEnd(ContextAwareEdit edit) {
      return edit.getEndB();
    }

    @Override
    public ContextAwareEdit create(
        ContextAwareEdit edit, int shiftedAmount, String adjustedFilePath) {
      return ContextAwareEdit.create(
          edit.getOldFilePath(),
          adjustedFilePath,
          edit.getBeginA(),
          edit.getEndA(),
          edit.getBeginB() + shiftedAmount,
          edit.getEndB() + shiftedAmount,
          !Objects.equals(edit.getNewFilePath(), adjustedFilePath));
    }
  }
}
