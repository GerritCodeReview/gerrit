// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.account.externalids.storage.notedb;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.account.externalids.ExternalId;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevWalk;

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

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  /** Defined only for handling the case when externalIdsRefExpirySecs is zero */
  private static final Supplier<ObjectId> UNUSED_OBJECT_ID_SUPPLIER =
      Suppliers.ofInstance(ObjectId.zeroId());

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
  private final Timer0 readSingleLatency;
  private final ExternalIdFactoryNoteDbImpl externalIdFactory;
  private final AuthConfig authConfig;
  private final Supplier<ObjectId> allUsersSupplier;
  private final int externalIdsRefExpirySecs;

  @VisibleForTesting
  @Inject
  public ExternalIdReader(
      GitRepositoryManager repoManager,
      AllUsersName allUsersName,
      MetricMaker metricMaker,
      ExternalIdFactoryNoteDbImpl externalIdFactory,
      AuthConfig authConfig) {
    this.repoManager = repoManager;
    this.allUsersName = allUsersName;
    this.readAllLatency =
        metricMaker.newTimer(
            "notedb/read_all_external_ids_latency",
            new Description("Latency for reading all external IDs from NoteDb.")
                .setCumulative()
                .setUnit(Units.MILLISECONDS));
    this.readSingleLatency =
        metricMaker.newTimer(
            "notedb/read_single_external_id_latency",
            new Description("Latency for reading a single external ID from NoteDb.")
                .setCumulative()
                .setUnit(Units.MILLISECONDS));
    this.externalIdFactory = externalIdFactory;
    this.authConfig = authConfig;
    this.externalIdsRefExpirySecs = authConfig.getExternalIdsRefExpirySecs();
    this.allUsersSupplier =
        externalIdsRefExpirySecs > 0
            ? Suppliers.memoizeWithExpiration(
                () -> {
                  try {
                    logger.atFine().log("Refreshing external-ids revision from All-Users repo");
                    return readRevision(repoManager, allUsersName);
                  } catch (IOException e) {
                    throw new IllegalStateException(
                        "Couldn't refresh external-ids from All-Users repo", e);
                  }
                },
                Duration.ofSeconds(externalIdsRefExpirySecs))
            : UNUSED_OBJECT_ID_SUPPLIER;
  }

  @VisibleForTesting
  public void setFailOnLoad(boolean failOnLoad) {
    this.failOnLoad = failOnLoad;
  }

  public void checkReadEnabled() throws IOException {
    if (failOnLoad) {
      throw new IOException("Reading from external IDs is disabled");
    }
  }

  public ObjectId readRevision() throws IOException {
    return externalIdsRefExpirySecs > 0
        ? allUsersSupplier.get()
        : readRevision(repoManager, allUsersName);
  }

  private static ObjectId readRevision(GitRepositoryManager repoManager, AllUsersName allUsersName)
      throws IOException {
    try (Repository repo = repoManager.openRepository(allUsersName)) {
      return readRevision(repo);
    }
  }

  public static ObjectId readRevision(Repository repo) throws IOException {
    Ref ref = repo.exactRef(RefNames.REFS_EXTERNAL_IDS);
    return ref != null ? ref.getObjectId() : ObjectId.zeroId();
  }

  /** Reads and returns all external IDs. */
  ImmutableSet<ExternalId> all() throws IOException, ConfigInvalidException {
    checkReadEnabled();

    try (Timer0.Context ctx = readAllLatency.start();
        Repository repo = repoManager.openRepository(allUsersName)) {
      return ExternalIdNotes.loadReadOnly(
              allUsersName,
              repo,
              null,
              externalIdFactory,
              authConfig.isUserNameCaseInsensitiveMigrationMode())
          .all();
    }
  }

  /**
   * Reads and returns all external IDs from the specified revision of the {@code
   * refs/meta/external-ids} branch.
   *
   * @param rev the revision from which the external IDs should be read, if {@code null} the
   *     external IDs are read from the current tip, if {@link ObjectId#zeroId()} it's assumed that
   *     the {@code refs/meta/external-ids} branch doesn't exist and the loaded external IDs will be
   *     empty
   * @return all external IDs that were read from the specified revision
   */
  public ImmutableSet<ExternalId> all(@Nullable ObjectId rev)
      throws IOException, ConfigInvalidException {
    checkReadEnabled();

    try (Timer0.Context ctx = readAllLatency.start();
        Repository repo = repoManager.openRepository(allUsersName)) {
      return ExternalIdNotes.loadReadOnly(
              allUsersName,
              repo,
              rev,
              externalIdFactory,
              authConfig.isUserNameCaseInsensitiveMigrationMode())
          .all();
    }
  }

  /** Reads and returns the specified external ID. */
  Optional<ExternalId> get(ExternalId.Key key) throws IOException, ConfigInvalidException {
    checkReadEnabled();

    try (Timer0.Context ctx = readSingleLatency.start();
        Repository repo = repoManager.openRepository(allUsersName)) {
      return ExternalIdNotes.loadReadOnly(
              allUsersName,
              repo,
              null,
              externalIdFactory,
              authConfig.isUserNameCaseInsensitiveMigrationMode())
          .get(key);
    }
  }

  /** Reads and returns the specified external ID from the given revision. */
  Optional<ExternalId> get(ExternalId.Key key, ObjectId rev)
      throws IOException, ConfigInvalidException {
    checkReadEnabled();

    try (Timer0.Context ctx = readSingleLatency.start();
        Repository repo = repoManager.openRepository(allUsersName)) {
      return ExternalIdNotes.loadReadOnly(
              allUsersName,
              repo,
              rev,
              externalIdFactory,
              authConfig.isUserNameCaseInsensitiveMigrationMode())
          .get(key);
    }
  }
}
