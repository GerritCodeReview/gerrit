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

package com.google.gerrit.server;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.auto.value.AutoValue;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * This class can be used to clean zombie draft comments. More context in <a
 * href="https://gerrit-review.googlesource.com/c/gerrit/+/246233">
 * https://gerrit-review.googlesource.com/c/gerrit/+/246233 </a>
 *
 * <p>The implementation has two cases for detecting zombie drafts:
 *
 * <ul>
 *   <li>An earlier bug in the deletion of draft comments caused some draft refs to remain empty but
 *       not get deleted.
 *   <li>Inspecting all draft-comments. Check for each draft if there exists a published comment
 *       with the same UUID. These comments are called zombie drafts. If the program is run in
 *       {@link DeleteZombieComments#dryRun} mode, the zombie draft IDs will only be logged for
 *       tracking, otherwise they will also be deleted.
 * </uL>
 */
public abstract class DeleteZombieComments<KeyT> implements AutoCloseable {
  @AutoValue
  abstract static class ChangeUserIDsPair {
    abstract Change.Id changeId();

    abstract Account.Id accountId();

    static ChangeUserIDsPair create(Change.Id changeId, Account.Id accountId) {
      return new AutoValue_DeleteZombieComments_ChangeUserIDsPair(changeId, accountId);
    }
  }

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final int cleanupPercentage;
  protected final boolean dryRun;
  @Nullable private final Consumer<String> uiConsumer;
  @Nullable private final GitRepositoryManager repoManager;
  @Nullable private final DraftCommentsReader draftCommentsReader;
  @Nullable private final ChangeNotes.Factory changeNotesFactory;
  @Nullable private final CommentsUtil commentsUtil;

  private Map<Change.Id, Project.NameKey> changeProjectMap = new HashMap<>();
  private Map<Change.Id, ChangeNotes> changeNotes = new HashMap<>();

  protected DeleteZombieComments(
      Integer cleanupPercentage,
      boolean dryRun,
      Consumer<String> uiConsumer,
      GitRepositoryManager repoManager,
      DraftCommentsReader draftCommentsReader,
      ChangeNotes.Factory changeNotesFactory,
      CommentsUtil commentsUtil) {
    this.cleanupPercentage = cleanupPercentage == null ? 100 : cleanupPercentage;
    this.dryRun = dryRun;
    this.uiConsumer = uiConsumer;
    this.repoManager = repoManager;
    this.draftCommentsReader = draftCommentsReader;
    this.changeNotesFactory = changeNotesFactory;
    this.commentsUtil = commentsUtil;
  }

  public void execute() throws IOException {
    setup();
    List<KeyT> emptyDrafts = filterByCleanupPercentage(listEmptyDrafts(), "empty");
    ListMultimap<KeyT, HumanComment> alreadyPublished = listDraftCommentsThatAreAlsoPublished();
    if (dryRun) {
      logInfo(
          String.format(
              "Running in dry run mode. Skipping deletion."
                  + "\nStats (with %d cleanup-percentage):"
                  + "\nEmpty drafts = %d"
                  + "\nAlready published drafts (zombies) = %d",
              cleanupPercentage, emptyDrafts.size(), alreadyPublished.size()));
    } else {
      deleteEmptyDraftsByKey(emptyDrafts);
      deleteZombieDrafts(alreadyPublished);
    }
  }

  @VisibleForTesting
  public abstract void setup() throws IOException;

  @Override
  public abstract void close() throws IOException;

  protected abstract List<KeyT> listAllDrafts() throws IOException;

  protected abstract List<KeyT> listEmptyDrafts() throws IOException;

  protected abstract void deleteEmptyDraftsByKey(Collection<KeyT> keys) throws IOException;

  protected abstract void deleteZombieDrafts(ListMultimap<KeyT, HumanComment> drafts)
      throws IOException;

  protected abstract Change.Id getChangeId(KeyT key);

  protected abstract Account.Id getAccountId(KeyT key);

  protected abstract String loggable(KeyT key);

  protected ChangeNotes getChangeNotes(Change.Id changeId) {
    if (changeNotes.containsKey(changeId)) {
      return changeNotes.get(changeId);
    }
    ChangeNotes notes = changeNotesFactory.createChecked(changeProjectMap.get(changeId), changeId);
    changeNotes.put(changeId, notes);
    return notes;
  }

  private List<KeyT> filterByCleanupPercentage(List<KeyT> drafts, String reason) {
    if (cleanupPercentage >= 100) {
      logInfo(
          String.format(
              "Cleanup percentage = %d" + "\nNumber of drafts to be cleaned for %s = %d",
              cleanupPercentage, reason, drafts.size()));
      return drafts;
    }
    ImmutableList<KeyT> res =
        drafts.stream()
            .filter(key -> getChangeId(key).get() % 100 < cleanupPercentage)
            .collect(toImmutableList());
    logInfo(
        String.format(
            "Cleanup percentage = %d"
                + "\nOriginal number of drafts for %s = %d"
                + "\nNumber of drafts to be processed for %s = %d",
            cleanupPercentage, reason, drafts.size(), reason, res.size()));
    return res;
  }

  @VisibleForTesting
  public ListMultimap<KeyT, HumanComment> listDraftCommentsThatAreAlsoPublished()
      throws IOException {
    List<KeyT> draftKeys = filterByCleanupPercentage(listAllDrafts(), "all-drafts");
    changeProjectMap.putAll(mapChangesWithDraftsToProjects(draftKeys));

    ListMultimap<KeyT, HumanComment> zombieDrafts = ArrayListMultimap.create();
    Set<ChangeUserIDsPair> visitedSet = new HashSet<>();
    for (KeyT key : draftKeys) {
      try {
        Change.Id changeId = getChangeId(key);
        Account.Id accountId = getAccountId(key);
        ChangeUserIDsPair changeUserIDsPair = ChangeUserIDsPair.create(changeId, accountId);
        if (!visitedSet.add(changeUserIDsPair)) {
          continue;
        }
        if (!changeProjectMap.containsKey(changeId)) {
          logger.atWarning().log(
              "Could not find a project associated with change ID %s. Skipping draft [%s]",
              changeId, loggable(key));
          continue;
        }
        List<HumanComment> drafts =
            draftCommentsReader.getDraftsByChangeAndDraftAuthor(changeId, accountId);
        ChangeNotes notes =
            changeNotesFactory.createChecked(changeProjectMap.get(changeId), changeId);
        List<HumanComment> published = commentsUtil.publishedHumanCommentsByChange(notes);
        Set<String> publishedIds = toUuid(published);
        ImmutableList<HumanComment> zombieDraftsForChangeAndAuthor =
            drafts.stream()
                .filter(draft -> publishedIds.contains(draft.key.uuid))
                .collect(toImmutableList());
        zombieDraftsForChangeAndAuthor.forEach(
            zombieDraft ->
                logger.atWarning().log(
                    "Draft comment with uuid '%s' of change %s, account %s, written on %s,"
                        + " is a zombie draft that is already published.",
                    zombieDraft.key.uuid, changeId, accountId, zombieDraft.writtenOn));
        zombieDrafts.putAll(key, zombieDraftsForChangeAndAuthor);
      } catch (RuntimeException e) {
        logger.atWarning().withCause(e).log("Failed to process draft [%s]", loggable(key));
      }
    }

    if (!zombieDrafts.isEmpty()) {
      Timestamp earliestZombieTs = null;
      Timestamp latestZombieTs = null;
      for (HumanComment zombieDraft : zombieDrafts.values()) {
        earliestZombieTs = getEarlierTs(earliestZombieTs, zombieDraft.writtenOn);
        latestZombieTs = getLaterTs(latestZombieTs, zombieDraft.writtenOn);
      }
      logger.atWarning().log(
          "Detected %d zombie drafts that were already published (earliest at %s, latest at %s).",
          zombieDrafts.size(), earliestZombieTs, latestZombieTs);
    }
    return zombieDrafts;
  }

  /**
   * Map each change ID to its associated project.
   *
   * <p>When doing a ref scan of draft refs
   * "refs/draft-comments/$change_id_short/$change_id/$user_id" we don't know which project this
   * draft comment is associated with. The project name is needed to load published comments for the
   * change, hence we map each change ID to its project here by scanning through the change meta ref
   * of the change ID in all projects.
   */
  private Map<Change.Id, Project.NameKey> mapChangesWithDraftsToProjects(List<KeyT> drafts) {
    ImmutableSet<Change.Id> changeIds =
        drafts.stream().map(key -> getChangeId(key)).collect(ImmutableSet.toImmutableSet());
    Map<Change.Id, Project.NameKey> result = new HashMap<>();
    for (Project.NameKey project : repoManager.list()) {
      try (Repository repo = repoManager.openRepository(project)) {
        Sets.SetView<Change.Id> unmappedChangeIds = Sets.difference(changeIds, result.keySet());
        for (Change.Id changeId : unmappedChangeIds) {
          Ref ref = repo.getRefDatabase().exactRef(RefNames.changeMetaRef(changeId));
          if (ref != null) {
            result.put(changeId, project);
          }
        }
      } catch (Exception e) {
        logger.atWarning().withCause(e).log("Failed to open repository for project '%s'.", project);
      }
      if (changeIds.size() == result.size()) {
        // We do not need to scan the remaining repositories
        break;
      }
    }
    if (result.size() != changeIds.size()) {
      logger.atWarning().log(
          "Failed to associate the following change Ids to a project: %s",
          Sets.difference(changeIds, result.keySet()));
    }
    return result;
  }

  protected void logInfo(String message) {
    logger.atInfo().log("%s", message);
    uiConsumer.accept(message);
  }

  /** Map the list of input comments to their UUIDs. */
  private Set<String> toUuid(List<HumanComment> in) {
    return in.stream().map(c -> c.key.uuid).collect(toImmutableSet());
  }

  private Timestamp getEarlierTs(@Nullable Timestamp t1, Timestamp t2) {
    if (t1 == null) {
      return t2;
    }
    return t1.before(t2) ? t1 : t2;
  }

  private Timestamp getLaterTs(@Nullable Timestamp t1, Timestamp t2) {
    if (t1 == null) {
      return t2;
    }
    return t1.after(t2) ? t1 : t2;
  }
}
