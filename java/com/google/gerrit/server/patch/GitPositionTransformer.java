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

package com.google.gerrit.server.patch;

import static com.google.common.collect.Comparators.emptiesFirst;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Transformer of {@link Position}s in one Git tree to {@link Position}s in another Git tree given
 * the {@link Mapping}s between the trees.
 *
 * <p>The base idea is that a {@link Position} in the source tree can be translated/mapped to a
 * corresponding {@link Position} in the target tree when we know how the target tree changed
 * compared to the source tree. As long as {@link Position}s are only defined via file path and line
 * range, we only need to know which file path in the source tree corresponds to which file path in
 * the target tree and how the lines within that file changed from the source to the target tree.
 *
 * <p>The algorithm is roughly:
 *
 * <ol>
 *   <li>Go over all positions and replace the file path for each of them with the corresponding one
 *       in the target tree. If a file path maps to two file paths in the target tree (copied file),
 *       duplicate the position entry and use each of the new file paths with it. If a file path
 *       maps to no file in the target tree (deleted file), apply the specified conflict strategy
 *       (e.g. drop position completely or map to next best guess).
 *   <li>Per file path, go through the file from top to bottom and keep track of how the range
 *       mappings for that file shift the lines. Derive the shifted amount by comparing the number
 *       of lines between source and target in the range mapping. While going through the file,
 *       shift each encountered position by the currently tracked amount. If a position overlaps
 *       with the lines of a range mapping, apply the specified conflict strategy (e.g. drop
 *       position completely or map to next best guess).
 * </ol>
 */
public class GitPositionTransformer {
  private final PositionConflictStrategy positionConflictStrategy;

  /**
   * Creates a new {@code GitPositionTransformer} which uses the specified strategy for conflicts.
   */
  public GitPositionTransformer(PositionConflictStrategy positionConflictStrategy) {
    this.positionConflictStrategy = positionConflictStrategy;
  }

  /**
   * Transforms the {@link Position}s of the specified entities as indicated via the {@link
   * Mapping}s.
   *
   * <p>This is typically used to transform the {@link Position}s in one Git tree (source) to the
   * corresponding {@link Position}s in another Git tree (target). The {@link Mapping}s need to
   * indicate all relevant changes between the source and target tree. {@link Mapping}s for files
   * not referenced by the given {@link Position}s need not be specified. They can be included,
   * though, as they aren't harmful.
   *
   * @param entities the entities whose {@link Position} should be mapped to the target tree
   * @param mappings the mappings describing all relevant changes between the source and the target
   *     tree
   * @param <T> an entity which has a {@link Position}
   * @return a list of entities with transformed positions. There are no guarantees about the order
   *     of the returned elements.
   */
  public <T> ImmutableList<PositionedEntity<T>> transform(
      Collection<PositionedEntity<T>> entities, Set<Mapping> mappings) {
    // Update the file paths first as copied files might exist. For copied files, this operation
    // will duplicate the PositionedEntity instances of the original file.
    List<PositionedEntity<T>> filePathUpdatedEntities = updateFilePaths(entities, mappings);

    return shiftRanges(filePathUpdatedEntities, mappings);
  }

  private <T> ImmutableList<PositionedEntity<T>> updateFilePaths(
      Collection<PositionedEntity<T>> entities, Set<Mapping> mappings) {
    Map<String, ImmutableSet<String>> newFilesPerOldFile = groupNewFilesByOldFiles(mappings);
    return entities.stream()
        .flatMap(entity -> mapToNewFileIfChanged(newFilesPerOldFile, entity))
        .collect(toImmutableList());
  }

  private static Map<String, ImmutableSet<String>> groupNewFilesByOldFiles(Set<Mapping> mappings) {
    return mappings.stream()
        .map(Mapping::file)
        // Ignore file additions (irrelevant for mappings).
        .filter(mapping -> mapping.oldPath().isPresent())
        .collect(
            groupingBy(
                mapping -> mapping.oldPath().orElse(""),
                collectingAndThen(
                    Collectors.mapping(FileMapping::newPath, toImmutableSet()),
                    // File deletion (empty Optional) -> empty set.
                    GitPositionTransformer::unwrapOptionals)));
  }

  private static ImmutableSet<String> unwrapOptionals(ImmutableSet<Optional<String>> optionals) {
    return optionals.stream().flatMap(Streams::stream).collect(toImmutableSet());
  }

  private <T> Stream<PositionedEntity<T>> mapToNewFileIfChanged(
      Map<String, ? extends Set<String>> newFilesPerOldFile, PositionedEntity<T> entity) {
    if (!entity.position().filePath().isPresent()) {
      // No mapping of file paths necessary if no file path is set. -> Keep existing entry.
      return Stream.of(entity);
    }
    String oldFilePath = entity.position().filePath().get();
    if (!newFilesPerOldFile.containsKey(oldFilePath)) {
      // Unchanged files don't have a mapping. -> Keep existing entries.
      return Stream.of(entity);
    }
    Set<String> newFiles = newFilesPerOldFile.get(oldFilePath);
    if (newFiles.isEmpty()) {
      // File was deleted.
      return
          positionConflictStrategy.getOnFileConflict(entity.position()).map(entity::withPosition).stream();
    }
    return newFiles.stream().map(entity::withFilePath);
  }

  private <T> ImmutableList<PositionedEntity<T>> shiftRanges(
      List<PositionedEntity<T>> filePathUpdatedEntities, Set<Mapping> mappings) {
    Map<String, ImmutableSet<RangeMapping>> mappingsPerNewFilePath =
        groupRangeMappingsByNewFilePath(mappings);
    return Stream.concat(
            // Keep positions without a file.
            filePathUpdatedEntities.stream()
                .filter(entity -> !entity.position().filePath().isPresent()),
            // Shift ranges per file.
            groupByFilePath(filePathUpdatedEntities).entrySet().stream()
                .flatMap(
                    newFilePathAndEntities ->
                        shiftRangesInOneFileIfChanged(
                            mappingsPerNewFilePath,
                            newFilePathAndEntities.getKey(),
                            newFilePathAndEntities.getValue())
                            .stream()))
        .collect(toImmutableList());
  }

  private static Map<String, ImmutableSet<RangeMapping>> groupRangeMappingsByNewFilePath(
      Set<Mapping> mappings) {
    return mappings.stream()
        // Ignore range mappings of deleted files.
        .filter(mapping -> mapping.file().newPath().isPresent())
        .collect(
            groupingBy(
                mapping -> mapping.file().newPath().orElse(""),
                collectingAndThen(
                    Collectors.<Mapping, Set<RangeMapping>>reducing(
                        new HashSet<>(), Mapping::ranges, Sets::union),
                    ImmutableSet::copyOf)));
  }

  private static <T> Map<String, ImmutableList<PositionedEntity<T>>> groupByFilePath(
      List<PositionedEntity<T>> fileUpdatedEntities) {
    return fileUpdatedEntities.stream()
        .filter(entity -> entity.position().filePath().isPresent())
        .collect(groupingBy(entity -> entity.position().filePath().orElse(""), toImmutableList()));
  }

  private <T> ImmutableList<PositionedEntity<T>> shiftRangesInOneFileIfChanged(
      Map<String, ImmutableSet<RangeMapping>> mappingsPerNewFilePath,
      String newFilePath,
      ImmutableList<PositionedEntity<T>> sameFileEntities) {
    ImmutableSet<RangeMapping> sameFileRangeMappings =
        mappingsPerNewFilePath.getOrDefault(newFilePath, ImmutableSet.of());
    if (sameFileRangeMappings.isEmpty()) {
      // Unchanged files and pure renames/copies don't have range mappings. -> Keep existing
      // entries.
      return sameFileEntities;
    }
    return shiftRangesInOneFile(sameFileEntities, sameFileRangeMappings);
  }

  private <T> ImmutableList<PositionedEntity<T>> shiftRangesInOneFile(
      List<PositionedEntity<T>> sameFileEntities, Set<RangeMapping> sameFileRangeMappings) {
    ImmutableList<PositionedEntity<T>> sortedEntities = sortByStartEnd(sameFileEntities);
    ImmutableList<RangeMapping> sortedMappings = sortByOldStartEnd(sameFileRangeMappings);

    int shiftedAmount = 0;
    int mappingIndex = 0;
    int entityIndex = 0;
    ImmutableList.Builder<PositionedEntity<T>> resultingEntities =
        ImmutableList.builderWithExpectedSize(sortedEntities.size());
    while (entityIndex < sortedEntities.size() && mappingIndex < sortedMappings.size()) {
      PositionedEntity<T> entity = sortedEntities.get(entityIndex);
      if (entity.position().lineRange().isPresent()) {
        Range range = entity.position().lineRange().get();
        RangeMapping mapping = sortedMappings.get(mappingIndex);
        if (mapping.oldLineRange().end() <= range.start()) {
          shiftedAmount = mapping.newLineRange().end() - mapping.oldLineRange().end();
          mappingIndex++;
        } else if (range.end() <= mapping.oldLineRange().start()) {
          resultingEntities.add(entity.shiftPositionBy(shiftedAmount));
          entityIndex++;
        } else {
          positionConflictStrategy
              .getOnRangeConflict(entity.position())
              .map(entity::withPosition)
              .ifPresent(resultingEntities::add);
          entityIndex++;
        }
      } else {
        // No range -> no need to shift.
        resultingEntities.add(entity);
        entityIndex++;
      }
    }
    for (int i = entityIndex; i < sortedEntities.size(); i++) {
      resultingEntities.add(sortedEntities.get(i).shiftPositionBy(shiftedAmount));
    }
    return resultingEntities.build();
  }

  private static <T> ImmutableList<PositionedEntity<T>> sortByStartEnd(
      List<PositionedEntity<T>> entities) {
    return entities.stream()
        .sorted(
            comparing(
                entity -> entity.position().lineRange(),
                emptiesFirst(comparing(Range::start).thenComparing(Range::end))))
        .collect(toImmutableList());
  }

  private static ImmutableList<RangeMapping> sortByOldStartEnd(Set<RangeMapping> mappings) {
    return mappings.stream()
        .sorted(
            comparing(
                RangeMapping::oldLineRange, comparing(Range::start).thenComparing(Range::end)))
        .collect(toImmutableList());
  }

  /**
   * A mapping from a {@link Position} in one Git commit/tree (source) to a {@link Position} in
   * another Git commit/tree (target).
   */
  @AutoValue
  public abstract static class Mapping {

    /** A mapping describing how the attributes of one file are mapped from source to target. */
    public abstract FileMapping file();

    /**
     * Mappings describing how line ranges within the file indicated by {@link #file()} are mapped
     * from source to target.
     */
    public abstract ImmutableSet<RangeMapping> ranges();

    public static Mapping create(FileMapping fileMapping, Iterable<RangeMapping> rangeMappings) {
      return new AutoValue_GitPositionTransformer_Mapping(
          fileMapping, ImmutableSet.copyOf(rangeMappings));
    }
  }

  /**
   * A mapping of attributes from a file in one Git tree (source) to a file in another Git tree
   * (target).
   *
   * <p>At the moment, only the file path is considered. Other attributes like file mode would be
   * imaginable too but are currently not supported.
   */
  @AutoValue
  public abstract static class FileMapping {

    /** File path in the source tree. For file additions, this is an empty {@link Optional}. */
    public abstract Optional<String> oldPath();

    /**
     * File path in the target tree. Can be the same as {@link #oldPath()} if unchanged. For file
     * deletions, this is an empty {@link Optional}.
     */
    public abstract Optional<String> newPath();

    /**
     * Creates a {@link FileMapping} for a file addition.
     *
     * <p>In the context of {@link GitPositionTransformer}, file additions are irrelevant as no
     * given position in the source tree can refer to such a new file in the target tree. We still
     * provide this factory method so that code outside of {@link GitPositionTransformer} doesn't
     * have to care about such details and can simply create {@link FileMapping}s for any
     * modifications between the trees.
     */
    public static FileMapping forAddedFile(String filePath) {
      return new AutoValue_GitPositionTransformer_FileMapping(
          Optional.empty(), Optional.of(filePath));
    }

    /** Creates a {@link FileMapping} for a file deletion. */
    public static FileMapping forDeletedFile(String filePath) {
      return new AutoValue_GitPositionTransformer_FileMapping(
          Optional.of(filePath), Optional.empty());
    }

    /** Creates a {@link FileMapping} for a file modification. */
    public static FileMapping forModifiedFile(String filePath) {
      return new AutoValue_GitPositionTransformer_FileMapping(
          Optional.of(filePath), Optional.of(filePath));
    }

    /** Creates a {@link FileMapping} for a file renaming. */
    public static FileMapping forRenamedFile(String oldPath, String newPath) {
      return new AutoValue_GitPositionTransformer_FileMapping(
          Optional.of(oldPath), Optional.of(newPath));
    }

    /** Creates a {@link FileMapping} using the old and new paths. */
    public static FileMapping forFile(Optional<String> oldPath, Optional<String> newPath) {
      return new AutoValue_GitPositionTransformer_FileMapping(oldPath, newPath);
    }
  }

  /**
   * A mapping of a line range in one Git tree (source) to the corresponding line range in another
   * Git tree (target).
   */
  @AutoValue
  public abstract static class RangeMapping {

    /** Range in the source tree. */
    public abstract Range oldLineRange();

    /** Range in the target tree. */
    public abstract Range newLineRange();

    /**
     * Creates a new {@code RangeMapping}.
     *
     * @param oldRange see {@link #oldLineRange()}
     * @param newRange see {@link #newLineRange()}
     */
    public static RangeMapping create(Range oldRange, Range newRange) {
      return new AutoValue_GitPositionTransformer_RangeMapping(oldRange, newRange);
    }
  }

  /**
   * A position within the tree of a Git commit.
   *
   * <p>The term 'position' is our own invention. The underlying idea is that a Gerrit comment is at
   * a specific position within the commit of a patchset. That position is defined by the attributes
   * defined in this class.
   *
   * <p>The same thinking can be applied to diff hunks (= JGit edits). Each diff hunk maps a
   * position in one commit (e.g. in the parent of the patchset) to a position in another commit
   * (e.g. in the commit of the patchset).
   *
   * <p>We only refer to lines and not character offsets within the lines here as Git only works
   * with line precision. In theory, we could do better in Gerrit as we also have intraline diffs.
   * Incorporating those requires careful considerations, though.
   */
  @AutoValue
  public abstract static class Position {

    /** Absolute file path. */
    public abstract Optional<String> filePath();

    /**
     * Affected lines. An empty {@link Optional} indicates that this position does not refer to any
     * specific lines (e.g. used for a file comment).
     */
    public abstract Optional<Range> lineRange();

    /**
     * Creates a copy of this {@code Position} whose range is shifted by the indicated amount.
     *
     * <p><strong>Note:</strong> There's no guarantee that this method returns a new instance.
     *
     * @param amount number of lines to shift. Negative values mean moving the range up, positive
     *     values mean moving the range down.
     * @return a {@code Position} instance with the updated range
     */
    public Position shiftBy(int amount) {
      return lineRange()
          .map(range -> toBuilder().lineRange(range.shiftBy(amount)).build())
          .orElse(this);
    }

    /**
     * Creates a copy of this {@code Position} which doesn't refer to any specific lines.
     *
     * <p><strong>Note:</strong> There's no guarantee that this method returns a new instance.
     *
     * @return a {@code Position} instance without a line range
     */
    public Position withoutLineRange() {
      return toBuilder().lineRange(Optional.empty()).build();
    }

    /**
     * Creates a copy of this {@code Position} whose file path is adjusted to the indicated value.
     *
     * <p><strong>Note:</strong> There's no guarantee that this method returns a new instance.
     *
     * @param filePath the new file path to use
     * @return a {@code Position} instance with the indicated file path
     */
    public Position withFilePath(String filePath) {
      return toBuilder().filePath(filePath).build();
    }

    abstract Builder toBuilder();

    public static Builder builder() {
      return new AutoValue_GitPositionTransformer_Position.Builder();
    }

    /** Builder of a {@link Position}. */
    @AutoValue.Builder
    public abstract static class Builder {

      /** See {@link #filePath()}. */
      public abstract Builder filePath(String filePath);

      /** See {@link #lineRange()}. */
      public abstract Builder lineRange(Range lineRange);

      /** See {@link #lineRange()}. */
      public abstract Builder lineRange(Optional<Range> lineRange);

      public abstract Position build();
    }
  }

  /** A range. In the context of {@link GitPositionTransformer}, this is a line range. */
  @AutoValue
  public abstract static class Range {

    /** Start of the range. (inclusive) */
    public abstract int start();

    /** End of the range. (exclusive) */
    public abstract int end();

    /**
     * Creates a copy of this {@code Range} which is shifted by the indicated amount. A shift
     * equally applies to both {@link #start()} end {@link #end()}.
     *
     * <p><strong>Note:</strong> There's no guarantee that this method returns a new instance.
     *
     * @param amount amount to shift. Negative values mean moving the range up, positive values mean
     *     moving the range down.
     * @return a {@code Range} instance with updated start/end
     */
    public Range shiftBy(int amount) {
      return create(start() + amount, end() + amount);
    }

    public static Range create(int start, int end) {
      return new AutoValue_GitPositionTransformer_Range(start, end);
    }
  }

  /**
   * Wrapper around an instance of {@code T} which annotates it with a {@link Position}. Methods
   * such as {@link #shiftPositionBy(int)} and {@link #withFilePath(String)} allow to update the
   * associated {@link Position}. Afterwards, use {@link #getEntityAtUpdatedPosition()} to get an
   * updated version of the {@code T} instance.
   *
   * @param <T> an object/entity type which has a {@link Position}
   */
  public static class PositionedEntity<T> {

    private final T entity;
    private final Position position;
    private final BiFunction<T, Position, T> updatedEntityCreator;

    /**
     * Creates a new {@code PositionedEntity}.
     *
     * @param entity an instance which should be annotated with a {@link Position}
     * @param positionExtractor a function describing how a {@link Position} can be derived from the
     *     given entity
     * @param updatedEntityCreator a function to create a new entity of type {@code T} from an
     *     existing entity and a given {@link Position}. This must return a new instance of type
     *     {@code T}! The existing instance must not be modified!
     * @param <T> an object/entity type which has a {@link Position}
     */
    public static <T> PositionedEntity<T> create(
        T entity,
        Function<T, Position> positionExtractor,
        BiFunction<T, Position, T> updatedEntityCreator) {
      Position position = positionExtractor.apply(entity);
      return new PositionedEntity<>(entity, position, updatedEntityCreator);
    }

    private PositionedEntity(
        T entity, Position position, BiFunction<T, Position, T> updatedEntityCreator) {
      this.entity = entity;
      this.position = position;
      this.updatedEntityCreator = updatedEntityCreator;
    }

    /**
     * Returns the original underlying entity.
     *
     * @return the original instance of {@code T}
     */
    public T getEntity() {
      return entity;
    }

    /**
     * Returns an updated version of the entity to which the internally stored {@link Position} was
     * written back to.
     *
     * @return an updated instance of {@code T}
     */
    public T getEntityAtUpdatedPosition() {
      return updatedEntityCreator.apply(entity, position);
    }

    Position position() {
      return position;
    }

    /**
     * Shifts the tracked {@link Position} by the specified amount.
     *
     * @param amount number of lines to shift. Negative values mean moving the range up, positive
     *     values mean moving the range down.
     * @return a {@code PositionedEntity} with updated {@link Position}
     */
    public PositionedEntity<T> shiftPositionBy(int amount) {
      return new PositionedEntity<>(entity, position.shiftBy(amount), updatedEntityCreator);
    }

    /**
     * Updates the file path of the tracked {@link Position}.
     *
     * @param filePath the new file path to use
     * @return a {@code PositionedEntity} with updated {@link Position}
     */
    public PositionedEntity<T> withFilePath(String filePath) {
      return new PositionedEntity<>(entity, position.withFilePath(filePath), updatedEntityCreator);
    }

    /**
     * Updates the tracked {@link Position}.
     *
     * @return a {@code PositionedEntity} with updated {@link Position}
     */
    public PositionedEntity<T> withPosition(Position newPosition) {
      return new PositionedEntity<>(entity, newPosition, updatedEntityCreator);
    }
  }

  /**
   * Strategy indicating how to handle {@link Position}s for which mapping conflicts exist. A
   * mapping conflict means that a {@link Position} can't be transformed such that it still refers
   * to exactly the same commit content afterwards.
   *
   * <p>Example: A {@link Position} refers to file foo.txt and lines 5-6 which contain the text
   * "Line 5\nLine 6". One of the {@link Mapping}s given to {@link #transform(Collection, Set)}
   * indicates that line 5 of foo.txt was modified to "Line five\nLine 5.1\n". We could derive a
   * transformed {@link Position} (foo.txt, lines 5-7) but that {@link Position} would then refer to
   * the content "Line five\nLine 5.1\nLine 6". If the modification started already in line 4, we
   * could even only guess what the transformed {@link Position} would be.
   */
  public interface PositionConflictStrategy {
    /**
     * Determines an alternate {@link Position} when the range of the position can't be mapped
     * without a conflict.
     *
     * @param oldPosition position in the source tree
     * @return the new {@link Position} or an empty {@link Optional} if the position should be
     *     dropped
     */
    Optional<Position> getOnRangeConflict(Position oldPosition);

    /**
     * Determines an alternate {@link Position} when there is no file for the position (= file
     * deletion) in the target tree.
     *
     * @param oldPosition position in the source tree
     * @return the new {@link Position} or an empty {@link Optional} if the position should be *
     *     dropped
     */
    Optional<Position> getOnFileConflict(Position oldPosition);
  }

  /**
   * A strategy which drops any {@link Position}s on a conflicting mapping. Such a strategy is
   * useful if it's important that any mapped {@link Position} still refers to exactly the same
   * commit content as before. See more details at {@link PositionConflictStrategy}.
   *
   * <p>We need this strategy for computing edits due to rebase.
   */
  public enum OmitPositionOnConflict implements PositionConflictStrategy {
    INSTANCE;

    @Override
    public Optional<Position> getOnRangeConflict(Position oldPosition) {
      return Optional.empty();
    }

    @Override
    public Optional<Position> getOnFileConflict(Position oldPosition) {
      return Optional.empty();
    }
  }

  /**
   * A strategy which tries to select the next suitable {@link Position} on a conflicting mapping.
   * At the moment, this strategy is very basic and only defers to the next higher level (e.g. range
   * unclear -> drop range but keep file reference). This could be improved in the future.
   *
   * <p>We need this strategy for ported comments.
   *
   * <p><strong>Warning:</strong> With this strategy, mapped {@link Position}s are not guaranteed to
   * refer to exactly the same commit content as before. See more details at {@link
   * PositionConflictStrategy}.
   *
   * <p>Contract: This strategy will never drop any {@link Position}.
   */
  public enum BestPositionOnConflict implements PositionConflictStrategy {
    INSTANCE;

    @Override
    public Optional<Position> getOnRangeConflict(Position oldPosition) {
      return Optional.of(oldPosition.withoutLineRange());
    }

    @Override
    public Optional<Position> getOnFileConflict(Position oldPosition) {
      // If there isn't a target file, we can also drop any ranges.
      return Optional.of(Position.builder().build());
    }
  }
}
