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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Transformer of {@link Position}s in one Git tree to {@link Position}s in another Git tree given
 * the {@link Mapping}s between the trees.
 */
public class GitPositionTransformer {

  // This is currently only a utility class but it won't stay like that.
  private GitPositionTransformer() {}

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
   * @return a list of entities with transformed positions
   */
  public static <T> ImmutableList<PositionedEntity<T>> transform(
      Collection<PositionedEntity<T>> entities, Set<Mapping> mappings) {
    // Update the file paths first as copied files might exist. For copied files, this operation
    // will duplicate the PositionedEntity instances of the original file.
    List<PositionedEntity<T>> filePathUpdatedEntities = updateFilePaths(entities, mappings);

    return shiftRanges(filePathUpdatedEntities, mappings);
  }

  private static <T> ImmutableList<PositionedEntity<T>> updateFilePaths(
      Collection<PositionedEntity<T>> entities, Set<Mapping> mappings) {
    Map<String, ImmutableSet<String>> newFilesPerOldFile = getNewFilesPerOldFiles(mappings);
    return entities.stream()
        .flatMap(entity -> mapToNewFileIfChanged(newFilesPerOldFile, entity))
        .collect(toImmutableList());
  }

  private static Map<String, ImmutableSet<String>> getNewFilesPerOldFiles(Set<Mapping> mappings) {
    return mappings.stream()
        .map(Mapping::file)
        .collect(
            groupingBy(
                FileMapping::oldPath, Collectors.mapping(FileMapping::newPath, toImmutableSet())));
  }

  private static <T> Stream<PositionedEntity<T>> mapToNewFileIfChanged(
      Map<String, ? extends Set<String>> newFilesPerOldFile, PositionedEntity<T> entity) {
    String oldFilePath = entity.position().filePath();
    if (!newFilesPerOldFile.containsKey(oldFilePath)) {
      // Unchanged files don't have a mapping. -> Keep existing entries.
      return Stream.of(entity);
    }
    Set<String> newFiles = newFilesPerOldFile.get(oldFilePath);
    return newFiles.stream().map(entity::withFilePath);
  }

  private static <T> ImmutableList<PositionedEntity<T>> shiftRanges(
      List<PositionedEntity<T>> filePathUpdatedEntities, Set<Mapping> mappings) {
    Map<String, ImmutableSet<RangeMapping>> mappingsPerNewFilePath =
        getRangeMappingsPerNewFilePath(mappings);
    Map<String, ImmutableList<PositionedEntity<T>>> entitiesPerNewFilePath =
        getEntitiesPerFilePath(filePathUpdatedEntities);
    return entitiesPerNewFilePath.entrySet().stream()
        .flatMap(
            newFilePathAndEntities ->
                shiftRangesInOneFileIfChanged(
                    mappingsPerNewFilePath,
                    newFilePathAndEntities.getKey(),
                    newFilePathAndEntities.getValue())
                    .stream())
        .collect(toImmutableList());
  }

  private static Map<String, ImmutableSet<RangeMapping>> getRangeMappingsPerNewFilePath(
      Set<Mapping> mappings) {
    return mappings.stream()
        .collect(
            groupingBy(
                mapping -> mapping.file().newPath(),
                collectingAndThen(
                    Collectors.<Mapping, Set<RangeMapping>>reducing(
                        new HashSet<>(), Mapping::ranges, Sets::union),
                    ImmutableSet::copyOf)));
  }

  private static <T> Map<String, ImmutableList<PositionedEntity<T>>> getEntitiesPerFilePath(
      List<PositionedEntity<T>> fileUpdatedEntities) {
    return fileUpdatedEntities.stream()
        .collect(groupingBy(entity -> entity.position().filePath(), toImmutableList()));
  }

  private static <T> ImmutableList<PositionedEntity<T>> shiftRangesInOneFileIfChanged(
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

  private static <T> ImmutableList<PositionedEntity<T>> shiftRangesInOneFile(
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
      RangeMapping mapping = sortedMappings.get(mappingIndex);
      if (mapping.oldLineRange().end() <= entity.position().lineRange().start()) {
        shiftedAmount = mapping.newLineRange().end() - mapping.oldLineRange().end();
        mappingIndex++;
      } else if (entity.position().lineRange().end() <= mapping.oldLineRange().start()) {
        resultingEntities.add(entity.shiftPositionBy(shiftedAmount));
        entityIndex++;
      } else {
        // Overlapping -> ignore.
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
                comparing(Range::start).thenComparing(Range::end)))
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

    /** File path in the source tree. */
    public abstract String oldPath();

    /** File path in the target tree. Can be the same as {@link #oldPath()}. */
    public abstract String newPath();

    /**
     * Creates a new {@code FileMapping}.
     *
     * @param oldPath see {@link #oldPath()}
     * @param newPath see {@link #newPath()}
     */
    public static FileMapping create(String oldPath, String newPath) {
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
    public abstract String filePath();

    /** Affected lines. */
    public abstract Range lineRange();

    /**
     * Creates a copy of this {@code Position} whose range is shifted by the indicated amount.
     *
     * @param amount number of lines to shift. Negative values mean moving the range up, positive
     *     values mean moving the range down.
     * @return a new {@code Position} instance with an updated range
     */
    public Position shiftBy(int amount) {
      return toBuilder().lineRange(lineRange().shiftBy(amount)).build();
    }

    /**
     * Creates a copy of this {@code Position} whose file path is adjusted to the indicated value.
     *
     * @param filePath the new file path to use
     * @return a new {@code Position} instance with an update file path
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
     * @param amount amount to shift. Negative values mean moving the range up, positive values mean
     *     moving the range down.
     * @return a new {@code Range} instance with updated start/end
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
      if (amount == 0) {
        // No need to create new instances as nothing was changed.
        return this;
      }
      return new PositionedEntity<>(entity, position.shiftBy(amount), updatedEntityCreator);
    }

    /**
     * Updates the file path of the tracked {@link Position}.
     *
     * @param filePath the new file path to use
     * @return a {@code PositionedEntity} with updated {@link Position}
     */
    public PositionedEntity<T> withFilePath(String filePath) {
      if (position.filePath().equals(filePath)) {
        // No need to create new instances as nothing was changed.
        return this;
      }
      return new PositionedEntity<>(entity, position.withFilePath(filePath), updatedEntityCreator);
    }
  }
}
