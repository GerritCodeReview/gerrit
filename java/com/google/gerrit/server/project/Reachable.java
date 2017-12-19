// Copyright (C) 2017 The Android Open Source Project
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

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.gerrit.server.change.IncludedInResolver;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Report whether a commit is reachable from a set of commits. This is used for checking if a user
 * has read permissions on a commit.
 */
public class Reachable {
  private final VisibleRefFilter.Factory refFilter;
  private static final Logger log = LoggerFactory.getLogger(Reachable.class);

  @Inject
  Reachable(VisibleRefFilter.Factory refFilter) {
    this.refFilter = refFilter;
  }

  /** @return true if a commit is reachable from a given set of refs. */
  public boolean fromRefs(
      ProjectState state, Repository repo, RevCommit commit, Map<String, Ref> refs) {
    try (RevWalk rw = new RevWalk(repo)) {
      Map<String, Ref> filtered = refFilter.create(state, repo).filter(refs, true);
      return IncludedInResolver.includedInAny(repo, rw, commit, filtered.values());
    } catch (IOException e) {
      log.error(
          String.format(
              "Cannot verify permissions to commit object %s in repository %s",
              commit.name(), state.getNameKey()),
          e);
      return false;
    }
  }

  /** @return true if a commit is reachable from a repo's branches and tags. */
  boolean fromHeadsOrTags(ProjectState state, Repository repo, RevCommit commit) {
    try {
      RefDatabase refdb = repo.getRefDatabase();
      Collection<Ref> heads = refdb.getRefs(Constants.R_HEADS).values();
      Collection<Ref> tags = refdb.getRefs(Constants.R_TAGS).values();
      Map<String, Ref> refs = Maps.newHashMapWithExpectedSize(heads.size() + tags.size());
      for (Ref r : Iterables.concat(heads, tags)) {
        refs.put(r.getName(), r);
      }
      return fromRefs(state, repo, commit, refs);
    } catch (IOException e) {
      log.error(
          String.format(
              "Cannot verify permissions to commit object %s in repository %s",
              commit.name(), state.getProject().getNameKey()),
          e);
      return false;
    }
  }
}
