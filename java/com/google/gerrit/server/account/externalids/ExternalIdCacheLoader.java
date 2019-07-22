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
import com.google.common.cache.CacheLoader;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.metrics.Counter1;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.Description.Units;
import com.google.gerrit.metrics.Field;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.metrics.Timer0;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/** Loads cache values for the external ID cache using either a full or a partial reload. */
@Singleton
public class ExternalIdCacheLoader extends CacheLoader<ObjectId, AllExternalIds> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  // Maximum number of prior states we inspect to find a base for differential. If no cached state
  // is found within this number of parents, we fall back to reading everything from scratch.
  private static final int MAX_HISTORY_LOOKBACK = 10;
  // Maximum number of changes we perform using the differential approach. If more updates need to
  // be applied, we fall back to reading everything from scratch.
  private static final int MAX_DIFF_UPDATES = 50;

  private final ExternalIdReader externalIdReader;
  private final Provider<Cache<ObjectId, AllExternalIds>> externalIdCache;
  private final GitRepositoryManager gitRepositoryManager;
  private final AllUsersName allUsersName;
  private final Counter1<Boolean> reloadCounter;
  private final Timer0 reloadDifferential;
  private final boolean enablePartialReloads;

  @Inject
  ExternalIdCacheLoader(
      GitRepositoryManager gitRepositoryManager,
      AllUsersName allUsersName,
      ExternalIdReader externalIdReader,
      @Named(ExternalIdCacheImpl.CACHE_NAME)
          Provider<Cache<ObjectId, AllExternalIds>> externalIdCache,
      MetricMaker metricMaker,
      @GerritServerConfig Config config) {
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
            Field.ofBoolean("partial", Metadata.Builder::partial).build());
    this.reloadDifferential =
        metricMaker.newTimer(
            "notedb/external_id_partial_read_latency",
            new Description(
                    "Latency for generating a new external ID cache state from a prior state.")
                .setCumulative()
                .setUnit(Units.MILLISECONDS));
    this.enablePartialReloads =
        config.getBoolean("cache", ExternalIdCacheImpl.CACHE_NAME, "enablePartialReloads", false);
  }

  @Override
  public AllExternalIds load(ObjectId notesRev) throws IOException, ConfigInvalidException {
    if (!enablePartialReloads) {
      logger.atInfo().log(
          "Partial reloads of "
              + ExternalIdCacheImpl.CACHE_NAME
              + " disabled. Falling back to full reload.");
      return reloadAllExternalIds(notesRev);
    }

    // We failed to load the requested value from the cache (hence, this loader was  invoked).
    // Therefore, try to create this entry from a past value using the minimal amount of Git
    // operations possible to reduce latency.
    //
    // First, try to find the most recent state we have in the persistent cache. Most of the time,
    // this will be the state before the last update happened, but it can also date further back. We
    // try a best effort approach and check the last 10 states. If nothing is found, we default to
    // loading the value from scratch.
    //
    // If a prior state was found, we use Git to diff the trees and find modifications. This is
    // faster than just loading the complete current tree and working off of that because of how the
    // data is structured: NotesMaps use nested trees, so, for example, a NotesMap with 200k entries
    // has two layers of nesting: 12/34/1234..99. DiffFormatter is smart in skipping the traversal
    // of identical subtrees.
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
      while ((parentWithCacheValue = rw.next()) != null && i++ < MAX_HISTORY_LOOKBACK) {
        oldExternalIds = externalIdCache.get().getIfPresent(parentWithCacheValue.getId());
        if (oldExternalIds != null) {
          // We found a previously cached state.
          break;
        }
      }
      if (oldExternalIds == null) {
        logger.atWarning().log(
            "Unable to find an old ExternalId cache state, falling back to full reload");
        return reloadAllExternalIds(notesRev);
      }

      List<DiffEntry> allChanges;
      try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
        diffFormatter.setReader(repo.newObjectReader(), repo.getConfig());
        allChanges = diffFormatter.scan(parentWithCacheValue, currentCommit);
      }

      Set<ObjectId> removals = new HashSet<>(); // Set<Blob-Object-Id>
      Map<ObjectId, ObjectId> additions = new HashMap<>(); // Map<Name-ObjectId, Blob-Object-Id>
      for (DiffEntry diff : allChanges) {
        switch (diff.getChangeType()) {
          case ADD:
            additions.put(fileNameToObjectId(diff.getNewPath()), diff.getNewId().toObjectId());
            break;
          case DELETE:
            removals.add(diff.getOldId().toObjectId());
            break;
          case MODIFY:
            removals.add(diff.getOldId().toObjectId());
            additions.put(fileNameToObjectId(diff.getNewPath()), diff.getNewId().toObjectId());
            break;
          case RENAME:
          case COPY:
          default:
            // COPY is an illegal operation because the key is both in the file name and in the file
            // which means that COPY would create inconsistent data.
            // RENAME is used only when the NotesMap reshards (adding a new tree level). In this
            // case we would exceed any limit for differential updates, so we can fall back as well.
            // DiffFormatter also has rename detection turned off by default.
            logger.atWarning().log(
                "Unable to load External IDs using fast diff approach because %s is an unknown "
                    + "modification type, falling back to full reload",
                diff);
            return reloadAllExternalIds(notesRev);
        }
      }

      ImmutableSetMultimap.Builder<Account.Id, ExternalId> byAccount =
          ImmutableSetMultimap.builder();
      ImmutableSetMultimap.Builder<String, ExternalId> byEmail = ImmutableSetMultimap.builder();

      // Copy over old ExternalIds but exclude deleted ones
      for (ExternalId externalId : oldExternalIds.byAccount().values()) {
        if (removals.contains(externalId.blobId())) {
          continue;
        }

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
                ExternalId.parse(
                    nameToBlob.getKey().name(),
                    reader.open(nameToBlob.getValue()).getCachedBytes(),
                    nameToBlob.getValue());
          } catch (ConfigInvalidException | RuntimeException e) {
            logger.atSevere().withCause(e).log(
                "Ignoring invalid external ID note %s", nameToBlob.getKey().name());
            continue;
          }

          byAccount.put(parsedExternalId.accountId(), parsedExternalId);
          if (parsedExternalId.email() != null) {
            byEmail.put(parsedExternalId.email(), parsedExternalId);
          }
        }
      }

      reloadCounter.increment(true);
      reloadDifferential.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
      return new AutoValue_AllExternalIds(byAccount.build(), byEmail.build());
    }
  }

  private static ObjectId fileNameToObjectId(String path) {
    return ObjectId.fromString(CharMatcher.is('/').removeFrom(path));
  }

  private AllExternalIds reloadAllExternalIds(ObjectId notesRev)
      throws IOException, ConfigInvalidException {
    try (TraceTimer ignored =
        TraceContext.newTimer(
            "Loading external IDs from scratch",
            Metadata.builder().revision(notesRev.name()).build())) {
      ImmutableSet<ExternalId> externalIds = externalIdReader.all(notesRev);
      externalIds.forEach(ExternalId::checkThatBlobIdIsSet);
      AllExternalIds allExternalIds = AllExternalIds.create(externalIds);
      reloadCounter.increment(false);
      return allExternalIds;
    }
  }
}
