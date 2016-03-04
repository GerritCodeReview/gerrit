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
import com.google.gerrit.reviewdb.client.SubmoduleSubscription;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.util.SubmoduleSectionParser;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.BlobBasedConfig;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class GitModules {
  private static final Logger log = LoggerFactory.getLogger(GitModules.class);

  public interface Factory {
    GitModules create(Branch.NameKey project);
  }

  private static final String GIT_MODULES = ".gitmodules";

  private final Provider<String> urlProvider;
  private final GitRepositoryManager repoManager;
  private final SubmoduleSectionParser.Factory subSecParserFactory;
  private final Branch.NameKey branch;

  Set<SubmoduleSubscription> subscriptions;

  @AssistedInject
  GitModules(
      @CanonicalWebUrl @Nullable Provider<String> urlProvider,
      GitRepositoryManager repoManager,
      SubmoduleSectionParser.Factory subSecParserFactory,
      @Assisted Branch.NameKey branch) {
    this.repoManager = repoManager;
    this.urlProvider = urlProvider;
    this.subSecParserFactory = subSecParserFactory;
    this.branch = branch;
  }

  void load() throws SubmoduleException {
    try (Repository repo = repoManager.openRepository(branch.getParentKey());
        RevWalk rw = new RevWalk(repo)) {

      ObjectId id = repo.resolve(branch.get());
      if (id == null) {
        throw new IOException("Cannot open branch " + branch.get());
      }
      RevCommit commit = rw.parseCommit(id);

      TreeWalk tw = TreeWalk.forPath(repo, GIT_MODULES, commit.getTree());
      if (tw != null
          && (FileMode.REGULAR_FILE.equals(tw.getRawMode(0)) ||
              FileMode.EXECUTABLE_FILE.equals(tw.getRawMode(0)))) {
        BlobBasedConfig bbc =
            new BlobBasedConfig(null, repo, commit, GIT_MODULES);

        String thisServer = new URI(urlProvider.get()).getHost();
        subscriptions = subSecParserFactory.create(bbc, thisServer,
            branch).parseAllSections();
      }
    } catch (ConfigInvalidException | IOException e) {
      logAndThrowSubmoduleException(
          "Could not read .gitmodule file of super project: " +
              branch.getParentKey(), e);
    } catch (URISyntaxException e) {
      logAndThrowSubmoduleException("Incorrect Gerrit canonical web url " +
          "provided in gerrit.config file.", e);
    }
  }

  public Collection<SubmoduleSubscription> subscribedTo(Branch.NameKey src) {
    Collection<SubmoduleSubscription> ret = Collections.emptyList();
    for (SubmoduleSubscription s : subscriptions) {
      if (s.getSubmodule().equals(src)) {
        ret.add(s);
      }
    }
    return ret;
  }

  private static void logAndThrowSubmoduleException(final String errorMsg,
      final Exception e) throws SubmoduleException {
    log.error(errorMsg, e);
    throw new SubmoduleException(errorMsg, e);
  }
}
