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

package com.google.gerrit.server.verifier.db;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.git.meta.VersionedMetaData;
import com.google.gerrit.server.verifier.VerifierUuid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * {@link VersionedMetaData} subclass to read/update the repository to verifiers map.
 *
 * <p>The map of repository to verifiers is stored in the {@code refs/meta/verifiers} notes branch
 * in the {@code All-Projects} repository. The note ID is a SHA1 that is computed from the
 * repository name. The node content is a plain list of verifier UUIDs, one verifier UUID per line.
 *
 * <p>This is a low-level API. Reading of the repository to verifiers map should be done through
 * {@link
 * com.google.gerrit.server.verifier.Verifiers#verifiersOf(com.google.gerrit.reviewdb.client.Project.NameKey)}.
 * Updates to the repository to verifiers map are done automatically when creating/updating
 * verifiers through {@link com.google.gerrit.server.verifier.VerifiersUpdate}.
 *
 * <p>On load the note map from {@code refs/meta/verifiers} is read, but the verifier lists are not
 * parsed yet (see {@link #onLoad()}).
 *
 * <p>After loading the note map callers can access the verifier list for a single repository. Only
 * now the requested verifier list is parsed.
 *
 * <p>After loading the note map callers can stage various updates for the repository to verifier
 * map (insert, update, remove).
 *
 * <p>On save the staged updates for the repository to verifiers map are performed (see {@link
 * #onSave(CommitBuilder)}).
 */
@VisibleForTesting
public class VerifiersByRepositoryNotes extends VersionedMetaData {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final int MAX_NOTE_SZ = 1 << 19;

  public static VerifiersByRepositoryNotes load(
      AllProjectsName allProjectsName, Repository allProjectsRepo)
      throws IOException, ConfigInvalidException {
    return new VerifiersByRepositoryNotes(allProjectsName, allProjectsRepo).load();
  }

  public static VerifiersByRepositoryNotes load(
      AllProjectsName allProjectsName, Repository allProjectsRepo, @Nullable ObjectId rev)
      throws IOException, ConfigInvalidException {
    return new VerifiersByRepositoryNotes(allProjectsName, allProjectsRepo).load(rev);
  }

  private final AllProjectsName allProjectsName;
  private final Repository repo;

  // the loaded note map
  private NoteMap noteMap;

  // Staged note map updates that should be executed on save.
  private List<NoteMapUpdate> noteMapUpdates = new ArrayList<>();

  private VerifiersByRepositoryNotes(AllProjectsName allProjectsName, Repository allProjectsRepo) {
    this.allProjectsName = requireNonNull(allProjectsName, "allProjectsName");
    this.repo = requireNonNull(allProjectsRepo, "allProjectsRepo");
  }

  public Repository getRepository() {
    return repo;
  }

  @Override
  protected String getRefName() {
    return RefNames.REFS_META_VERIFIERS;
  }

  /**
   * Loads the verifiers by repository notes from the current tip of the {@code refs/meta/verifiers}
   * branch.
   *
   * @return {@link VerifiersByRepositoryNotes} instance for chaining
   */
  private VerifiersByRepositoryNotes load() throws IOException, ConfigInvalidException {
    super.load(allProjectsName, repo);
    return this;
  }

  /**
   * Loads the verifiers by repository notes from the specified revision of the {@code
   * refs/meta/verifiers} branch.
   *
   * @param rev the revision from which the verifiers by repository notes should be loaded, if
   *     {@code null} the verifiers by repository notes are loaded from the current tip, if {@link
   *     ObjectId#zeroId()} it's assumed that the {@code refs/meta/verifiers} branch doesn't exist
   *     and the loaded verifiers by repository will be empty
   * @return {@link VerifiersByRepositoryNotes} instance for chaining
   */
  VerifiersByRepositoryNotes load(@Nullable ObjectId rev)
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
   * Parses and returns the set of verifier UUIDs for the specified repository.
   *
   * <p>Invalid verifier UUIDs are silently ignored.
   *
   * @param repositoryName the name of the repository for which the set of verifier UUIDs should be
   *     parsed and returned
   * @return the set of verifier UUIDs for the specified repository, empty set if no verifiers apply
   *     for this repository
   * @throws IOException if reading the note with the verifier UUID list fails
   */
  public ImmutableSet<String> get(Project.NameKey repositoryName) throws IOException {
    checkLoaded();
    ObjectId noteId = computeRepositorySha1(repositoryName);
    if (!noteMap.contains(noteId)) {
      return ImmutableSet.of();
    }

    try (RevWalk rw = new RevWalk(repo)) {
      ObjectId noteDataId = noteMap.get(noteId);
      byte[] raw = readNoteData(rw, noteDataId);
      return parseVerifierUuidsFromNote(noteId, raw, noteDataId);
    }
  }

  /**
   * Inserts a new verifier for a repository.
   *
   * <p><strong>Note:</strong> This method doesn't perform the update. It only contains the
   * instructions for the update. To apply the update for real and write the result back to NoteDb,
   * call {@link #commit(MetaDataUpdate)} on this {@code VerifiersByRepositoryNotes}.
   *
   * @param verifierUuid the UUID of the verifier that should be inserted for the given repository
   * @param repositoryName the name of the repository for which the verifier should be inserted
   */
  public void insert(String verifierUuid, Project.NameKey repositoryName) {
    checkLoaded();

    noteMapUpdates.add(
        (rw, n, f) -> {
          insert(rw, inserter, n, f, verifierUuid, repositoryName);
        });
  }

  /**
   * Removes a verifier from a repository.
   *
   * <p><strong>Note:</strong> This method doesn't perform the update. It only contains the
   * instructions for the update. To apply the update for real and write the result back to NoteDb,
   * call {@link #commit(MetaDataUpdate)} on this {@code VerifiersByRepositoryNotes}.
   *
   * @param verifierUuid the UUID of the verifier that should be removed from the given repository
   * @param repositoryName the name of the repository for which the verifier should be removed
   */
  public void remove(String verifierUuid, Project.NameKey repositoryName) {
    checkLoaded();

    noteMapUpdates.add(
        (rw, n, f) -> {
          remove(rw, inserter, n, f, verifierUuid, repositoryName);
        });
  }

  /**
   * Updates the repository for a verifier.
   *
   * <p><strong>Note:</strong> This method doesn't perform the update. It only contains the
   * instructions for the update. To apply the update for real and write the result back to NoteDb,
   * call {@link #commit(MetaDataUpdate)} on this {@code VerifiersByRepositoryNotes}.
   *
   * @param verifierUuid the UUID of the verifier that should be removed from the given repository
   * @param oldRepositoryName the name of the repository for which the verifier should be removed
   * @param newRepositoryName the name of the repository for which the verifier should be inserted
   */
  public void update(
      String verifierUuid, Project.NameKey oldRepositoryName, Project.NameKey newRepositoryName) {
    checkLoaded();

    if (oldRepositoryName.equals(newRepositoryName)) {
      return;
    }

    noteMapUpdates.add(
        (rw, n, f) -> {
          remove(rw, inserter, n, f, verifierUuid, oldRepositoryName);
          insert(rw, inserter, n, f, verifierUuid, newRepositoryName);
        });
  }

  @Override
  protected void onLoad() throws IOException, ConfigInvalidException {
    logger.atFine().log("Reading verifiers by repository note map");

    noteMap = revision != null ? NoteMap.read(reader, revision) : NoteMap.newEmptyMap();
  }

  private void checkLoaded() {
    checkState(noteMap != null, "Verifiers by repository not loaded yet");
  }

  @Override
  protected boolean onSave(CommitBuilder commit) throws IOException, ConfigInvalidException {
    if (noteMapUpdates.isEmpty()) {
      return false;
    }

    logger.atFine().log("Updating verifiers by repository");

    if (Strings.isNullOrEmpty(commit.getMessage())) {
      commit.setMessage("Update verifiers by repository\n");
    }

    try (RevWalk rw = new RevWalk(reader)) {
      Set<String> footers = new HashSet<>();
      for (NoteMapUpdate noteMapUpdate : noteMapUpdates) {
        noteMapUpdate.execute(rw, noteMap, footers);
      }
      noteMapUpdates.clear();
      if (!footers.isEmpty()) {
        commit.setMessage(
            footers
                .stream()
                .sorted()
                .collect(joining("\n", commit.getMessage().trim() + "\n\n", "")));
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
   * Parses a list of verifier UUIDs from a byte array that contain the verifier UUIDs as a plain
   * text with one verifier UUID per line:
   *
   * <pre>
   * e021147da7713263c46d3126b77a863930ff555b
   * e497b37e55074b7a11832a7e2d18c44b4dab8017
   * 8cc1d2415fd4fc78b7d5cc02ac59ee3939d0e1da
   * </pre>
   *
   * <p>Invalid verifier UUIDs are silently ignored.
   */
  private static ImmutableSet<String> parseVerifierUuidsFromNote(
      ObjectId noteId, byte[] raw, ObjectId blobId) {
    ImmutableSet<String> lines = parseNote(raw);
    Set<String> verifierUuids = new HashSet<>(lines.size());
    lines.forEach(
        line -> {
          if (VerifierUuid.isUuid(line)) {
            verifierUuids.add(line);
          } else {
            logger.atWarning().log(
                "Ignoring invalid verifier UUID %s in note %s with blob ID %s.",
                line, noteId.name(), blobId.name());
          }
        });
    return ImmutableSet.copyOf(verifierUuids);
  }

  /**
   * Parses all entries from a note, one entry per line.
   *
   * <p>Doesn't validate the entries are valid verifier UUIDs.
   */
  private static ImmutableSet<String> parseNote(byte[] raw) {
    return Splitter.on('\n').splitToList(new String(raw, UTF_8)).stream().collect(toImmutableSet());
  }

  /**
   * Insert a verifier UUID for a repository and updates the note map.
   *
   * <p>No-op if the verifier UUID is already recorded for the repository.
   */
  private static void insert(
      RevWalk rw,
      ObjectInserter ins,
      NoteMap noteMap,
      Set<String> footers,
      String verifierUuid,
      Project.NameKey repositoryName)
      throws IOException {
    ObjectId noteId = computeRepositorySha1(repositoryName);
    TreeSet<String> newLines = new TreeSet<>();
    if (noteMap.contains(noteId)) {
      ObjectId noteDataId = noteMap.get(noteId);
      byte[] raw = readNoteData(rw, noteDataId);
      ImmutableSet<String> oldLines = parseNote(raw);
      if (oldLines.contains(verifierUuid)) {
        return;
      }
      newLines.addAll(oldLines);
    }

    newLines.add(verifierUuid);
    byte[] raw = Joiner.on("\n").join(newLines).getBytes(UTF_8);
    ObjectId noteData = ins.insert(OBJ_BLOB, raw);
    noteMap.set(noteId, noteData);
    addFooters(footers, verifierUuid, repositoryName);
  }

  /**
   * Removes a verifier UUID from a repository and updates the note map.
   *
   * <p>No-op if the verifier UUID is already not recorded for the repository.
   */
  private static void remove(
      RevWalk rw,
      ObjectInserter ins,
      NoteMap noteMap,
      Set<String> footers,
      String verifierUuid,
      Project.NameKey repositoryName)
      throws IOException {
    ObjectId noteId = computeRepositorySha1(repositoryName);
    TreeSet<String> newLines = new TreeSet<>();
    if (noteMap.contains(noteId)) {
      ObjectId noteDataId = noteMap.get(noteId);
      byte[] raw = readNoteData(rw, noteDataId);
      ImmutableSet<String> oldLines = parseNote(raw);
      if (!oldLines.contains(verifierUuid)) {
        return;
      }
      newLines.addAll(oldLines);
    }

    newLines.remove(verifierUuid);

    if (newLines.isEmpty()) {
      noteMap.remove(noteId);
      return;
    }

    byte[] raw = Joiner.on("\n").join(newLines).getBytes(UTF_8);
    ObjectId noteData = ins.insert(OBJ_BLOB, raw);
    noteMap.set(noteId, noteData);
    addFooters(footers, verifierUuid, repositoryName);
  }

  private static void addFooters(
      Set<String> footers, String verifierUuid, Project.NameKey repositoryName) {
    footers.add("Repository: " + repositoryName.get());
    footers.add("Verifier: " + verifierUuid);
  }

  /**
   * Returns the SHA1 of the repository that is used as note ID in the {@code refs/meta/verifiers}
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
    void execute(RevWalk rw, NoteMap noteMap, Set<String> footers) throws IOException;
  }
}
