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
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

import java.io.IOException;

/**
 * Created by czhen on 5/9/16.
 */
public class MergeabilityCheck implements RestReadView<BranchResource> {

  Config config;
  String sourceRef;
  String mergeStrategy;

  @Option(name = "--source-ref", aliases = {"-sr"}, metaVar = "branch",
      usage = "source commit reference point to merge", required = true)
  public MergeabilityCheck setSourceRef(String sourceRef) {
    this.sourceRef = sourceRef;
    return this;
  }

  @Option(name = "--merge-strategy", aliases = {"-ms"}, metaVar = "recursive",
      usage = "the merge strategy to use")
  public MergeabilityCheck setMergeStrategy(String mergeStrategy) {
    this.mergeStrategy = mergeStrategy;
    return this;
  }

  private final GitRepositoryManager gitManager;

  @Inject
  MergeabilityCheck(GitRepositoryManager gitManager,
      @GerritServerConfig Config cfg) {
    this.gitManager = gitManager;
    this.mergeStrategy = MergeUtil.getMergeStrategy(cfg).getName();
  }


  @Override
  public MergeableInfo apply(BranchResource resource)
      throws IOException {
    MergeableInfo result = new MergeableInfo();
    result.mergeStrategy = mergeStrategy;
    try (Repository git = gitManager
        .openRepository(resource.getNameKey());
        RevWalk rw = new RevWalk(git)) {
      ObjectInserter inserter = MergeUtil.createDryRunInserter(git);
      Merger m = MergeUtil.newMerger(git, inserter, mergeStrategy);

      Ref destRef = git.getRefDatabase().exactRef(resource.getRef());
      RevCommit targetCommit = rw.parseCommit(destRef.getObjectId());
      RevCommit sourceCommit = rw.parseCommit(git.resolve(sourceRef));
      result.mergeable = m.merge(targetCommit, sourceCommit);
    }
    return result;
  }
}
