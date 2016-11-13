// Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.MergeListBuilder;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

public class GetMergeList implements RestReadView<RevisionResource> {
  private final GitRepositoryManager repoManager;
  private final ChangeJson.Factory json;

  @Option(name = "--parent", usage = "Uninteresting parent (1-based, default = 1)")
  private int uninterestingParent = 1;

  @Option(name = "--links", usage = "Include weblinks")
  private boolean addLinks;

  @Inject
  GetMergeList(GitRepositoryManager repoManager, ChangeJson.Factory json) {
    this.repoManager = repoManager;
    this.json = json;
  }

  public void setUninterestingParent(int uninterestingParent) {
    this.uninterestingParent = uninterestingParent;
  }

  public void setAddLinks(boolean addLinks) {
    this.addLinks = addLinks;
  }

  @Override
  public Response<List<CommitInfo>> apply(RevisionResource rsrc)
      throws BadRequestException, IOException {
    Project.NameKey p = rsrc.getChange().getProject();
    try (Repository repo = repoManager.openRepository(p);
        RevWalk rw = new RevWalk(repo)) {
      String rev = rsrc.getPatchSet().getRevision().get();
      RevCommit commit = rw.parseCommit(ObjectId.fromString(rev));
      rw.parseBody(commit);

      if (uninterestingParent < 1 || uninterestingParent > commit.getParentCount()) {
        throw new BadRequestException("No such parent: " + uninterestingParent);
      }

      if (commit.getParentCount() < 2) {
        return createResponse(rsrc, ImmutableList.<CommitInfo>of());
      }

      List<RevCommit> commits = MergeListBuilder.build(rw, commit, uninterestingParent);
      List<CommitInfo> result = new ArrayList<>(commits.size());
      ChangeJson changeJson = json.create(ChangeJson.NO_OPTIONS);
      for (RevCommit c : commits) {
        result.add(changeJson.toCommit(rsrc.getControl(), rw, c, addLinks, true));
      }
      return createResponse(rsrc, result);
    }
  }

  private static Response<List<CommitInfo>> createResponse(
      RevisionResource rsrc, List<CommitInfo> result) {
    Response<List<CommitInfo>> r = Response.ok(result);
    if (rsrc.isCacheable()) {
      r.caching(CacheControl.PRIVATE(7, TimeUnit.DAYS));
    }
    return r;
  }
}
