// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.common.BlameInfo;
import com.google.gerrit.extensions.common.RangeInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.FileResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.patch.AutoMerger;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gitiles.blame.cache.BlameCache;
import com.google.gitiles.blame.cache.Region;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ThreeWayMergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

public class GetBlame implements RestReadView<FileResource> {

  private final GitRepositoryManager repoManager;
  private final BlameCache blameCache;
  private final boolean allowBlame;
  private final ThreeWayMergeStrategy mergeStrategy;
  private final AutoMerger autoMerger;

  @Option(
    name = "--base",
    aliases = {"-b"},
    usage =
        "whether to load the blame of the base revision (the direct"
            + " parent of the change) instead of the change"
  )
  private boolean base;

  @Inject
  GetBlame(
      GitRepositoryManager repoManager,
      BlameCache blameCache,
      @GerritServerConfig Config cfg,
      AutoMerger autoMerger) {
    this.repoManager = repoManager;
    this.blameCache = blameCache;
    this.mergeStrategy = MergeUtil.getMergeStrategy(cfg);
    this.autoMerger = autoMerger;
    allowBlame = cfg.getBoolean("change", "allowBlame", true);
  }

  @Override
  public Response<List<BlameInfo>> apply(FileResource resource)
      throws RestApiException, OrmException, IOException, InvalidChangeOperationException {
    if (!allowBlame) {
      throw new BadRequestException("blame is disabled");
    }

    Project.NameKey project = resource.getRevision().getChange().getProject();
    try (Repository repository = repoManager.openRepository(project);
        ObjectInserter ins = repository.newObjectInserter();
        ObjectReader reader = ins.newReader();
        RevWalk revWalk = new RevWalk(reader)) {
      String refName =
          resource.getRevision().getEdit().isPresent()
              ? resource.getRevision().getEdit().get().getRefName()
              : resource.getRevision().getPatchSet().getRefName();

      Ref ref = repository.findRef(refName);
      if (ref == null) {
        throw new ResourceNotFoundException("unknown ref " + refName);
      }
      ObjectId objectId = ref.getObjectId();
      RevCommit revCommit = revWalk.parseCommit(objectId);
      RevCommit[] parents = revCommit.getParents();

      String path = resource.getPatchKey().getFileName();

      List<BlameInfo> result;
      if (!base) {
        result = blame(revCommit, path, repository, revWalk);

      } else if (parents.length == 0) {
        throw new ResourceNotFoundException("Initial commit doesn't have base");

      } else if (parents.length == 1) {
        result = blame(parents[0], path, repository, revWalk);

      } else if (parents.length == 2) {
        ObjectId automerge = autoMerger.merge(repository, revWalk, ins, revCommit, mergeStrategy);
        result = blame(automerge, path, repository, revWalk);

      } else {
        throw new ResourceNotFoundException(
            "Cannot generate blame for merge commit with more than 2 parents");
      }

      Response<List<BlameInfo>> r = Response.ok(result);
      if (resource.isCacheable()) {
        r.caching(CacheControl.PRIVATE(7, TimeUnit.DAYS));
      }
      return r;
    }
  }

  private List<BlameInfo> blame(ObjectId id, String path, Repository repository, RevWalk revWalk)
      throws IOException {
    ListMultimap<BlameInfo, RangeInfo> ranges =
        MultimapBuilder.hashKeys().arrayListValues().build();
    List<BlameInfo> result = new ArrayList<>();
    if (blameCache.findLastCommit(repository, id, path) == null) {
      return result;
    }

    List<Region> blameRegions = blameCache.get(repository, id, path);
    int from = 1;
    for (Region region : blameRegions) {
      RevCommit commit = revWalk.parseCommit(region.getSourceCommit());
      BlameInfo blameInfo = toBlameInfo(commit, region.getSourceAuthor());
      ranges.put(blameInfo, new RangeInfo(from, from + region.getCount() - 1));
      from += region.getCount();
    }

    for (BlameInfo key : ranges.keySet()) {
      key.ranges = ranges.get(key);
      result.add(key);
    }
    return result;
  }

  private static BlameInfo toBlameInfo(RevCommit commit, PersonIdent sourceAuthor) {
    BlameInfo blameInfo = new BlameInfo();
    blameInfo.author = sourceAuthor.getName();
    blameInfo.id = commit.getName();
    blameInfo.commitMsg = commit.getFullMessage();
    blameInfo.time = commit.getCommitTime();
    return blameInfo;
  }
}
