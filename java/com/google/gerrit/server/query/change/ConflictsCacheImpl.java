// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.flogger.LazyArgs.lazy;
import static com.google.gerrit.server.query.change.ConflictsPredicate.warnWithOccasionalStackTrace;

import com.google.common.cache.Cache;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.serialize.BooleanCacheSerializer;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.submit.SubmitDryRun;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class ConflictsCacheImpl implements ConflictsCache {
  public static final String NAME = "conflicts";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(NAME, ConflictKey.class, Boolean.class)
            .version(1)
            .keySerializer(ConflictKey.Serializer.INSTANCE)
            .valueSerializer(BooleanCacheSerializer.INSTANCE)
            .maximumWeight(37400);
        bind(ConflictsCache.class).to(ConflictsCacheImpl.class);
      }
    };
  }

  private final Cache<ConflictKey, Boolean> conflictsCache;

  @Inject
  public ConflictsCacheImpl(@Named(NAME) Cache<ConflictKey, Boolean> conflictsCache) {
    this.conflictsCache = conflictsCache;
  }

  @Override
  public void put(ConflictKey key, boolean value) {
    conflictsCache.put(key, value);
  }

  @Override
  public Boolean get(ConflictKey key, Callable<? extends Boolean> loader)
      throws ExecutionException {
    return conflictsCache.get(key, loader);
  }

  static class Loader implements Callable<Boolean> {
    private final Change otherChange;
    private final SubmitTypeRecord str;
    private final ConflictsPredicate.ChangeDataCache changeDataCache;
    private final ObjectId other;
    private final ChangeQueryBuilder.Arguments args;
    private final Project.NameKey otherProject;
    private final Change.Id id;

    Loader(
        Change otherChange,
        SubmitTypeRecord str,
        ConflictsPredicate.ChangeDataCache changeDataCache,
        ObjectId other,
        ChangeQueryBuilder.Arguments args,
        Project.NameKey otherProject,
        Change.Id id) {
      super();
      this.otherChange = otherChange;
      this.str = str;
      this.changeDataCache = changeDataCache;
      this.other = other;
      this.args = args;
      this.otherProject = otherProject;
      this.id = id;
    }

    @Override
    public Boolean call() throws Exception {
      try (Repository repo = args.repoManager.openRepository(otherChange.getProject());
          CodeReviewCommit.CodeReviewRevWalk rw = CodeReviewCommit.newRevWalk(repo)) {
        return !args.submitDryRun.run(
            null,
            str.type,
            repo,
            rw,
            otherChange.getDest(),
            changeDataCache.getTestAgainst(),
            other,
            getAlreadyAccepted(repo, rw));
      } catch (NoSuchProjectException | IOException e) {
        ObjectId finalOther = other;
        warnWithOccasionalStackTrace(
            e,
            "Failure when loading conflicts of change %s in %s (%s): %s",
            id,
            firstNonNull(otherProject, "unknown project"),
            lazy(() -> finalOther != null ? finalOther.name() : "unknown commit"),
            e.getMessage());
        return false;
      }
    }

    private Set<RevCommit> getAlreadyAccepted(Repository repo, RevWalk rw) {
      try {
        Set<RevCommit> accepted = new HashSet<>();
        SubmitDryRun.addCommits(changeDataCache.getAlreadyAccepted(repo), rw, accepted);
        ObjectId tip = changeDataCache.getTestAgainst();
        if (tip != null) {
          accepted.add(rw.parseCommit(tip));
        }
        return accepted;
      } catch (StorageException | IOException e) {
        throw new StorageException("Failed to determine already accepted commits.", e);
      }
    }
  }
}
