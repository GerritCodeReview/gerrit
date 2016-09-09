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

import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class GetMergeList implements RestReadView<RevisionResource> {
  private final GitRepositoryManager repoManager;
  private final ChangeJson.Factory json;

  @Option(name = "--parent", usage = "Uninteresting parent (default = 0)")
  private int uninterestingParent;

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
      throws BadRequestException, MethodNotAllowedException, IOException {
    List<CommitInfo> result = new ArrayList<>();
    Project.NameKey p = rsrc.getChange().getProject();
    try (Repository repo = repoManager.openRepository(p);
        RevWalk rw = new RevWalk(repo)) {
      String rev = rsrc.getPatchSet().getRevision().get();
      RevCommit commit = rw.parseCommit(ObjectId.fromString(rev));
      rw.parseBody(commit);

      if (uninterestingParent < 0
          || uninterestingParent >= commit.getParentCount()) {
        throw new BadRequestException("No such parent: " + uninterestingParent);
      }

      if (commit.getParentCount() < 2) {
        throw new MethodNotAllowedException();
      }

      for (int parent = 0; parent < commit.getParentCount(); parent++) {
        if (parent == uninterestingParent) {
          rw.markUninteresting(commit.getParent(parent));
        } else {
          rw.markStart(commit.getParent(parent));
        }
      }

      RevCommit c;
      while ((c = rw.next()) != null) {
        CommitInfo info = json.create(ChangeJson.NO_OPTIONS)
            .toCommit(rsrc.getControl(), rw, c, addLinks, true);
        result.add(info);
      }
    }

    Response<List<CommitInfo>> r = Response.ok(result);
    if (rsrc.isCacheable()) {
      r.caching(CacheControl.PRIVATE(7, TimeUnit.DAYS));
    }
    return r;
  }
}
