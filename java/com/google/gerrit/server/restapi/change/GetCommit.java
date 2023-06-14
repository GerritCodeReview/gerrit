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

import static java.util.concurrent.TimeUnit.DAYS;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ParentCommitData;
import com.google.gerrit.entities.ParentCommitData.ChangeRevision;
import com.google.gerrit.entities.ParentCommitData.TargetBranch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.change.RevisionJson;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.ReachabilityChecker;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

public class GetCommit implements RestReadView<RevisionResource> {
  private final GitRepositoryManager repoManager;
  private final RevisionJson.Factory json;
  private final Provider<InternalChangeQuery> queryProvider;

  private boolean addLinks;

  @Inject
  GetCommit(
      GitRepositoryManager repoManager,
      RevisionJson.Factory json,
      Provider<InternalChangeQuery> queryProvider) {
    this.repoManager = repoManager;
    this.json = json;
    this.queryProvider = queryProvider;
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
      RevCommit[] parents = commit.getParents();
      List<ParentCommitData> parentDatas = new ArrayList<>();
      for (RevCommit parent : parents) {
        ParentCommitData parentData =
            getParentData(repo, rw, parent, rsrc.getChange().getDest().branch());
        parentDatas.add(parentData);
      }
      rw.parseBody(commit);
      CommitInfo info =
          json.create(ImmutableSet.of())
              .getCommitInfo(
                  rsrc.getProject(),
                  rw,
                  commit,
                  addLinks,
                  /* fillCommit= */ true,
                  rsrc.getChange().getDest().branch(),
                  rsrc.getChange().getKey().get(),
                  rsrc.getChange().getId().get(),
                  parentDatas);
      Response<CommitInfo> r = Response.ok(info);
      if (rsrc.isCacheable()) {
        r.caching(CacheControl.PRIVATE(7, DAYS));
      }
      return r;
    }
  }

  private ParentCommitData getParentData(
      Repository repo, RevWalk rw, RevCommit parent, String targetBranch) throws IOException {
    // Check if the parent commit is reachable from the target branch
    Ref targetBranchRef = repo.exactRef(targetBranch);
    RevCommit targetBranchCommit = rw.parseCommit(targetBranchRef.getObjectId());
    ReachabilityChecker checker = rw.getObjectReader().createReachabilityChecker(rw);
    Optional<RevCommit> unreachable =
        checker.areAllReachable(
            ImmutableList.of(parent), ImmutableList.of(targetBranchCommit).stream());
    if (unreachable.isEmpty()) {
      return ParentCommitData.create(
          Optional.of(TargetBranch.create(targetBranch, parent.getId().name())), Optional.empty());
    }

    Set<Ref> parentRefs = repo.getRefDatabase().getTipsWithSha1(parent);
    // it's guaranteed that one Ref of form 'refs/changes/xy/abcdxy/<patchset_number>' points
    // to the parent commit
    Ref patchsetCommitRef =
        parentRefs.stream()
            .filter(
                r -> RefNames.isRefsChanges(r.getName()) && !RefNames.isNoteDbMetaRef(r.getName()))
            .collect(MoreCollectors.onlyElement());

    System.out.println(patchsetCommitRef.getName());
    patchsetCommitRef.getName();
    Pattern pattern = Pattern.compile("refs/changes/[0-9]{2}/([0-9]+)/([0-9]+)");
    Matcher matcher = pattern.matcher(patchsetCommitRef.getName());
    Integer changeNumber = Integer.parseInt(matcher.group(1));
    Integer patchSetNumber = Integer.parseInt(matcher.group(2));

    ChangeData changeData =
        queryProvider.get().byLegacyChangeId(Change.id(changeNumber)).stream()
            .collect(MoreCollectors.onlyElement());

    return ParentCommitData.create(
        Optional.empty(),
        Optional.of(
            ChangeRevision.create(
                changeNumber,
                changeData.getId().toString(),
                patchSetNumber,
                changeData.change().getStatus().name())));
  }
}
