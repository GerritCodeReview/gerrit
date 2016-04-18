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

package com.google.gerrit.server.git;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.git.MergeOp.OpenRepo;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.util.SubmoduleSectionParser;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * Loads the .gitmodules file of the specified project/branch.
 * It can be queried which submodules this branch is subscribed to.
 */
public class GitModules {
  private static final Logger log = LoggerFactory.getLogger(GitModules.class);

  public interface Factory {
    GitModules create(Branch.NameKey project, String submissionId, MergeOp m);
  }

  private static final String GIT_MODULES = ".gitmodules";

  private final String thisServer;
  private final SubmoduleSectionParser.Factory subSecParserFactory;
  private final Branch.NameKey branch;
  private final String submissionId;
  private final MergeOp mergeOp;

  Set<SubmoduleSubscription> subscriptions;

  @AssistedInject
  GitModules(
      @CanonicalWebUrl @Nullable String canonicalWebUrl,
      SubmoduleSectionParser.Factory subSecParserFactory,
      @Assisted Branch.NameKey branch,
      @Assisted String submissionId,
      @Assisted MergeOp mergeOp) throws SubmoduleException {
    this.subSecParserFactory = subSecParserFactory;
    this.mergeOp = mergeOp;
    this.branch = branch;
    this.submissionId = submissionId;
    try {
      this.thisServer = new URI(canonicalWebUrl).getHost();
    } catch (URISyntaxException e) {
      throw new SubmoduleException("Incorrect Gerrit canonical web url " +
          "provided in gerrit.config file.", e);
    }
  }

  void load() throws IOException {
    Project.NameKey project = branch.getParentKey();
    logDebug("Loading .gitmodules of {} for project {}", branch, project);
    try {
      mergeOp.openRepo(project, false);
    } catch (NoSuchProjectException e) {
      throw new IOException(e);
    }
    OpenRepo or = mergeOp.getRepo(project);

    ObjectId id = or.repo.resolve(branch.get());
    if (id == null) {
      throw new IOException("Cannot open branch " + branch.get());
    }
    RevCommit commit = or.rw.parseCommit(id);

    TreeWalk tw = TreeWalk.forPath(or.repo, GIT_MODULES, commit.getTree());
    if (tw == null
        || (tw.getRawMode(0) & FileMode.TYPE_MASK) != FileMode.TYPE_FILE) {
      return;
    }
    try {
      BlobBasedConfig bbc =
          new BlobBasedConfig(null, or.repo, commit, GIT_MODULES);
      subscriptions = subSecParserFactory.create(bbc, thisServer,
          branch).parseAllSections();
    } catch (ConfigInvalidException e) {
      throw new IOException(
          "Could not read .gitmodule file of super project: " +
              branch.getParentKey(), e);
    }
  }

  public Collection<SubmoduleSubscription> subscribedTo(Branch.NameKey src) {
    logDebug("Checking for a subscription of " + src);
    Collection<SubmoduleSubscription> ret = new ArrayList<>();
    for (SubmoduleSubscription s : subscriptions) {
      if (s.getSubmodule().equals(src)) {
        logDebug("Found " + s);
        ret.add(s);
      }
    }
    return ret;
  }

  private void logDebug(String msg, Object... args) {
    if (log.isDebugEnabled()) {
      log.debug("[" + submissionId + "]" + msg, args);
    }
  }
}
