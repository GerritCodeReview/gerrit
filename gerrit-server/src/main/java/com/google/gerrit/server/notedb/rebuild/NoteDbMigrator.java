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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparing;

import com.google.common.base.Predicates;
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
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.notedb.ChangeBundleReader;
import com.google.gerrit.server.notedb.ConfigNotesMigration;
import com.google.gerrit.server.notedb.NoteDbUpdateManager;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.NotesMigrationState;
import com.google.gerrit.server.notedb.rebuild.ChangeRebuilder.NoPatchSetsException;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
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
import java.util.function.Predicate;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** One stop shop for migrating a site's change storage from ReviewDb to NoteDb. */
public class NoteDbMigrator implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(NoteDbMigrator.class);

  private final FileBasedConfig gerritConfig;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final NoteDbUpdateManager.Factory updateManagerFactory;
  private final ChangeRebuilder rebuilder;
  private final ChangeBundleReader bundleReader;
  private final WorkQueue workQueue;
  private final NotesMigration globalNotesMigration;

  private ListeningExecutorService executor;
  private ImmutableList<Project.NameKey> projects = ImmutableList.of();
  private ImmutableList<Change.Id> changes = ImmutableList.of();
  private OutputStream progressOut = NullOutputStream.INSTANCE;
  private boolean trial;
  private boolean forceRebuild;

  private boolean started = false;

  @Inject
  NoteDbMigrator(
      SitePaths sitePaths,
      SchemaFactory<ReviewDb> schemaFactory,
      NoteDbUpdateManager.Factory updateManagerFactory,
      ChangeRebuilder rebuilder,
      ChangeBundleReader bundleReader,
      WorkQueue workQueue,
      NotesMigration globalNotesMigration) {
    this.schemaFactory = schemaFactory;
    this.updateManagerFactory = updateManagerFactory;
    this.rebuilder = rebuilder;
    this.bundleReader = bundleReader;
    this.workQueue = workQueue;
    this.globalNotesMigration = globalNotesMigration;
    this.gerritConfig = new FileBasedConfig(sitePaths.gerrit_config.toFile(), FS.detect());
  }

  /**
   * Set the number of threads used by parallelizable phases of the migration, such as rebuilding
   * all changes.
   *
   * <p>Not all phases are parallelizable, and calling {@link #rebuild()} directly will do
   * substantial work in the calling thread regardless of the number of threads configured.
   *
   * @param threads thread count; if less than 1, all work happens in the calling thread.
   * @return this.
   */
  public NoteDbMigrator setThreads(int threads) {
    executor =
        threads > 1
            ? MoreExecutors.listeningDecorator(workQueue.createQueue(threads, "RebuildChange"))
            : MoreExecutors.newDirectExecutorService();
    return this;
  }

  /**
   * Limit the set of projects that are processed.
   *
   * <p>Incompatible with {@link #setChanges(Collection)}.
   *
   * @param projects set of projects; if null or empty, all projects will be processed.
   * @return this.
   */
  public NoteDbMigrator setProjects(@Nullable Collection<Project.NameKey> projects) {
    this.projects = projects != null ? ImmutableList.copyOf(projects) : ImmutableList.of();
    return this;
  }

  /**
   * Limit the set of changes that are processed.
   *
   * <p>Incompatible with {@link #setChanges(Collection)}.
   *
   * @param changes set of changes; if null or empty, all changes will be processed.
   * @return this.
   */
  public NoteDbMigrator setChanges(@Nullable Collection<Change.Id> changes) {
    this.changes = changes != null ? ImmutableList.copyOf(changes) : ImmutableList.of();
    return this;
  }

  /**
   * Set output stream for progress monitors.
   *
   * <p>Defaults to no progress monitor output (although there may be other logs).
   *
   * @param progressOut output stream.
   * @return this.
   */
  public NoteDbMigrator setProgressOut(OutputStream progressOut) {
    this.progressOut = checkNotNull(progressOut);
    return this;
  }

  /**
   * Rebuild in "trial mode": configure Gerrit to write to and read from NoteDb, but leave ReviewDb
   * as the source of truth for all changes.
   *
   * @param trial whether to rebuild in trial mode.
   * @return this.
   */
  public NoteDbMigrator setTrialMode(boolean trial) {
    this.trial = trial;
    return this;
  }

  /**
   * Rebuild all changes in NoteDb from ReviewDb, even if Gerrit is currently configured to read
   * from NoteDb.
   *
   * <p>Only supported if ReviewDb is still the source of truth for all changes.
   *
   * @param forceRebuild whether to force rebuilding.
   * @return this.
   */
  public NoteDbMigrator setForceRebuild(boolean forceRebuild) {
    this.forceRebuild = forceRebuild;
    return this;
  }

  @Override
  public void close() {
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  public void migrate() throws OrmException, IOException {
    checkAutoRebuildPreconditions();
    Optional<NotesMigrationState> maybeState = loadState();
    if (!maybeState.isPresent()) {
      throw new MigrationException("Could not determine initial migration state");
    }

    NotesMigrationState state = maybeState.get();
    if (trial && state.compareTo(NotesMigrationState.READ_WRITE_NO_SEQUENCE) > 0) {
      throw new MigrationException(
          "Migration has already progressed past the endpoint of the \"trial mode\" state;"
              + " NoteDb is already the primary storage for some changes");
    }

    boolean rebuilt = false;
    while (state.compareTo(NotesMigrationState.NOTE_DB_UNFUSED) < 0) {
      if (trial && state.compareTo(NotesMigrationState.READ_WRITE_NO_SEQUENCE) >= 0) {
        return;
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
          if (forceRebuild && !rebuilt) {
            state = rebuildAndEnableReads(state);
            rebuilt = true;
          }
          state = enableSequences();
          break;
        case READ_WRITE_WITH_SEQUENCE_REVIEW_DB_PRIMARY:
          if (forceRebuild && !rebuilt) {
            state = rebuildAndEnableReads(state);
            rebuilt = true;
          }
          state = setNoteDbPrimary();
          break;
        case READ_WRITE_WITH_SEQUENCE_NOTE_DB_PRIMARY:
          state = disableReviewDb();
          break;
        case NOTE_DB_UNFUSED:
          // Done!
          break;
        case NOTE_DB:
          // TODO(dborowitz): Allow this state once FileRepository supports fused updates.
          // Until then, fallthrough and throw.
        default:
          throw new MigrationException(
              "Migration out of the following state is not supported:\n" + state.toText());
      }
    }
  }

  private NotesMigrationState turnOnWrites(NotesMigrationState prev) throws IOException {
    return saveState(prev, NotesMigrationState.WRITE);
  }

  private NotesMigrationState rebuildAndEnableReads(NotesMigrationState prev)
      throws OrmException, IOException {
    rebuild();
    return saveState(prev, NotesMigrationState.READ_WRITE_NO_SEQUENCE);
  }

  private NotesMigrationState enableSequences() {
    throw new UnsupportedOperationException("not yet implemented");
  }

  private NotesMigrationState setNoteDbPrimary() {
    throw new UnsupportedOperationException("not yet implemented");
  }

  private NotesMigrationState disableReviewDb() {
    throw new UnsupportedOperationException("not yet implemented");
  }

  private Optional<NotesMigrationState> loadState() throws IOException {
    try {
      gerritConfig.load();
      return NotesMigrationState.forNotesMigration(new ConfigNotesMigration(gerritConfig));
    } catch (ConfigInvalidException | IllegalArgumentException e) {
      log.warn("error reading NoteDb migration options from " + gerritConfig.getFile(), e);
      return Optional.empty();
    }
  }

  private NotesMigrationState saveState(
      NotesMigrationState expectedOldState, NotesMigrationState newState) throws IOException {
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
      ConfigNotesMigration.setConfigValues(gerritConfig, newState.migration());
      gerritConfig.save();

      // Only set in-memory state once it's been persisted to storage.
      globalNotesMigration.setFrom(newState.migration());

      return newState;
    }
  }

  public void rebuild() throws MigrationException, OrmException {
    checkRebuildPreconditions();
    started = true;
    if (executor == null) {
      setThreads(0);
    }

    boolean ok;
    Stopwatch sw = Stopwatch.createStarted();
    log.info("Rebuilding changes in NoteDb");

    List<ListenableFuture<Boolean>> futures = new ArrayList<>();
    ImmutableListMultimap<Project.NameKey, Change.Id> changesByProject = getChangesByProject();
    List<Project.NameKey> projectNames =
        Ordering.usingToString().sortedCopy(changesByProject.keySet());
    for (Project.NameKey project : projectNames) {
      ListenableFuture<Boolean> future =
          executor.submit(
              () -> {
                try (ReviewDb db = unwrapDb(schemaFactory.open())) {
                  return rebuildProject(db, changesByProject, project);
                } catch (Exception e) {
                  log.error("Error rebuilding project " + project, e);
                  return false;
                }
              });
      futures.add(future);
    }

    try {
      ok = Iterables.all(Futures.allAsList(futures).get(), Predicates.equalTo(true));
    } catch (InterruptedException | ExecutionException e) {
      log.error("Error rebuilding projects", e);
      ok = false;
    }

    double t = sw.elapsed(TimeUnit.MILLISECONDS) / 1000d;
    log.info(
        String.format(
            "Rebuilt %d changes in %.01fs (%.01f/s)\n",
            changesByProject.size(), t, changesByProject.size() / t));
    if (!ok) {
      throw new MigrationException("Rebuilding some changes failed, see log");
    }
  }

  private void checkPreconditions() {
    checkState(!started, "%s may only be used once", getClass().getSimpleName());
  }

  private void checkAutoRebuildPreconditions() {
    checkPreconditions();
    checkState(
        changes.isEmpty() && projects.isEmpty(),
        "cannot set changes or projects during auto-migration; call rebuild() instead");
  }

  private void checkRebuildPreconditions() {
    checkPreconditions();
    boolean hasChanges = !changes.isEmpty();
    boolean hasProjects = !projects.isEmpty();
    checkState(!(hasChanges && hasProjects), "cannot set both changes and projects");
  }

  private ImmutableListMultimap<Project.NameKey, Change.Id> getChangesByProject()
      throws OrmException {
    // Memoize all changes so we can close the db connection and allow other threads to use the full
    // connection pool.
    try (ReviewDb db = unwrapDb(schemaFactory.open())) {
      SetMultimap<Project.NameKey, Change.Id> out =
          MultimapBuilder.treeKeys(comparing(Project.NameKey::get))
              .treeSetValues(comparing(Change.Id::get))
              .build();
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

  private boolean rebuildProject(
      ReviewDb db,
      ImmutableListMultimap<Project.NameKey, Change.Id> allChanges,
      Project.NameKey project)
      throws IOException, OrmException {
    checkArgument(allChanges.containsKey(project));
    boolean ok = true;
    ProgressMonitor pm =
        new TextProgressMonitor(
            new PrintWriter(new BufferedWriter(new OutputStreamWriter(progressOut, UTF_8))));
    pm.beginTask(FormatUtil.elide(project.get(), 50), allChanges.get(project).size());
    try (NoteDbUpdateManager manager = updateManagerFactory.create(project)) {
      for (Change.Id changeId : allChanges.get(project)) {
        try {
          rebuilder.buildUpdates(manager, bundleReader.fromReviewDb(db, changeId));
        } catch (NoPatchSetsException e) {
          log.warn(e.getMessage());
        } catch (Throwable t) {
          log.error("Failed to rebuild change " + changeId, t);
          ok = false;
        }
        pm.update(1);
      }
      manager.execute();
    } finally {
      pm.endTask();
    }
    return ok;
  }
}
