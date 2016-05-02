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

package com.google.gerrit.server.project;

import com.google.gerrit.extensions.common.MergeableInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.Merger;
import org.eclipse.jgit.merge.ResolveMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

import java.io.IOException;

/**
 * Check the mergeability at current branch for the source point.
 * <p>
 * source could be any git object references expression
 * @see org.eclipse.jgit.lib.Repository#resolve(String)
 *
 * strategy is name of the merge strategy (optional)
 * @see org.eclipse.jgit.merge.MergeStrategy
 */
public class CheckMergeability implements RestReadView<BranchResource> {

  private String source;
  private String strategy;

  @Option(name = "--source", metaVar = "branch",
      usage = "source point to merge", required = true)
  public CheckMergeability setSource(String mergeSource) {
    this.source = mergeSource;
    return this;
  }

  @Option(name = "--strategy", metaVar = "recursive",
      usage = "the merge strategy to use")
  public CheckMergeability setStrategy(String mergeStrategy) {
    this.strategy = mergeStrategy;
    return this;
  }

  private final GitRepositoryManager gitManager;

  @Inject
  CheckMergeability(GitRepositoryManager gitManager,
      @GerritServerConfig Config cfg) {
    this.gitManager = gitManager;
    this.strategy = MergeUtil.getMergeStrategy(cfg).getName();
  }


  @Override
  public MergeableInfo apply(BranchResource resource)
      throws IOException, BadRequestException {
    MergeableInfo result = new MergeableInfo();
    result.mergeStrategy = strategy;
    try (Repository git = gitManager.openRepository(resource.getNameKey());
         RevWalk rw = new RevWalk(git)) {
      ObjectInserter inserter = MergeUtil.createDryRunInserter(git);
      Merger m = MergeUtil.newMerger(git, inserter, strategy);

      Ref destRef = git.getRefDatabase().exactRef(resource.getRef());
      RevCommit targetCommit = rw.parseCommit(destRef.getObjectId());
      RevCommit sourceCommit = rw.parseCommit(git.resolve(source));
      result.mergeable = m.merge(targetCommit, sourceCommit);
      if (m instanceof ResolveMerger) {
        result.conflicts = ((ResolveMerger) m).getUnmergedPaths();
      }
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage());
    }
    return result;
  }
}
