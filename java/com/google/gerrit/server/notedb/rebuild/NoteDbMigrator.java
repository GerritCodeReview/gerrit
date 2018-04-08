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

package com.google.gerrit.server.notedb.rebuild;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.reviewdb.server.ReviewDbUtil.unwrapDb;
import static com.google.gerrit.server.notedb.NotesMigration.SECTION_NOTE_DB;
import static com.google.gerrit.server.notedb.NotesMigrationState.NOTE_DB;
import static com.google.gerrit.server.notedb.NotesMigrationState.READ_WRITE_NO_SEQUENCE;
import static com.google.gerrit.server.notedb.NotesMigrationState.READ_WRITE_WITH_SEQUENCE_NOTE_DB_PRIMARY;
import static com.google.gerrit.server.notedb.NotesMigrationState.READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY;
import static com.google.gerrit.server.notedb.NotesMigrationState.WRITE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.gerrit.common.FormatUtil;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.config.GerritServerConfigProvider;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.reviewdb.server.ReviewDbWrapper;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.InternalUser;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LockFailureException;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.notedb.ChangeBundleReader;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.MutableNotesMigration;
import com.google.gerrit.server.notedb.NoteDbTable;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.gerrit.server.notedb.PrimaryStorageMigrator;
import com.google.gerrit.server.notedb.PrimaryStorageMigrator.NoNoteDbStateException;
import com.google.gerrit.server.notedb.RepoSequence;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilder.NoPatchSetsException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.update.ChainedReceiveCommands;
import com.google.gerrit.server.update.RefUpdateUtil;
import com.google.gerrit.server.util.ManualRequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.PackInserter;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One stop shop for migrating a site's change storage from ReviewDb to NoteDb. */
public class NoteDbMigrator implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(NoteDbMigrator.class);

  private static final String AUTO_MIGRATE = "autoMigrate";
  private static final String TRIAL = "trial";

  public static boolean getAutoMigrate(Config cfg) {
    return cfg.getBoolean(SECTION_NOTE_DB, NoteDbTable.CHANGES.key(), AUTO_MIGRATE, false);
  }

  private static void setAutoMigrate(Config cfg, boolean autoMigrate) {
    cfg.setBoolean(SECTION_NOTE_DB, NoteDbTable.CHANGES.key(), AUTO_MIGRATE, autoMigrate);
  }

  public static boolean getTrialMode(Config cfg) {
    return cfg.getBoolean(SECTION_NOTE_DB, NoteDbTable.CHANGES.key(), TRIAL, false);
  }

  public static void setTrialMode(Config cfg, boolean trial) {
    cfg.setBoolean(SECTION_NOTE_DB, NoteDbTable.CHANGES.key(), TRIAL, trial);
  }

  public static class Builder {
    private final Config cfg;
    private final SitePaths sitePaths;
    private final Provider<PersonIdent> serverIdent;
    private final AllUsersName allUsers;
    private final SchemaFactory<ReviewDb> schemaFactory;
    private final GitRepositoryManager repoManager;
    private final NoteDbUpdateManager.Factory updateManagerFactory;
    private final ChangeBundleReader bundleReader;
    private final AllProjectsName allProjects;
    private final InternalUser.Factory userFactory;
    private final ThreadLocalRequestContext requestContext;
    private final ChangeRebuilderImpl rebuilder;
    private final WorkQueue workQueue;
    private final MutableNotesMigration globalNotesMigration;
    private final PrimaryStorageMigrator primaryStorageMigrator;
    private final DynamicSet<NotesMigrationStateListener> listeners;

    private int threads;
    private ImmutableList<Project.NameKey> projects = ImmutableList.of();
    private ImmutableList<Change.Id> changes = ImmutableList.of();
    private OutputStream progressOut = NullOutputStream.INSTANCE;
    private NotesMigrationState stopAtState;
    private boolean trial;
    private boolean forceRebuild;
    private int sequenceGap = -1;
    private boolean autoMigrate;

    @Inject
    Builder(
        GerritServerConfigProvider configProvider,
        SitePaths sitePaths,
        @GerritPersonIdent Provider<PersonIdent> serverIdent,
        AllUsersName allUsers,
        SchemaFactory<ReviewDb> schemaFactory,
        GitRepositoryManager repoManager,
        NoteDbUpdateManager.Factory updateManagerFactory,
        ChangeBundleReader bundleReader,
        AllProjectsName allProjects,
        ThreadLocalRequestContext requestContext,
        InternalUser.Factory userFactory,
        ChangeRebuilderImpl rebuilder,
        WorkQueue workQueue,
        MutableNotesMigration globalNotesMigration,
        PrimaryStorageMigrator primaryStorageMigrator,
        DynamicSet<NotesMigrationStateListener> listeners) {
      // Reload gerrit.config/notedb.config on each migrator invocation, in case a previous
      // migration in the same process modified the on-disk contents. This ensures the defaults for
      // trial/autoMigrate get set correctly below.
      this.cfg = configProvider.get();
      this.sitePaths = sitePaths;
      this.serverIdent = serverIdent;
      this.allUsers = allUsers;
      this.schemaFactory = schemaFactory;
      this.repoManager = repoManager;
      this.updateManagerFactory = updateManagerFactory;
      this.bundleReader = bundleReader;
      this.allProjects = allProjects;
      this.requestContext = requestContext;
      this.userFactory = userFactory;
      this.rebuilder = rebuilder;
      this.workQueue = workQueue;
      this.globalNotesMigration = globalNotesMigration;
      this.primaryStorageMigrator = primaryStorageMigrator;
      this.listeners = listeners;
      this.trial = getTrialMode(cfg);
      this.autoMigrate = getAutoMigrate(cfg);
    }

    /**
     * Set the number of threads used by parallelizable phases of the migration, such as rebuilding
     * all changes.
     *
     * <p>Not all phases are parallelizable, and calling {@link #rebuild()} directly will do
     * substantial work in the calling thread regardless of the number of threads configured.
     *
     * <p>By default, all work is done in the calling thread.
     *
     * @param threads thread count; if less than 2, all work happens in the calling thread.
     * @return this.
     */
    public Builder setThreads(int threads) {
      this.threads = threads;
      return this;
    }

    /**
     * Limit the set of projects that are processed.
     *
     * <p>Incompatible with {@link #setChanges(Collection)}.
     *
     * <p>By default, all projects will be processed.
     *
     * @param projects set of projects; if null or empty, all projects will be processed.
     * @return this.
     */
    public Builder setProjects(@Nullable Collection<Project.NameKey> projects) {
      this.projects = projects != null ? ImmutableList.copyOf(projects) : ImmutableList.of();
      return this;
    }

    /**
     * Limit the set of changes that are processed.
     *
     * <p>Incompatible with {@link #setProjects(Collection)}.
     *
     * <p>By default, all changes will be processed.
     *
     * @param changes set of changes; if null or empty, all changes will be processed.
     * @return this.
     */
    public Builder setChanges(@Nullable Collection<Change.Id> changes) {
      this.changes = changes != null ? ImmutableList.copyOf(changes) : ImmutableList.of();
      return this;
    }

    /**
     * Set output stream for progress monitors.
     *
     * <p>By default, there is no progress monitor output (although there may be other logs).
     *
     * @param progressOut output stream.
     * @return this.
     */
    public Builder setProgressOut(OutputStream progressOut) {
      this.progressOut = checkNotNull(progressOut);
      return this;
    }

    /**
     * Stop at a specific migration state, for testing only.
     *
     * @param stopAtState state to stop at.
     * @return this.
     */
    @VisibleForTesting
    public Builder setStopAtStateForTesting(NotesMigrationState stopAtState) {
      this.stopAtState = stopAtState;
      return this;
    }

    /**
     * Rebuild in "trial mode": configure Gerrit to write to and read from NoteDb, but leave
     * ReviewDb as the source of truth for all changes.
     *
     * <p>By default, trial mode is off, and NoteDb is the source of truth for all changes following
     * the migration.
     *
     * @param trial whether to rebuild in trial mode.
     * @return this.
     */
    public Builder setTrialMode(boolean trial) {
      this.trial = trial;
      return this;
    }

    /**
     * Rebuild all changes in NoteDb from ReviewDb, even if Gerrit is currently configured to read
     * from NoteDb.
     *
     * <p>Only supported if ReviewDb is still the source of truth for all changes.
     *
     * <p>By default, force rebuilding is off.
     *
     * @param forceRebuild whether to force rebuilding.
     * @return this.
     */
    public Builder setForceRebuild(boolean forceRebuild) {
      this.forceRebuild = forceRebuild;
      return this;
    }

    /**
     * Gap between ReviewDb change sequence numbers and NoteDb.
     *
     * <p>If NoteDb sequences are enabled in a running server, there is a race between the migration
     * step that calls {@code nextChangeId()} to seed the ref, and other threads that call {@code
     * nextChangeId()} to create new changes. In order to prevent these operations stepping on one
     * another, we use this value to skip some predefined sequence numbers. This is strongly
     * recommended in a running server.
     *
     * <p>If the migration takes place offline, there is no race with other threads, and this option
     * may be set to 0. However, admins may still choose to use a gap, for example to make it easier
     * to distinguish changes that were created before and after the NoteDb migration.
     *
     * <p>By default, uses the value from {@code noteDb.changes.initialSequenceGap} in {@code
     * gerrit.config}, which defaults to 1000.
     *
     * @param sequenceGap sequence gap size; if negative, use the default.
     * @return this.
     */
    public Builder setSequenceGap(int sequenceGap) {
      this.sequenceGap = sequenceGap;
      return this;
    }

    /**
     * Enable auto-migration on subsequent daemon launches.
     *
     * <p>If true, prior to running any migration steps, sets the necessary configuration in {@code
     * gerrit.config} to make {@code gerrit.war daemon} retry the migration on next startup, if it
     * fails.
     *
     * @param autoMigrate whether to set auto-migration config.
     * @return this.
     */
    public Builder setAutoMigrate(boolean autoMigrate) {
      this.autoMigrate = autoMigrate;
      return this;
    }

    public NoteDbMigrator build() throws MigrationException {
      return new NoteDbMigrator(
          sitePaths,
          schemaFactory,
          serverIdent,
          allUsers,
          repoManager,
          updateManagerFactory,
          bundleReader,
          allProjects,
          requestContext,
          userFactory,
          rebuilder,
          globalNotesMigration,
          primaryStorageMigrator,
          listeners,
          threads > 1
              ? MoreExecutors.listeningDecorator(workQueue.createQueue(threads, "RebuildChange"))
              : MoreExecutors.newDirectExecutorService(),
          projects,
          changes,
          progressOut,
          stopAtState,
          trial,
          forceRebuild,
          sequenceGap >= 0 ? sequenceGap : Sequences.getChangeSequenceGap(cfg),
          autoMigrate);
    }
  }

  private final FileBasedConfig gerritConfig;
  private final FileBasedConfig noteDbConfig;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final Provider<PersonIdent> serverIdent;
  private final AllUsersName allUsers;
  private final GitRepositoryManager repoManager;
  private final NoteDbUpdateManager.Factory updateManagerFactory;
  private final ChangeBundleReader bundleReader;
  private final AllProjectsName allProjects;
  private final ThreadLocalRequestContext requestContext;
  private final InternalUser.Factory userFactory;
  private final ChangeRebuilderImpl rebuilder;
  private final MutableNotesMigration globalNotesMigration;
  private final PrimaryStorageMigrator primaryStorageMigrator;
  private final DynamicSet<NotesMigrationStateListener> listeners;

  private final ListeningExecutorService executor;
  private final ImmutableList<Project.NameKey> projects;
  private final ImmutableList<Change.Id> changes;
  private final OutputStream progressOut;
  private final NotesMigrationState stopAtState;
  private final boolean trial;
  private final boolean forceRebuild;
  private final int sequenceGap;
  private final boolean autoMigrate;

  private NoteDbMigrator(
      SitePaths sitePaths,
      SchemaFactory<ReviewDb> schemaFactory,
      Provider<PersonIdent> serverIdent,
      AllUsersName allUsers,
      GitRepositoryManager repoManager,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeBundleReader bundleReader,
      AllProjectsName allProjects,
      ThreadLocalRequestContext requestContext,
      InternalUser.Factory userFactory,
      ChangeRebuilderImpl rebuilder,
      MutableNotesMigration globalNotesMigration,
      PrimaryStorageMigrator primaryStorageMigrator,
      DynamicSet<NotesMigrationStateListener> listeners,
      ListeningExecutorService executor,
      ImmutableList<Project.NameKey> projects,
      ImmutableList<Change.Id> changes,
      OutputStream progressOut,
      NotesMigrationState stopAtState,
      boolean trial,
      boolean forceRebuild,
      int sequenceGap,
      boolean autoMigrate)
      throws MigrationException {
    if (!changes.isEmpty() && !projects.isEmpty()) {
      throw new MigrationException("Cannot set both changes and projects");
    }
    if (sequenceGap < 0) {
      throw new MigrationException("Sequence gap must be non-negative: " + sequenceGap);
    }

    this.schemaFactory = schemaFactory;
    this.serverIdent = serverIdent;
    this.allUsers = allUsers;
    this.rebuilder = rebuilder;
    this.repoManager = repoManager;
    this.updateManagerFactory = updateManagerFactory;
    this.bundleReader = bundleReader;
    this.allProjects = allProjects;
    this.requestContext = requestContext;
    this.userFactory = userFactory;
    this.globalNotesMigration = globalNotesMigration;
    this.primaryStorageMigrator = primaryStorageMigrator;
    this.listeners = listeners;
    this.executor = executor;
    this.projects = projects;
    this.changes = changes;
    this.progressOut = progressOut;
    this.stopAtState = stopAtState;
    this.trial = trial;
    this.forceRebuild = forceRebuild;
    this.sequenceGap = sequenceGap;
    this.autoMigrate = autoMigrate;

    // Stack notedb.config over gerrit.config, in the same way as GerritServerConfigProvider.
    this.gerritConfig = new FileBasedConfig(sitePaths.gerrit_config.toFile(), FS.detect());
    this.noteDbConfig =
        new FileBasedConfig(gerritConfig, sitePaths.notedb_config.toFile(), FS.detect());
  }

  @Override
  public void close() {
    executor.shutdownNow();
  }

  public void migrate() throws OrmException, IOException {
    if (!changes.isEmpty() || !projects.isEmpty()) {
      throw new MigrationException(
          "Cannot set changes or projects during full migration; call rebuild() instead");
    }
    Optional<NotesMigrationState> maybeState = loadState();
    if (!maybeState.isPresent()) {
      throw new MigrationException("Could not determine initial migration state");
    }

    NotesMigrationState state = maybeState.get();
    if (trial && state.compareTo(READ_WRITE_NO_SEQUENCE) > 0) {
      throw new MigrationException(
          "Migration has already progressed past the endpoint of the \"trial mode\" state;"
              + " NoteDb is already the primary storage for some changes");
    }
    if (forceRebuild && state.compareTo(READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY) > 0) {
      throw new MigrationException(
          "Cannot force rebuild changes; NoteDb is already the primary storage for some changes");
    }
    setControlFlags();

    boolean rebuilt = false;
    while (state.compareTo(NOTE_DB) < 0) {
      if (state.equals(stopAtState)) {
        return;
      }
      boolean stillNeedsRebuild = forceRebuild && !rebuilt;
      if (trial && state.compareTo(READ_WRITE_NO_SEQUENCE) >= 0) {
        if (stillNeedsRebuild && state == READ_WRITE_NO_SEQUENCE) {
          // We're at the end state of trial mode, but still need a rebuild due to forceRebuild. Let
          // the loop go one more time.
        } else {
          return;
        }
      }
      switch (state) {
        case REVIEW_DB:
          state = turnOnWrites(state);
          break;
        case WRITE:
          state = rebuildAndEnableReads(state);
          rebuilt = true;
          break;
        case READ_WRITE_NO_SEQUENCE:
          if (stillNeedsRebuild) {
            state = rebuildAndEnableReads(state);
            rebuilt = true;
          } else {
            state = enableSequences(state);
          }
          break;
        case READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY:
          if (stillNeedsRebuild) {
            state = rebuildAndEnableReads(state);
            rebuilt = true;
          } else {
            state = setNoteDbPrimary(state);
          }
          break;
        case READ_WRITE_WITH_SEQUENCE_NOTE_DB_PRIMARY:
          // The only way we can get here is if there was a failure on a previous run of
          // setNoteDbPrimary, since that method moves to NOTE_DB if it completes
          // successfully. Assume that not all changes were converted and re-run the step.
          // migrateToNoteDbPrimary is a relatively fast no-op for already-migrated changes, so this
          // isn't actually repeating work.
          state = setNoteDbPrimary(state);
          break;
        case NOTE_DB:
          // Done!
          break;
        default:
          throw new MigrationException(
              "Migration out of the following state is not supported:\n" + state.toText());
      }
    }
  }

  private NotesMigrationState turnOnWrites(NotesMigrationState prev) throws IOException {
    return saveState(prev, WRITE);
  }

  private NotesMigrationState rebuildAndEnableReads(NotesMigrationState prev)
      throws OrmException, IOException {
    rebuild();
    return saveState(prev, READ_WRITE_NO_SEQUENCE);
  }

  private NotesMigrationState enableSequences(NotesMigrationState prev)
      throws OrmException, IOException {
    try (ReviewDb db = schemaFactory.open()) {
      @SuppressWarnings("deprecation")
      RepoSequence seq =
          new RepoSequence(
              repoManager,
              GitReferenceUpdated.DISABLED,
              allProjects,
              Sequences.NAME_CHANGES,
              // If sequenceGap is 0, this writes into the sequence ref the same ID that is returned
              // by the call to seq.next() below. If we actually used this as a change ID, that
              // would be a problem, but we just discard it, so this is safe.
              () -> db.nextChangeId() + sequenceGap - 1,
              1);
      seq.next();
    }
    return saveState(prev, READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY);
  }

  private NotesMigrationState setNoteDbPrimary(NotesMigrationState prev)
      throws MigrationException, OrmException, IOException {
    checkState(
        projects.isEmpty() && changes.isEmpty(),
        "Should not have attempted setNoteDbPrimary with a subset of changes");
    checkState(
        prev == READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY
            || prev == READ_WRITE_WITH_SEQUENCE_NOTE_DB_PRIMARY,
        "Unexpected start state for setNoteDbPrimary: %s",
        prev);

    // Before changing the primary storage of old changes, ensure new changes are created with
    // NoteDb primary.
    prev = saveState(prev, READ_WRITE_WITH_SEQUENCE_NOTE_DB_PRIMARY);

    Stopwatch sw = Stopwatch.createStarted();
    log.info("Setting primary storage to NoteDb");
    List<Change.Id> allChanges;
    try (ReviewDb db = unwrapDb(schemaFactory.open())) {
      allChanges = Streams.stream(db.changes().all()).map(Change::getId).collect(toList());
    }

    try (ContextHelper contextHelper = new ContextHelper()) {
      List<ListenableFuture<Boolean>> futures =
          allChanges
              .stream()
              .map(
                  id ->
                      executor.submit(
                          () -> {
                            try (ManualRequestContext ctx = contextHelper.open()) {
                              try {
                                primaryStorageMigrator.migrateToNoteDbPrimary(id);
                              } catch (NoNoteDbStateException e) {
                                if (canSkipPrimaryStorageMigration(
                                    ctx.getReviewDbProvider().get(), id)) {
                                  log.warn(
                                      "Change {} previously failed to rebuild;"
                                          + " skipping primary storage migration",
                                      e);
                                } else {
                                  throw e;
                                }
                              }
                              return true;
                            } catch (Exception e) {
                              log.error("Error migrating primary storage for " + id, e);
                              return false;
                            }
                          }))
              .collect(toList());

      boolean ok = futuresToBoolean(futures, "Error migrating primary storage");
      double t = sw.elapsed(TimeUnit.MILLISECONDS) / 1000d;
      log.info(
          String.format(
              "Migrated primary storage of %d changes in %.01fs (%.01f/s)\n",
              allChanges.size(), t, allChanges.size() / t));
      if (!ok) {
        throw new MigrationException("Migrating primary storage for some changes failed, see log");
      }
    }

    return disableReviewDb(prev);
  }

  /**
   * Checks whether a change is so corrupt that it can be completely skipped by the primary storage
   * migration step.
   *
   * <p>To get to the point where this method is called from {@link #setNoteDbPrimary}, it means we
   * attempted to rebuild it, and encountered an error that was then caught in {@link
   * #rebuildProject} and skipped. As a result, there is no {@code noteDbState} field in the change
   * by the time we get to {@link #setNoteDbPrimary}, so {@code migrateToNoteDbPrimary} throws an
   * exception.
   *
   * <p>We have to do this hacky double-checking because we don't have a way for the rebuilding
   * phase to communicate to the primary storage migration phase that the change is skippable. It
   * would be possible to store this info in some field in this class, but there is no guarantee
   * that the rebuild and primary storage migration phases are run in the same JVM invocation.
   *
   * <p>In an ideal world, we could do this through the {@link
   * com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage} enum, having a separate value
   * for errors. However, that would be an invasive change touching many non-migration-related parts
   * of the NoteDb migration code, which is too risky to attempt in the stable branch where this bug
   * had to be fixed.
   *
   * <p>As of this writing, the only case where this happens is when a change has no patch sets.
   */
  private static boolean canSkipPrimaryStorageMigration(ReviewDb db, Change.Id id) {
    try {
      return Iterables.isEmpty(unwrapDb(db).patchSets().byChange(id));
    } catch (Exception e) {
      log.error("Error checking if change " + id + " can be skipped, assuming no", e);
      return false;
    }
  }

  private NotesMigrationState disableReviewDb(NotesMigrationState prev) throws IOException {
    return saveState(prev, NOTE_DB, c -> setAutoMigrate(c, false));
  }

  private Optional<NotesMigrationState> loadState() throws IOException {
    try {
      gerritConfig.load();
      noteDbConfig.load();
      return NotesMigrationState.forConfig(noteDbConfig);
    } catch (ConfigInvalidException | IllegalArgumentException e) {
      log.warn("error reading NoteDb migration options from " + noteDbConfig.getFile(), e);
      return Optional.empty();
    }
  }

  private NotesMigrationState saveState(
      NotesMigrationState expectedOldState, NotesMigrationState newState) throws IOException {
    return saveState(expectedOldState, newState, c -> {});
  }

  private NotesMigrationState saveState(
      NotesMigrationState expectedOldState,
      NotesMigrationState newState,
      Consumer<Config> additionalUpdates)
      throws IOException {
    synchronized (globalNotesMigration) {
      // This read-modify-write is racy. We're counting on the fact that no other Gerrit operation
      // modifies gerrit.config, and hoping that admins don't either.
      Optional<NotesMigrationState> actualOldState = loadState();
      if (!actualOldState.equals(Optional.of(expectedOldState))) {
        throw new MigrationException(
            "Cannot move to new state:\n"
                + newState.toText()
                + "\n\n"
                + "Expected this state in gerrit.config:\n"
                + expectedOldState.toText()
                + "\n\n"
                + (actualOldState.isPresent()
                    ? "But found this state:\n" + actualOldState.get().toText()
                    : "But could not parse the current state"));
      }

      preStateChange(expectedOldState, newState);

      newState.setConfigValues(noteDbConfig);
      additionalUpdates.accept(noteDbConfig);
      noteDbConfig.save();

      // Only set in-memory state once it's been persisted to storage.
      globalNotesMigration.setFrom(newState);

      return newState;
    }
  }

  private void preStateChange(NotesMigrationState oldState, NotesMigrationState newState)
      throws IOException {
    for (NotesMigrationStateListener listener : listeners) {
      listener.preStateChange(oldState, newState);
    }
  }

  private void setControlFlags() throws MigrationException {
    synchronized (globalNotesMigration) {
      try {
        noteDbConfig.load();
        setAutoMigrate(noteDbConfig, autoMigrate);
        setTrialMode(noteDbConfig, trial);
        noteDbConfig.save();
      } catch (ConfigInvalidException | IOException e) {
        throw new MigrationException("Error saving auto-migration config", e);
      }
    }
  }

  public void rebuild() throws MigrationException, OrmException {
    if (!globalNotesMigration.commitChangeWrites()) {
      throw new MigrationException("Cannot rebuild without noteDb.changes.write=true");
    }
    Stopwatch sw = Stopwatch.createStarted();
    log.info("Rebuilding changes in NoteDb");

    ImmutableListMultimap<Project.NameKey, Change.Id> changesByProject = getChangesByProject();
    List<ListenableFuture<Boolean>> futures = new ArrayList<>();
    try (ContextHelper contextHelper = new ContextHelper()) {
      List<Project.NameKey> projectNames =
          Ordering.usingToString().sortedCopy(changesByProject.keySet());
      for (Project.NameKey project : projectNames) {
        ListenableFuture<Boolean> future =
            executor.submit(
                () -> {
                  try {
                    return rebuildProject(contextHelper.getReviewDb(), changesByProject, project);
                  } catch (Exception e) {
                    log.error("Error rebuilding project " + project, e);
                    return false;
                  }
                });
        futures.add(future);
      }

      boolean ok = futuresToBoolean(futures, "Error rebuilding projects");
      double t = sw.elapsed(TimeUnit.MILLISECONDS) / 1000d;
      log.info(
          String.format(
              "Rebuilt %d changes in %.01fs (%.01f/s)\n",
              changesByProject.size(), t, changesByProject.size() / t));
      if (!ok) {
        throw new MigrationException("Rebuilding some changes failed, see log");
      }
    }
  }

  private ImmutableListMultimap<Project.NameKey, Change.Id> getChangesByProject()
      throws OrmException {
    // Memoize all changes so we can close the db connection and allow other threads to use the full
    // connection pool.
    SetMultimap<Project.NameKey, Change.Id> out =
        MultimapBuilder.treeKeys(comparing(Project.NameKey::get))
            .treeSetValues(comparing(Change.Id::get))
            .build();
    try (ReviewDb db = unwrapDb(schemaFactory.open())) {
      if (!projects.isEmpty()) {
        return byProject(db.changes().all(), c -> projects.contains(c.getProject()), out);
      }
      if (!changes.isEmpty()) {
        return byProject(db.changes().get(changes), c -> true, out);
      }
      return byProject(db.changes().all(), c -> true, out);
    }
  }

  private static ImmutableListMultimap<Project.NameKey, Change.Id> byProject(
      Iterable<Change> changes,
      Predicate<Change> pred,
      SetMultimap<Project.NameKey, Change.Id> out) {
    Streams.stream(changes).filter(pred).forEach(c -> out.put(c.getProject(), c.getId()));
    return ImmutableListMultimap.copyOf(out);
  }

  private static ObjectInserter newPackInserter(Repository repo) {
    if (!(repo instanceof FileRepository)) {
      return repo.newObjectInserter();
    }
    PackInserter ins = ((FileRepository) repo).getObjectDatabase().newPackInserter();
    ins.checkExisting(false);
    return ins;
  }

  private boolean rebuildProject(
      ReviewDb db,
      ImmutableListMultimap<Project.NameKey, Change.Id> allChanges,
      Project.NameKey project) {
    checkArgument(allChanges.containsKey(project));
    boolean ok = true;
    ProgressMonitor pm =
        new TextProgressMonitor(
            new PrintWriter(new BufferedWriter(new OutputStreamWriter(progressOut, UTF_8))));
    try (Repository changeRepo = repoManager.openRepository(project);
        // Only use a PackInserter for the change repo, not All-Users.
        //
        // It's not possible to share a single inserter for All-Users across all project tasks, and
        // we don't want to add one pack per project to All-Users. Adding many loose objects is
        // preferable to many packs.
        //
        // Anyway, the number of objects inserted into All-Users is proportional to the number
        // of pending draft comments, which should not be high (relative to the total number of
        // changes), so the number of loose objects shouldn't be too unreasonable.
        ObjectInserter changeIns = newPackInserter(changeRepo);
        ObjectReader changeReader = changeIns.newReader();
        RevWalk changeRw = new RevWalk(changeReader);
        Repository allUsersRepo = repoManager.openRepository(allUsers);
        ObjectInserter allUsersIns = allUsersRepo.newObjectInserter();
        ObjectReader allUsersReader = allUsersIns.newReader();
        RevWalk allUsersRw = new RevWalk(allUsersReader)) {
      ChainedReceiveCommands changeCmds = new ChainedReceiveCommands(changeRepo);
      ChainedReceiveCommands allUsersCmds = new ChainedReceiveCommands(allUsersRepo);

      Collection<Change.Id> changes = allChanges.get(project);
      pm.beginTask(FormatUtil.elide("Rebuilding " + project.get(), 50), changes.size());
      int toSave = 0;
      try {
        for (Change.Id changeId : changes) {
          // NoteDbUpdateManager assumes that all commands in its OpenRepo were added by itself, so
          // we can't share the top-level ChainedReceiveCommands. Use a new set of commands sharing
          // the same underlying repo, and copy commands back to the top-level
          // ChainedReceiveCommands later. This also assumes that each ref in the final list of
          // commands was only modified by a single NoteDbUpdateManager; since we use one manager
          // per change, and each ref corresponds to exactly one change, this assumption should be
          // safe.
          ChainedReceiveCommands tmpChangeCmds =
              new ChainedReceiveCommands(changeCmds.getRepoRefCache());
          ChainedReceiveCommands tmpAllUsersCmds =
              new ChainedReceiveCommands(allUsersCmds.getRepoRefCache());

          try (NoteDbUpdateManager manager =
              updateManagerFactory
                  .create(project)
                  .setAtomicRefUpdates(false)
                  .setSaveObjects(false)
                  .setChangeRepo(changeRepo, changeRw, changeIns, tmpChangeCmds)
                  .setAllUsersRepo(allUsersRepo, allUsersRw, allUsersIns, tmpAllUsersCmds)) {
            rebuild(db, changeId, manager);

            // Executing with dryRun=true writes all objects to the underlying inserters and adds
            // commands to the ChainedReceiveCommands. Afterwards, we can discard the manager, so we
            // don't keep using any memory beyond what may be buffered in the PackInserter.
            manager.execute(true);

            tmpChangeCmds.getCommands().values().forEach(c -> addCommand(changeCmds, c));
            tmpAllUsersCmds.getCommands().values().forEach(c -> addCommand(allUsersCmds, c));

            toSave++;
          } catch (NoPatchSetsException e) {
            log.warn(e.getMessage());
          } catch (ConflictingUpdateException ex) {
            log.warn(
                "Rebuilding detected a conflicting ReviewDb update for change {};"
                    + " will be auto-rebuilt at runtime",
                changeId);
          } catch (Throwable t) {
            log.error("Failed to rebuild change " + changeId, t);
            ok = false;
          }
          pm.update(1);
        }
      } finally {
        pm.endTask();
      }

      pm.beginTask(FormatUtil.elide("Saving " + project.get(), 50), ProgressMonitor.UNKNOWN);
      try {
        save(changeRepo, changeRw, changeIns, changeCmds);
        save(allUsersRepo, allUsersRw, allUsersIns, allUsersCmds);
        // This isn't really useful progress. If we passed a real ProgressMonitor to
        // BatchRefUpdate#execute we might get something more incremental, but that doesn't allow us
        // to specify the repo name in the task text.
        pm.update(toSave);
      } catch (LockFailureException e) {
        log.warn(
            "Rebuilding detected a conflicting NoteDb update for the following refs, which will"
                + " be auto-rebuilt at runtime: {}",
            e.getFailedRefs().stream().distinct().sorted().collect(joining(", ")));
      } catch (IOException e) {
        log.error("Failed to save NoteDb state for " + project, e);
      } finally {
        pm.endTask();
      }
    } catch (RepositoryNotFoundException e) {
      log.warn("Repository {} not found", project);
    } catch (IOException e) {
      log.error("Failed to rebuild project " + project, e);
    }
    return ok;
  }

  private void rebuild(ReviewDb db, Change.Id changeId, NoteDbUpdateManager manager)
      throws OrmException, IOException {
    // Match ChangeRebuilderImpl#stage, but without calling manager.stage(), since that can only be
    // called after building updates for all changes.
    Change change =
        ChangeRebuilderImpl.checkNoteDbState(ChangeNotes.readOneReviewDbChange(db, changeId));
    if (change == null) {
      // Could log here instead, but this matches the behavior of ChangeRebuilderImpl#stage.
      throw new NoSuchChangeException(changeId);
    }
    rebuilder.buildUpdates(manager, bundleReader.fromReviewDb(db, changeId));

    rebuilder.execute(db, changeId, manager, true, false);
  }

  private static void addCommand(ChainedReceiveCommands cmds, ReceiveCommand cmd) {
    // ChainedReceiveCommands doesn't allow no-ops, but these occur when rebuilding a
    // previously-rebuilt change.
    if (!cmd.getOldId().equals(cmd.getNewId())) {
      cmds.add(cmd);
    }
  }

  private void save(Repository repo, RevWalk rw, ObjectInserter ins, ChainedReceiveCommands cmds)
      throws IOException {
    if (cmds.isEmpty()) {
      return;
    }
    ins.flush();
    BatchRefUpdate bru = repo.getRefDatabase().newBatchUpdate();
    bru.setRefLogMessage("Migrate changes to NoteDb", false);
    bru.setRefLogIdent(serverIdent.get());
    bru.setAtomic(false);
    bru.setAllowNonFastForwards(true);
    cmds.addTo(bru);
    RefUpdateUtil.executeChecked(bru, rw);
  }

  private static boolean futuresToBoolean(List<ListenableFuture<Boolean>> futures, String errMsg) {
    try {
      return Futures.allAsList(futures).get().stream().allMatch(b -> b);
    } catch (InterruptedException | ExecutionException e) {
      log.error(errMsg, e);
      return false;
    }
  }

  private class ContextHelper implements AutoCloseable {
    private final Thread callingThread;
    private ReviewDb db;
    private Runnable closeDb;

    ContextHelper() {
      callingThread = Thread.currentThread();
    }

    ManualRequestContext open() throws OrmException {
      return new ManualRequestContext(
          userFactory.create(),
          // Reuse the same lazily-opened ReviewDb on the original calling thread, otherwise open
          // SchemaFactory in the normal way.
          Thread.currentThread().equals(callingThread) ? this::getReviewDb : schemaFactory,
          requestContext);
    }

    synchronized ReviewDb getReviewDb() throws OrmException {
      if (db == null) {
        ReviewDb actual = schemaFactory.open();
        closeDb = actual::close;
        db =
            new ReviewDbWrapper(unwrapDb(actual)) {
              @Override
              public void close() {
                // Closed by ContextHelper#close.
              }
            };
      }
      return db;
    }

    @Override
    public synchronized void close() {
      if (db != null) {
        closeDb.run();
        db = null;
        closeDb = null;
      }
    }
  }
}
