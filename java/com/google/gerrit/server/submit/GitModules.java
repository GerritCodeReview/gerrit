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

package com.google.gerrit.server.submit;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.submit.MergeOpRepoManager.OpenRepo;
import com.google.gerrit.server.util.git.SubmoduleSectionParser;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

/**
 * Loads the .gitmodules file of the specified project/branch. It can be queried which submodules
 * this branch is subscribed to.
 */
public class GitModules {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    GitModules create(Branch.NameKey project, MergeOpRepoManager m);
  }

  private static final String GIT_MODULES = ".gitmodules";

  private Set<SubmoduleSubscription> subscriptions;

  @Inject
  GitModules(
      @CanonicalWebUrl @Nullable String canonicalWebUrl,
      @Assisted Branch.NameKey branch,
      @Assisted MergeOpRepoManager orm)
      throws IOException {
    Project.NameKey project = branch.project();
    logger.atFine().log("Loading .gitmodules of %s for project %s", branch, project);
    try {
      OpenRepo or = orm.getRepo(project);
      ObjectId id = or.repo.resolve(branch.branch());
      if (id == null) {
        throw new IOException("Cannot open branch " + branch.branch());
      }
      RevCommit commit = or.rw.parseCommit(id);

      try (TreeWalk tw = TreeWalk.forPath(or.repo, GIT_MODULES, commit.getTree())) {
        if (tw == null || (tw.getRawMode(0) & FileMode.TYPE_MASK) != FileMode.TYPE_FILE) {
          subscriptions = Collections.emptySet();
          logger.atFine().log("The .gitmodules file doesn't exist in %s", branch);
          return;
        }
      }
      BlobBasedConfig config;
      try {
        config = new BlobBasedConfig(null, or.repo, commit, GIT_MODULES);
      } catch (ConfigInvalidException e) {
        throw new IOException(
            "Could not read .gitmodules of super project: " + branch.project(), e);
      }
      subscriptions =
          new SubmoduleSectionParser(config, canonicalWebUrl, branch).parseAllSections();
    } catch (NoSuchProjectException e) {
      throw new IOException(e);
    }
  }

  Collection<SubmoduleSubscription> subscribedTo(Branch.NameKey src) {
    Collection<SubmoduleSubscription> ret = new ArrayList<>();
    for (SubmoduleSubscription s : subscriptions) {
      if (s.getSubmodule().equals(src)) {
        ret.add(s);
      }
    }
    return ret;
  }
}
