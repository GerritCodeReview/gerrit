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

import com.google.gerrit.server.change.IncludedInResolver;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Reachable {
  private final VisibleRefFilter.Factory refFilter;
  private static final Logger log = LoggerFactory.getLogger(Reachable.class);

  @Inject
  Reachable(VisibleRefFilter.Factory refFilter) {
    this.refFilter = refFilter;
  }

  public boolean isReachableFrom(
      ProjectState state, Repository repo, RevCommit commit, Map<String, Ref> refs) {
    try (RevWalk rw = new RevWalk(repo)) {
      refs = refFilter.create(state, repo).filter(refs, true);
      return IncludedInResolver.includedInAny(repo, rw, commit, refs.values());
    } catch (IOException e) {
      log.error(
          String.format(
              "Cannot verify permissions to commit object %s in repository %s",
              commit.name(), state.getNameKey()),
          e);
      return false;
    }
  }
}
