// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks.db;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.plugins.checks.CheckerRef;
import com.google.gerrit.plugins.checks.CheckerUuid;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * {@link VersionedMetaData} subclass to read/update the repository to checkers map.
 *
 * <p>The map of repository to checkers is stored in the {@code refs/meta/checkers} notes branch in
 * the {@code All-Projects} repository. The note ID is a SHA1 that is computed from the repository
 * name. The node content is a plain list of checker UUIDs, one checker UUID per line.
 *
 * <p>This is a low-level API. Reading of the repository to checkers map should be done through
 * {@link
 * com.google.gerrit.plugins.checks.Checkers#checkersOf(com.google.gerrit.reviewdb.client.Project.NameKey)}.
 * Updates to the repository to checkers map are done automatically when creating/updating checkers
 * through {@link com.google.gerrit.plugins.checks.CheckersUpdate}.
 *
 * <p>On load the note map from {@code refs/meta/checkers} is read, but the checker lists are not
 * parsed yet (see {@link #onLoad()}).
 *
 * <p>After loading the note map callers can access the checker list for a single repository. Only
 * now the requested checker list is parsed.
 *
 * <p>After loading the note map callers can stage various updates for the repository to checker map
 * (insert, update, remove).
 *
 * <p>On save the staged updates for the repository to checkers map are performed (see {@link
 * #onSave(CommitBuilder)}).
 */
@VisibleForTesting
public class CheckersByRepositoryNotes extends VersionedMetaData {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int MAX_NOTE_SZ = 1 << 19;

  public static CheckersByRepositoryNotes load(
      AllProjectsName allProjectsName, Repository allProjectsRepo)
      throws IOException, ConfigInvalidException {
    return new CheckersByRepositoryNotes(allProjectsName, allProjectsRepo).load();
  }

  public static CheckersByRepositoryNotes load(
      AllProjectsName allProjectsName, Repository allProjectsRepo, @Nullable ObjectId rev)
      throws IOException, ConfigInvalidException {
    return new CheckersByRepositoryNotes(allProjectsName, allProjectsRepo).load(rev);
  }

  private final AllProjectsName allProjectsName;
  private final Repository repo;

  // the loaded note map
  private NoteMap noteMap;

  // Staged note map updates that should be executed on save.
  private List<NoteMapUpdate> noteMapUpdates = new ArrayList<>();

  private CheckersByRepositoryNotes(AllProjectsName allProjectsName, Repository allProjectsRepo) {
    this.allProjectsName = requireNonNull(allProjectsName, "allProjectsName");
    this.repo = requireNonNull(allProjectsRepo, "allProjectsRepo");
  }

  public Repository getRepository() {
    return repo;
  }

  @Override
  protected String getRefName() {
    return CheckerRef.REFS_META_CHECKERS;
  }

  /**
   * Loads the checkers by repository notes from the current tip of the {@code refs/meta/checkers}
   * branch.
   *
   * @return {@link CheckersByRepositoryNotes} instance for chaining
   */
  private CheckersByRepositoryNotes load() throws IOException, ConfigInvalidException {
    super.load(allProjectsName, repo);
    return this;
  }

  /**
   * Loads the checkers by repository notes from the specified revision of the {@code
   * refs/meta/checkers} branch.
   *
   * @param rev the revision from which the checkers by repository notes should be loaded, if {@code
   *     null} the checkers by repository notes are loaded from the current tip, if {@link
   *     ObjectId#zeroId()} it's assumed that the {@code refs/meta/checkers} branch doesn't exist
   *     and the loaded checkers by repository will be empty
   * @return {@link CheckersByRepositoryNotes} instance for chaining
   */
  CheckersByRepositoryNotes load(@Nullable ObjectId rev)
      throws IOException, ConfigInvalidException {
    if (rev == null) {
      return load();
    }
    if (ObjectId.zeroId().equals(rev)) {
      load(allProjectsName, repo, null);
      return this;
    }
    load(allProjectsName, repo, rev);
    return this;
  }

  /**
   * Parses and returns the set of checker UUIDs for the specified repository.
   *
   * <p>Invalid checker UUIDs are silently ignored.
   *
   * @param repositoryName the name of the repository for which the set of checker UUIDs should be
   *     parsed and returned
   * @return the set of checker UUIDs for the specified repository, empty set if no checkers apply
   *     for this repository
   * @throws IOException if reading the note with the checker UUID list fails
   */
  public ImmutableSortedSet<String> get(Project.NameKey repositoryName) throws IOException {
    checkLoaded();
    ObjectId noteId = computeRepositorySha1(repositoryName);
    if (!noteMap.contains(noteId)) {
      return ImmutableSortedSet.of();
    }

    try (RevWalk rw = new RevWalk(repo)) {
      ObjectId noteDataId = noteMap.get(noteId);
      byte[] raw = readNoteData(rw, noteDataId);
      return parseCheckerUuidsFromNote(noteId, raw, noteDataId);
    }
  }

  /**
   * Inserts a new checker for a repository.
   *
   * <p><strong>Note:</strong> This method doesn't perform the update. It only contains the
   * instructions for the update. To apply the update for real and write the result back to NoteDb,
   * call {@link #commit(MetaDataUpdate)} on this {@code CheckersByRepositoryNotes}.
   *
   * @param checkerUuid the UUID of the checker that should be inserted for the given repository
   * @param repositoryName the name of the repository for which the checker should be inserted
   */
  public void insert(String checkerUuid, Project.NameKey repositoryName) {
    checkLoaded();

    noteMapUpdates.add(
        (rw, n, f) -> {
          insert(rw, inserter, n, f, checkerUuid, repositoryName);
        });
  }

  /**
   * Removes a checker from a repository.
   *
   * <p><strong>Note:</strong> This method doesn't perform the update. It only contains the
   * instructions for the update. To apply the update for real and write the result back to NoteDb,
   * call {@link #commit(MetaDataUpdate)} on this {@code CheckersByRepositoryNotes}.
   *
   * @param checkerUuid the UUID of the checker that should be removed from the given repository
   * @param repositoryName the name of the repository for which the checker should be removed
   */
  public void remove(String checkerUuid, Project.NameKey repositoryName) {
    checkLoaded();

    noteMapUpdates.add(
        (rw, n, f) -> {
          remove(rw, inserter, n, f, checkerUuid, repositoryName);
        });
  }

  /**
   * Updates the repository for a checker.
   *
   * <p><strong>Note:</strong> This method doesn't perform the update. It only contains the
   * instructions for the update. To apply the update for real and write the result back to NoteDb,
   * call {@link #commit(MetaDataUpdate)} on this {@code CheckersByRepositoryNotes}.
   *
   * @param checkerUuid the UUID of the checker that should be removed from the given repository
   * @param oldRepositoryName the name of the repository for which the checker should be removed
   * @param newRepositoryName the name of the repository for which the checker should be inserted
   */
  public void update(
      String checkerUuid, Project.NameKey oldRepositoryName, Project.NameKey newRepositoryName) {
    checkLoaded();

    if (oldRepositoryName.equals(newRepositoryName)) {
      return;
    }

    noteMapUpdates.add(
        (rw, n, f) -> {
          remove(rw, inserter, n, f, checkerUuid, oldRepositoryName);
          insert(rw, inserter, n, f, checkerUuid, newRepositoryName);
        });
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    logger.atFine().log("Reading checkers by repository note map");

    noteMap = revision != null ? NoteMap.read(reader, revision) : NoteMap.newEmptyMap();
  }

  private void checkLoaded() {
    checkState(noteMap != null, "Checkers by repository not loaded yet");
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    if (noteMapUpdates.isEmpty()) {
      return false;
    }

    logger.atFine().log("Updating checkers by repository");

    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Update checkers by repository\n");
    }

    try (RevWalk rw = new RevWalk(reader)) {
      ImmutableSortedSet.Builder<String> footersBuilder = ImmutableSortedSet.naturalOrder();
      for (NoteMapUpdate noteMapUpdate : noteMapUpdates) {
        noteMapUpdate.execute(rw, noteMap, footersBuilder);
      }
      noteMapUpdates.clear();
      ImmutableSortedSet<String> footers = footersBuilder.build();
      if (!footers.isEmpty()) {
        commit.setMessage(
            footers.stream().collect(joining("\n", commit.getMessage().trim() + "\n\n", "")));
      }

      RevTree oldTree = revision != null ? rw.parseTree(revision) : null;
      ObjectId newTreeId = noteMap.writeTree(inserter);
      if (newTreeId.equals(oldTree)) {
        return false;
      }

      commit.setTreeId(newTreeId);
      return true;
    }
  }

  private static byte[] readNoteData(RevWalk rw, ObjectId noteDataId) throws IOException {
    return rw.getObjectReader().open(noteDataId, OBJ_BLOB).getCachedBytes(MAX_NOTE_SZ);
  }

  /**
   * Parses a list of checker UUIDs from a byte array that contain the checker UUIDs as a plain text
   * with one checker UUID per line:
   *
   * <pre>
   * e021147da7713263c46d3126b77a863930ff555b
   * e497b37e55074b7a11832a7e2d18c44b4dab8017
   * 8cc1d2415fd4fc78b7d5cc02ac59ee3939d0e1da
   * </pre>
   *
   * <p>Invalid checker UUIDs are silently ignored.
   */
  private static ImmutableSortedSet<String> parseCheckerUuidsFromNote(
      ObjectId noteId, byte[] raw, ObjectId blobId) {
    ImmutableSortedSet<String> lines = parseNote(raw);
    ImmutableSortedSet.Builder<String> checkerUuids = ImmutableSortedSet.naturalOrder();
    lines.forEach(
        line -> {
          if (CheckerUuid.isUuid(line)) {
            checkerUuids.add(line);
          } else {
            logger.atWarning().log(
                "Ignoring invalid checker UUID %s in note %s with blob ID %s.",
                line, noteId.name(), blobId.name());
          }
        });
    return checkerUuids.build();
  }

  /**
   * Parses all entries from a note, one entry per line.
   *
   * <p>Doesn't validate the entries are valid checker UUIDs.
   */
  private static ImmutableSortedSet<String> parseNote(byte[] raw) {
    return Splitter.on('\n')
        .splitToList(new String(raw, UTF_8))
        .stream()
        .collect(toImmutableSortedSet(naturalOrder()));
  }

  /**
   * Insert a checker UUID for a repository and updates the note map.
   *
   * <p>No-op if the checker UUID is already recorded for the repository.
   */
  private static void insert(
      RevWalk rw,
      ObjectInserter ins,
      NoteMap noteMap,
      ImmutableSortedSet.Builder<String> footers,
      String checkerUuid,
      Project.NameKey repositoryName)
      throws IOException {
    ObjectId noteId = computeRepositorySha1(repositoryName);
    ImmutableSortedSet.Builder<String> newLinesBuilder = ImmutableSortedSet.naturalOrder();
    if (noteMap.contains(noteId)) {
      ObjectId noteDataId = noteMap.get(noteId);
      byte[] raw = readNoteData(rw, noteDataId);
      ImmutableSortedSet<String> oldLines = parseNote(raw);
      if (oldLines.contains(checkerUuid)) {
        return;
      }
      newLinesBuilder.addAll(oldLines);
    }

    newLinesBuilder.add(checkerUuid);
    byte[] raw = Joiner.on("\n").join(newLinesBuilder.build()).getBytes(UTF_8);
    ObjectId noteData = ins.insert(OBJ_BLOB, raw);
    noteMap.set(noteId, noteData);
    addFooters(footers, checkerUuid, repositoryName);
  }

  /**
   * Removes a checker UUID from a repository and updates the note map.
   *
   * <p>No-op if the checker UUID is already not recorded for the repository.
   */
  private static void remove(
      RevWalk rw,
      ObjectInserter ins,
      NoteMap noteMap,
      ImmutableSortedSet.Builder<String> footers,
      String checkerUuid,
      Project.NameKey repositoryName)
      throws IOException {
    ObjectId noteId = computeRepositorySha1(repositoryName);
    ImmutableSortedSet.Builder<String> newLinesBuilder = ImmutableSortedSet.naturalOrder();
    if (noteMap.contains(noteId)) {
      ObjectId noteDataId = noteMap.get(noteId);
      byte[] raw = readNoteData(rw, noteDataId);
      ImmutableSortedSet<String> oldLines = parseNote(raw);
      if (!oldLines.contains(checkerUuid)) {
        return;
      }
      oldLines.stream().filter(line -> !line.equals(checkerUuid)).forEach(newLinesBuilder::add);
    }

    ImmutableSortedSet<String> newLines = newLinesBuilder.build();
    if (newLines.isEmpty()) {
      noteMap.remove(noteId);
      return;
    }

    byte[] raw = Joiner.on("\n").join(newLines).getBytes(UTF_8);
    ObjectId noteData = ins.insert(OBJ_BLOB, raw);
    noteMap.set(noteId, noteData);
    addFooters(footers, checkerUuid, repositoryName);
  }

  private static void addFooters(
      ImmutableSortedSet.Builder<String> footers,
      String checkerUuid,
      Project.NameKey repositoryName) {
    footers.add("Repository: " + repositoryName.get());
    footers.add("Checker: " + checkerUuid);
  }

  /**
   * Returns the SHA1 of the repository that is used as note ID in the {@code refs/meta/checkers}
   * notes branch.
   *
   * @param repositoryName the name of the repository for which the SHA1 should be computed and
   *     returned
   * @return SHA1 for the given repository name
   */
  @VisibleForTesting
  @SuppressWarnings("deprecation") // Use Hashing.sha1 for compatibility.
  public static ObjectId computeRepositorySha1(Project.NameKey repositoryName) {
    return ObjectId.fromRaw(Hashing.sha1().hashString(repositoryName.get(), UTF_8).asBytes());
  }

  @FunctionalInterface
  private interface NoteMapUpdate {
    void execute(RevWalk rw, NoteMap noteMap, ImmutableSortedSet.Builder<String> footers)
        throws IOException;
  }
}
