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

package com.google.gerrit.server.account.externalids;

import com.google.common.base.CharMatcher;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

/** Loads cache values for the external ID cache using either a full or a partial reload. */
@Singleton
public class ExternalIdCacheLoader {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Maximum number of prior states we inspect to find a base for differential. If no cached state
  // is found within this number of parents, we fall back to reading everything from scratch.
  private static final int MAX_HISTORY_LOOKBACK = 10;

  private final ExternalIdReader externalIdReader;
  private final Cache<ObjectId, AllExternalIds> externalIdCache;
  private final GitRepositoryManager gitRepositoryManager;
  private final AllUsersName allUsersName;
  private final Counter1<Boolean> reloadCounter;
  private final Timer0 reloadDifferential;
  private final boolean isPersistentCache;
  private final ExternalIdFactory externalIdFactory;

  @Inject
  ExternalIdCacheLoader(
      GitRepositoryManager gitRepositoryManager,
      AllUsersName allUsersName,
      ExternalIdReader externalIdReader,
      @Named(ExternalIdCacheImpl.CACHE_NAME) Cache<ObjectId, AllExternalIds> externalIdCache,
      MetricMaker metricMaker,
      @GerritServerConfig Config config,
      ExternalIdFactory externalIdFactory) {
    this.externalIdReader = externalIdReader;
    this.externalIdCache = externalIdCache;
    this.gitRepositoryManager = gitRepositoryManager;
    this.allUsersName = allUsersName;
    this.reloadCounter =
        metricMaker.newCounter(
            "notedb/external_id_cache_load_count",
            new Description("Total number of external ID cache reloads from Git.")
                .setRate()
                .setUnit("updates"),
            Field.ofBoolean("partial", Metadata.Builder::partial)
                .description("Whether the reload was partial.")
                .build());
    this.reloadDifferential =
        metricMaker.newTimer(
            "notedb/external_id_partial_read_latency",
            new Description(
                    "Latency for generating a new external ID cache state from a prior state.")
                .setCumulative()
                .setUnit(Units.MILLISECONDS));
    this.isPersistentCache =
        config.getInt("cache", ExternalIdCacheImpl.CACHE_NAME, "diskLimit", 0) > 0;
    this.externalIdFactory = externalIdFactory;
  }

  public AllExternalIds load(ObjectId notesRev) throws IOException, ConfigInvalidException {
    externalIdReader.checkReadEnabled();
    // The requested value was not in the cache (hence, this loader was invoked). Therefore, try to
    // create this entry from a past value using the minimal amount of Git operations possible to
    // reduce latency.
    //
    // First, try to find the most recent state we have in the cache. Most of the time, this will be
    // the state before the last update happened, but it can also date further back. We try a best
    // effort approach and check the last 10 states. If nothing is found, we default to loading the
    // value from scratch.
    //
    // If a prior state was found, we use Git to diff the trees and find modifications. This is
    // faster than just loading the complete current tree and working off of that because of how the
    // data is structured: NotesMaps use nested trees, so, for example, a NotesMap with 200k entries
    // has two layers of nesting: 12/34/1234..99. TreeWalk is smart in skipping the traversal of
    // identical subtrees.
    //
    // Once we know what files changed, we apply additions and removals to the previously cached
    // state.

    try (Repository repo = gitRepositoryManager.openRepository(allUsersName);
        RevWalk rw = new RevWalk(repo)) {
      long start = System.nanoTime();
      Ref extIdRef = repo.exactRef(RefNames.REFS_EXTERNAL_IDS);
      if (extIdRef == null) {
        logger.atInfo().log(
            RefNames.REFS_EXTERNAL_IDS + " not initialized, falling back to full reload.");
        return reloadAllExternalIds(notesRev);
      }

      RevCommit currentCommit = rw.parseCommit(extIdRef.getObjectId());
      rw.markStart(currentCommit);
      RevCommit parentWithCacheValue;
      AllExternalIds oldExternalIds = null;
      int i = 0;
      while ((parentWithCacheValue = rw.next()) != null
          && i++ < MAX_HISTORY_LOOKBACK
          && parentWithCacheValue.getParentCount() < 2) {
        oldExternalIds = externalIdCache.getIfPresent(parentWithCacheValue.getId());
        if (oldExternalIds != null) {
          // We found a previously cached state.
          break;
        }
      }
      if (oldExternalIds == null) {
        if (isPersistentCache) {
          // If there is no persistence, this is normal. Don't upset admins reading the logs.
          logger.atWarning().log(
              "Unable to find an old ExternalId cache state, falling back to full reload");
        }
        return reloadAllExternalIds(notesRev);
      }

      // Diff trees to recognize modifications
      Set<ObjectId> removals = new HashSet<>(); // Set<Blob-Object-Id>
      Map<ObjectId, ObjectId> additions = new HashMap<>(); // Map<Name-ObjectId, Blob-Object-Id>
      try (TreeWalk treeWalk = new TreeWalk(repo)) {
        treeWalk.setFilter(TreeFilter.ANY_DIFF);
        treeWalk.setRecursive(true);
        treeWalk.reset(parentWithCacheValue.getTree(), currentCommit.getTree());
        while (treeWalk.next()) {
          String path = treeWalk.getPathString();
          ObjectId oldBlob = treeWalk.getObjectId(0);
          ObjectId newBlob = treeWalk.getObjectId(1);
          if (ObjectId.zeroId().equals(newBlob)) {
            // Deletion
            removals.add(oldBlob);
          } else if (ObjectId.zeroId().equals(oldBlob)) {
            // Addition
            additions.put(fileNameToObjectId(path), newBlob);
          } else {
            // Modification
            removals.add(oldBlob);
            additions.put(fileNameToObjectId(path), newBlob);
          }
        }
      }

      AllExternalIds allExternalIds;
      try {
        allExternalIds = buildAllExternalIds(repo, oldExternalIds, additions, removals);
      } catch (IllegalArgumentException e) {
        Set<String> additionKeys =
            additions.keySet().stream().map(AnyObjectId::getName).collect(Collectors.toSet());
        logger.atSevere().withCause(e).log(
            "Failed to load external ID cache. Repository ref is %s, cache ref is %s, additions are %s",
            extIdRef.getObjectId().getName(), parentWithCacheValue.getId().getName(), additionKeys);
        throw e;
      }
      reloadCounter.increment(true);
      reloadDifferential.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
      return allExternalIds;
    }
  }

  private static ObjectId fileNameToObjectId(String path) {
    return ObjectId.fromString(CharMatcher.is('/').removeFrom(path));
  }

  /**
   * Build a new {@link AllExternalIds} from an old state by applying additions and removals that
   * were performed since then.
   *
   * <p>Removals are applied before additions.
   *
   * @param repo open repository
   * @param oldExternalIds prior state that is used as base
   * @param additions map of name to blob ID for each external ID that should be added
   * @param removals set of name {@link ObjectId}s that should be removed
   */
  private AllExternalIds buildAllExternalIds(
      Repository repo,
      AllExternalIds oldExternalIds,
      Map<ObjectId, ObjectId> additions,
      Set<ObjectId> removals)
      throws IOException {
    ImmutableMap.Builder<ExternalId.Key, ExternalId> byKey = ImmutableMap.builder();
    ImmutableSetMultimap.Builder<Account.Id, ExternalId> byAccount = ImmutableSetMultimap.builder();
    ImmutableSetMultimap.Builder<String, ExternalId> byEmail = ImmutableSetMultimap.builder();

    // Copy over old ExternalIds but exclude deleted ones
    for (ExternalId externalId : oldExternalIds.byAccount().values()) {
      if (removals.contains(externalId.blobId())) {
        continue;
      }

      byKey.put(externalId.key(), externalId);
      byAccount.put(externalId.accountId(), externalId);
      if (externalId.email() != null) {
        byEmail.put(externalId.email(), externalId);
      }
    }

    // Add newly discovered ExternalIds
    try (ObjectReader reader = repo.newObjectReader()) {
      for (Map.Entry<ObjectId, ObjectId> nameToBlob : additions.entrySet()) {
        ExternalId parsedExternalId;
        try {
          parsedExternalId =
              externalIdFactory.parse(
                  nameToBlob.getKey().name(),
                  reader.open(nameToBlob.getValue()).getCachedBytes(),
                  nameToBlob.getValue());
        } catch (ConfigInvalidException | RuntimeException e) {
          logger.atSevere().withCause(e).log(
              "Ignoring invalid external ID note %s", nameToBlob.getKey().name());
          continue;
        }

        byKey.put(parsedExternalId.key(), parsedExternalId);
        byAccount.put(parsedExternalId.accountId(), parsedExternalId);
        if (parsedExternalId.email() != null) {
          byEmail.put(parsedExternalId.email(), parsedExternalId);
        }
      }
    }
    return new AutoValue_AllExternalIds(byKey.build(), byAccount.build(), byEmail.build());
  }

  private AllExternalIds reloadAllExternalIds(ObjectId notesRev)
      throws IOException, ConfigInvalidException {
    try (TraceTimer ignored =
        TraceContext.newTimer(
            "Loading external IDs from scratch",
            Metadata.builder().revision(notesRev.name()).build())) {
      ImmutableSet<ExternalId> externalIds = externalIdReader.all(notesRev);
      externalIds.forEach(ExternalId::checkThatBlobIdIsSet);
      AllExternalIds allExternalIds = AllExternalIds.create(externalIds.stream());
      reloadCounter.increment(false);
      return allExternalIds;
    }
  }
}
