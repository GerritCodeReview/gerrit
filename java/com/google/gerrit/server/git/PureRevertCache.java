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

package com.google.gerrit.server.git;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache;
import com.google.gerrit.server.cache.proto.Cache.PureRevertKeyProto;
import com.google.gerrit.server.cache.serialize.BooleanCacheSerializer;
import com.google.gerrit.server.cache.serialize.ObjectIdConverter;
import com.google.gerrit.server.cache.serialize.ProtobufSerializer;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Computes and caches if a change is a pure revert of another change. */
@Singleton
public class PureRevertCache {
  private static final String ID_CACHE = "pure_revert";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(ID_CACHE, Cache.PureRevertKeyProto.class, Boolean.class)
            .maximumWeight(100)
            .loader(Loader.class)
            .version(1)
            .keySerializer(new ProtobufSerializer<>(Cache.PureRevertKeyProto.parser()))
            .valueSerializer(BooleanCacheSerializer.INSTANCE);
      }
    };
  }

  private final LoadingCache<PureRevertKeyProto, Boolean> cache;
  private final ChangeNotes.Factory notesFactory;

  @Inject
  PureRevertCache(
      @Named(ID_CACHE) LoadingCache<PureRevertKeyProto, Boolean> cache,
      ChangeNotes.Factory notesFactory) {
    this.cache = cache;
    this.notesFactory = notesFactory;
  }

  /**
   * Returns {@code true} if {@code claimedRevert} is a pure (clean) revert of the change that is
   * referenced in {@link Change#getRevertOf()}.
   *
   * @return {@code true} if {@code claimedRevert} is a pure (clean) revert.
   * @throws IOException if there was a problem with the storage layer
   * @throws BadRequestException if there is a problem with the provided {@link ChangeNotes}
   */
  public boolean isPureRevert(ChangeNotes claimedRevert) throws IOException, BadRequestException {
    if (claimedRevert.getChange().getRevertOf() == null) {
      throw new BadRequestException("revertOf not set");
    }
    ChangeNotes claimedOriginal =
        notesFactory.createChecked(
            claimedRevert.getProjectName(), claimedRevert.getChange().getRevertOf());
    return isPureRevert(
        claimedRevert.getProjectName(),
        ObjectId.fromString(claimedRevert.getCurrentPatchSet().getRevision().get()),
        ObjectId.fromString(claimedOriginal.getCurrentPatchSet().getRevision().get()));
  }

  /**
   * Returns {@code true} if {@code claimedRevert} is a pure (clean) revert of {@code
   * claimedOriginal}.
   *
   * @return {@code true} if {@code claimedRevert} is a pure (clean) revert of {@code
   *     claimedOriginal}.
   * @throws IOException if there was a problem with the storage layer
   * @throws BadRequestException if there is a problem with the provided {@link ObjectId}s
   */
  public boolean isPureRevert(
      Project.NameKey project, ObjectId claimedRevert, ObjectId claimedOriginal)
      throws IOException, BadRequestException {
    try {
      return cache.get(key(project, claimedRevert, claimedOriginal));
    } catch (ExecutionException e) {
      Throwables.throwIfInstanceOf(e.getCause(), BadRequestException.class);
      throw new IOException(e);
    }
  }

  @VisibleForTesting
  static PureRevertKeyProto key(
      Project.NameKey project, ObjectId claimedRevert, ObjectId claimedOriginal) {
    ByteString original = ObjectIdConverter.create().toByteString(claimedOriginal);
    ByteString revert = ObjectIdConverter.create().toByteString(claimedRevert);
    return PureRevertKeyProto.newBuilder()
        .setProject(project.get())
        .setClaimedOriginal(original)
        .setClaimedRevert(revert)
        .build();
  }

  static class Loader extends CacheLoader<PureRevertKeyProto, Boolean> {
    private final GitRepositoryManager repoManager;
    private final MergeUtil.Factory mergeUtilFactory;
    private final ProjectCache projectCache;

    @Inject
    Loader(
        GitRepositoryManager repoManager,
        MergeUtil.Factory mergeUtilFactory,
        ProjectCache projectCache) {
      this.repoManager = repoManager;
      this.mergeUtilFactory = mergeUtilFactory;
      this.projectCache = projectCache;
    }

    @Override
    public Boolean load(PureRevertKeyProto key) throws BadRequestException, IOException {
      try (TraceContext.TraceTimer ignored =
          TraceContext.newTimer("Loading pure revert for %s", key)) {
        ObjectId original = ObjectIdConverter.create().fromByteString(key.getClaimedOriginal());
        ObjectId revert = ObjectIdConverter.create().fromByteString(key.getClaimedRevert());
        Project.NameKey project = Project.nameKey(key.getProject());

        try (Repository repo = repoManager.openRepository(project);
            ObjectInserter oi = repo.newObjectInserter();
            RevWalk rw = new RevWalk(repo)) {
          RevCommit claimedOriginalCommit;
          try {
            claimedOriginalCommit = rw.parseCommit(original);
          } catch (InvalidObjectIdException | MissingObjectException e) {
            throw new BadRequestException("invalid object ID");
          }
          if (claimedOriginalCommit.getParentCount() == 0) {
            throw new BadRequestException("can't check against initial commit");
          }
          RevCommit claimedRevertCommit = rw.parseCommit(revert);
          if (claimedRevertCommit.getParentCount() == 0) {
            return false;
          }
          // Rebase claimed revert onto claimed original
          ThreeWayMerger merger =
              mergeUtilFactory
                  .create(projectCache.checkedGet(project))
                  .newThreeWayMerger(oi, repo.getConfig());
          merger.setBase(claimedRevertCommit.getParent(0));
          boolean success = merger.merge(claimedRevertCommit, claimedOriginalCommit);
          if (!success || merger.getResultTreeId() == null) {
            // Merge conflict during rebase
            return false;
          }

          // Any differences between claimed original's parent and the rebase result indicate that
          // the
          // claimedRevert is not a pure revert but made content changes
          try (DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream())) {
            df.setReader(oi.newReader(), repo.getConfig());
            List<DiffEntry> entries =
                df.scan(claimedOriginalCommit.getParent(0), merger.getResultTreeId());
            return entries.isEmpty();
          }
        }
      }
    }
  }
}
