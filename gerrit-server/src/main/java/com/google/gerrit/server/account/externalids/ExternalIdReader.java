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

package com.google.gerrit.server.account.externalids;

import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class to read external IDs from NoteDb.
 *
 * <p>In NoteDb external IDs are stored in the All-Users repository in a Git Notes branch called
 * refs/meta/external-ids where the sha1 of the external ID is used as note name. Each note content
 * is a git config file that contains an external ID. It has exactly one externalId subsection with
 * an accountId and optionally email and password:
 *
 * <pre>
 * [externalId "username:jdoe"]
 *   accountId = 1003407
 *   email = jdoe@example.com
 *   password = bcrypt:4:LCbmSBDivK/hhGVQMfkDpA==:XcWn0pKYSVU/UJgOvhidkEtmqCp6oKB7
 * </pre>
 */
@Singleton
public class ExternalIdReader {
  private static final Logger log = LoggerFactory.getLogger(ExternalIdReader.class);

  public static final int MAX_NOTE_SZ = 1 << 19;

  public static ObjectId readRevision(Repository repo) throws IOException {
    Ref ref = repo.exactRef(RefNames.REFS_EXTERNAL_IDS);
    return ref != null ? ref.getObjectId() : ObjectId.zeroId();
  }

  public static NoteMap readNoteMap(RevWalk rw, ObjectId rev) throws IOException {
    if (!rev.equals(ObjectId.zeroId())) {
      return NoteMap.read(rw.getObjectReader(), rw.parseCommit(rev));
    }
    return NoteMap.newEmptyMap();
  }

  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;
  private boolean failOnLoad = false;
  private final Timer0 readAllLatency;

  @Inject
  ExternalIdReader(
      GitRepositoryManager repoManager, AllUsersName allUsersName, MetricMaker metricMaker) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.readAllLatency =
        metricMaker.newTimer(
            "notedb/read_all_external_ids_latency",
            new Description("Latency for reading all external IDs from NoteDb.")
                .setCumulative()
                .setUnit(Units.MILLISECONDS));
  }

  @VisibleForTesting
  public void setFailOnLoad(boolean failOnLoad) {
    this.failOnLoad = failOnLoad;
  }

  ObjectId readRevision() throws IOException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      return readRevision(repo);
    }
  }

  /** Reads and returns all external IDs. */
  Set<ExternalId> all() throws IOException {
    checkReadEnabled();

    try (Repository repo = repoManager.openRepository(allUsersName)) {
      return all(repo, readRevision(repo));
    }
  }

  /**
   * Reads and returns all external IDs from the specified revision of the refs/meta/external-ids
   * branch.
   */
  Set<ExternalId> all(ObjectId rev) throws IOException {
    checkReadEnabled();

    try (Repository repo = repoManager.openRepository(allUsersName)) {
      return all(repo, rev);
    }
  }

  /** Reads and returns all external IDs. */
  private Set<ExternalId> all(Repository repo, ObjectId rev) throws IOException {
    if (rev.equals(ObjectId.zeroId())) {
      return ImmutableSet.of();
    }

    try (Timer0.Context ctx = readAllLatency.start();
        RevWalk rw = new RevWalk(repo)) {
      NoteMap noteMap = readNoteMap(rw, rev);
      Set<ExternalId> extIds = new HashSet<>();
      for (Note note : noteMap) {
        byte[] raw =
            rw.getObjectReader().open(note.getData(), OBJ_BLOB).getCachedBytes(MAX_NOTE_SZ);
        try {
          extIds.add(ExternalId.parse(note.getName(), raw, note.getData()));
        } catch (Exception e) {
          log.error(String.format("Ignoring invalid external ID note %s", note.getName()), e);
        }
      }
      return extIds;
    }
  }

  /** Reads and returns the specified external ID. */
  @Nullable
  ExternalId get(ExternalId.Key key) throws IOException, ConfigInvalidException {
    checkReadEnabled();

    try (Repository repo = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(repo)) {
      ObjectId rev = readRevision(repo);
      if (rev.equals(ObjectId.zeroId())) {
        return null;
      }

      return parse(key, rw, rev);
    }
  }

  /** Reads and returns the specified external ID from the given revision. */
  @Nullable
  ExternalId get(ExternalId.Key key, ObjectId rev) throws IOException, ConfigInvalidException {
    checkReadEnabled();

    if (rev.equals(ObjectId.zeroId())) {
      return null;
    }

    try (Repository repo = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(repo)) {
      return parse(key, rw, rev);
    }
  }

  private static ExternalId parse(ExternalId.Key key, RevWalk rw, ObjectId rev)
      throws IOException, ConfigInvalidException {
    NoteMap noteMap = readNoteMap(rw, rev);
    ObjectId noteId = key.sha1();
    if (!noteMap.contains(noteId)) {
      return null;
    }

    ObjectId noteData = noteMap.get(noteId);
    byte[] raw = rw.getObjectReader().open(noteData, OBJ_BLOB).getCachedBytes(MAX_NOTE_SZ);
    return ExternalId.parse(noteId.name(), raw, noteData);
  }

  private void checkReadEnabled() throws IOException {
    if (failOnLoad) {
      throw new IOException("Reading from external IDs is disabled");
    }
  }
}
