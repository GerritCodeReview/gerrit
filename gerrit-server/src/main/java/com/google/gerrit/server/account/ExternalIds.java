// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.account;

import static java.util.stream.Collectors.toSet;
import static org.eclipse.jgit.lib.Constants.OBJ_BLOB;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.Note;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Class to read external IDs from ReviewDb or NoteDb.
 *
 * Whether external IDs are read from RevieDb or NoteDb is controlled by the
 * user.readExternalIdsFromGit parameter in gerrit.config.
 *
 * In NoteDb external IDs are stored in the All-Users repository in a Git Notes
 * branch called refs/meta/external-ids where the sha1 of the external ID is
 * used as note name. Each note content is a git config file that contains an
 * external ID. It has exactly one externalId subsection with an accountId and
 * optionally email and password:
 *
 * <pre>
 * [externalId "username:jdoe"]
 *   accountId = 1003407
 *   email = jdoe@example.com
 *   password = my-secret
 * </pre>
 */
@Singleton
public class ExternalIds {
  private static final Logger log = LoggerFactory
      .getLogger(ExternalIds.class);

  public static final int MAX_NOTE_SZ = 25 << 20;

  private final Config cfg;
  private final GitRepositoryManager repoManager;
  private final AllUsersName allUsersName;

  @Inject
  public ExternalIds(@GerritServerConfig Config cfg,
      GitRepositoryManager repoManager,
      AllUsersName allUsersName) {
    this.cfg = cfg;
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
  }

  /**
   * Reads and returns all external IDs.
   */
  public Collection<ExternalId> all(ReviewDb db) throws IOException, OrmException {
    if (!readFromGit(cfg)) {
      return db.accountExternalIds().all().toList().stream()
          .map(e -> ExternalId.from(e)).collect(toSet());
    }

    try (Repository repo = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(repo)) {
      ObjectId rev = readRevision(repo);
      if (rev.equals(ObjectId.zeroId())) {
        return ImmutableSet.of();
      }

      NoteMap noteMap = readNoteMap(rw, rev);
      Set<ExternalId> extIds = new HashSet<>();
      for (Note note : noteMap) {
        byte[] raw = rw.getObjectReader().open(note.getData(), OBJ_BLOB)
            .getCachedBytes(MAX_NOTE_SZ);
        try {
          extIds.add(ExternalId.parse(note.getName(), raw));
        } catch (ConfigInvalidException e) {
          log.error(String.format("Ignoring invalid external ID note %s",
              note.getName()), e);
        }
      }
      return extIds;
    }
  }

  /**
   * Reads and returns the specified external ID.
   */
  public ExternalId get(ReviewDb db, ExternalId.Key key)
      throws OrmException, IOException, ConfigInvalidException {
    if (!readFromGit(cfg)) {
      return ExternalId.from(
          db.accountExternalIds().get(key.asAccountExternalIdKey()));
    }

    try (Repository repo = repoManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(repo)) {
      ObjectId rev = readRevision(repo);
      if (rev.equals(ObjectId.zeroId())) {
        return null;
      }

      NoteMap noteMap = readNoteMap(rw, rev);
      ObjectId noteId = key.sha1();
      if (!noteMap.contains(noteId)) {
        return null;
      }

      byte[] raw = rw.getObjectReader().open(noteMap.get(noteId), OBJ_BLOB)
            .getCachedBytes(MAX_NOTE_SZ);
      return ExternalId.parse(noteId.name(), raw);
    }
  }

  public static ObjectId readRevision(Repository repo) throws IOException {
    Ref ref = repo.exactRef(RefNames.REFS_EXTERNAL_IDS);
    return ref != null ? ref.getObjectId() : ObjectId.zeroId();
  }

  public static NoteMap readNoteMap(RevWalk rw, ObjectId rev)
      throws IOException {
    if (!rev.equals(ObjectId.zeroId())) {
      return NoteMap.read(rw.getObjectReader(), rw.parseCommit(rev));
    }
    return NoteMap.newEmptyMap();
  }

  private static boolean readFromGit(Config cfg) {
    return cfg.getBoolean("user", "readExternalIdsFromGit", false);
  }
}
