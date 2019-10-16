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

package com.google.gerrit.server.restapi.change;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.RevisionJson;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

public class GetCommit implements RestReadView<RevisionResource> {
  private final GitRepositoryManager repoManager;
  private final RevisionJson.Factory json;

  private boolean addLinks;

  @Inject
  GetCommit(GitRepositoryManager repoManager, RevisionJson.Factory json) {
    this.repoManager = repoManager;
    this.json = json;
  }

  @Option(name = "--links", usage = "Include weblinks")
  public GetCommit setAddLinks(boolean addLinks) {
    this.addLinks = addLinks;
    return this;
  }

  @Override
  public Response<CommitInfo> apply(RevisionResource rsrc) throws IOException {
    Project.NameKey p = rsrc.getChange().getProject();
    try (Repository repo = repoManager.openRepository(p);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit commit = rw.parseCommit(rsrc.getPatchSet().commitId());
      rw.parseBody(commit);
      CommitInfo info =
          json.create(ImmutableSet.of())
              .getCommitInfo(rsrc.getProject(), rw, commit, addLinks, true);
      Response<CommitInfo> r = Response.ok(info);
      if (rsrc.isCacheable()) {
        r.caching(CacheControl.PRIVATE(7, TimeUnit.DAYS));
      }
      return r;
    }
  }
}
