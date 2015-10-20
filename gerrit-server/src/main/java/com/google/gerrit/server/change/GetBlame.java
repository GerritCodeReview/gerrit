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

package com.google.gerrit.server.change;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.gerrit.extensions.common.BlameInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.patch.AutoMerger;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gitiles.blame.BlameCache;
import com.google.gitiles.blame.Region;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ThreeWayMergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GetBlame implements RestReadView<FileResource> {

  private final GitRepositoryManager repoManager;
  private final InternalChangeQuery internalChangeQuery;
  private final BlameCache blameCache;
  private final boolean allowBlame;
  private final ThreeWayMergeStrategy mergeStrategy;
  private final AutoMerger autoMerger;
  private boolean base;

  @Option(name = "--base", aliases = {"-b"}, metaVar = "BASE",
      usage = "whether to load the blame of the base revision (the direct"
        + " parent of the change) instead of the change")
  public void setBase(boolean on) {
    this.base = true;
  }

  @Inject
  GetBlame(GitRepositoryManager repoManager,
      InternalChangeQuery internalChangeQuery,
      BlameCache blameCache,
      @GerritServerConfig Config cfg,
      AutoMerger autoMerger) {
    this.repoManager = repoManager;
    this.internalChangeQuery = internalChangeQuery;
    this.blameCache = blameCache;
    this.mergeStrategy = MergeUtil.getMergeStrategy(cfg);
    this.autoMerger = autoMerger;
    allowBlame = cfg.getBoolean("change", "allowBlame", true);
  }

  @Override
  public Response<BlameInfo> apply(FileResource resource)
      throws RestApiException, OrmException, IOException,
      InvalidChangeOperationException {
    if (!allowBlame) {
      throw new BadRequestException("blame is disabled");
    }

    Project.NameKey project = resource.getRevision().getChange().getProject();
    try (Repository repository = repoManager.openRepository(project);
        RevWalk revWalk = new RevWalk(repository)) {
      String refName = resource.getRevision().getEdit().isPresent()
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

      List<BlameInfo.Line> blames;
      if (!base) {
        blames = blame(revCommit, path, repository, revWalk);

      } else if (parents.length == 0) {
        throw new ResourceNotFoundException("Initial commit doesn't have base");

      } else if (parents.length == 1) {
        blames = blame(parents[0], path, repository, revWalk);

      } else if (parents.length == 2) {
        ObjectId automerge = autoMerger.merge(repository, revWalk, revCommit,
            mergeStrategy);
        blames = blame(automerge, path, repository, revWalk);

      } else {
        throw new ResourceNotFoundException(
            "Cannot generate blame for merge commit with more than 2 parents");
      }

      BlameInfo result = new BlameInfo();
      result.blames = packBlames(blames);

      for (BlameInfo.Blame blame : result.blames) {
        List<ChangeData> changeDatas =
            internalChangeQuery.byCommit(ObjectId.fromString(blame.meta.id));
        for (ChangeData changeData : changeDatas) {
          PatchSet.Id patchSetId = changeData.change().currentPatchSetId();
          if (patchSetId != null) {
            blame.meta.changeId = changeData.change().getId().get();
            blame.meta.patchSetId = patchSetId.get();
          }
        }
      }

      Response<BlameInfo> r = Response.ok(result);
      if (resource.isCacheable()) {
        r.caching(CacheControl.PRIVATE(7, TimeUnit.DAYS));
      }
      return r;
    }
  }

  private List<BlameInfo.Line> blame(ObjectId id, String path,
      Repository repository, RevWalk revWalk) throws IOException {
    List<BlameInfo.Line> result = new ArrayList<>();
    if (blameCache.findLastCommit(repository, id, path) != null) {
      List<Region> blameRegions = blameCache.get(repository, id, path);
      int from = 1;
      for (Region region : blameRegions) {
        ObjectId commitId = region.getSourceCommit();
        RevCommit commit = revWalk.parseCommit(commitId);
        BlameInfo.Meta meta = toMeta(commit, region.getSourceAuthor());
        addLines(result, from, region, meta);
        from = from + region.getCount();
        revWalk.reset();
      }
    }
    return result;
  }

  private static BlameInfo.Meta toMeta(
      RevCommit commit, PersonIdent sourceAuthor) {
    BlameInfo.Meta meta = new BlameInfo.Meta();
    meta.author = sourceAuthor.getName();
    meta.id = commit.getName();
    meta.commitMsg = commit.getFullMessage();
    meta.time = commit.getCommitTime();
    return meta;
  }

  private static void addLines(List<BlameInfo.Line> result,
      int from, Region region, BlameInfo.Meta meta) {
    for (int i = from; i < from + region.getCount(); ++i) {
      BlameInfo.Line line = new BlameInfo.Line();
      line.meta = meta;
      line.lineNum = i;
      result.add(line);
    }
  }

  private static List<BlameInfo.Blame> packBlames(
      List<BlameInfo.Line> blames) {
    ListMultimap<BlameInfo.Meta, BlameInfo.Blame.FromTo> fromTos =
        ArrayListMultimap.create();
    int numLines = blames.size();
    for (int i = 1; i <= numLines; ++i) {
      BlameInfo.Line current = blames.get(i - 1);
      int j = i;
      BlameInfo.Line next = null;
      do {
        j = j + 1;
        if (j <= numLines) {
          next = blames.get(j - 1);
        }
      } while(j <= numLines && next != null
          && next.meta.id.equals(current.meta.id));
      BlameInfo.Blame.FromTo fromTo = new BlameInfo.Blame.FromTo(
          current.lineNum, current.lineNum + j - i - 1);
      fromTos.put(current.meta, fromTo);
      i = j - 1;
    }

    List<BlameInfo.Blame> result = new ArrayList<>();
    for (BlameInfo.Meta key : fromTos.keySet()) {
      BlameInfo.Blame blame = new BlameInfo.Blame();
      blame.meta = key;
      blame.fromTo = fromTos.get(key);
      result.add(blame);
    }
    return result;
  }
}
