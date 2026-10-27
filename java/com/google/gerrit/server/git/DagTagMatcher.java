// Copyright (C) 2011 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.common.Nullable;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public class DagTagMatcher implements TagCache.Matcher {
  final Map<String, ObjectId> toUpdate = new HashMap<>();
  final DagTagSetHolder holder;
  final DagTagCache cache;
  final Repository db;
  final Collection<Ref> include;
  DagTagSet tagSet;
  @Nullable final Set<? extends ObjectId> reachableTags;

  DagTagMatcher(
      DagTagSetHolder holder,
      DagTagCache cache,
      Repository db,
      Collection<Ref> include,
      DagTagSet tagSet,
      Iterable<Ref> tags)
      throws IOException {
    this.holder = holder;
    this.cache = cache;
    this.db = db;
    this.include = include;
    this.tagSet = tagSet;
    reachableTags = tagSet.getReachableTags(db, this, include, tags);
  }

  private DagTagMatcher(DagTagMatcher m, Set<? extends ObjectId> reachableTags) {
    this.holder = m.holder;
    this.cache = m.cache;
    this.db = m.db;
    this.include = m.include;
    this.tagSet = m.tagSet;
    this.reachableTags = reachableTags;
  }

  /**
   * A matcher that also answers for the refs the DAG is not up to date for, by reading their
   * commits down to the DAG.
   */
  static DagTagMatcher readingCommitsOfOutOfDateRefs(DagTagMatcher m) throws IOException {
    return new DagTagMatcher(m, m.tagSet.getReachableTagsReadingCommits(m.db, m.include));
  }

  /** Whether the DAG knew all refs, so that {@link #isReachable(Ref)} can answer. */
  boolean isComplete() {
    return reachableTags != null;
  }

  @Override
  public boolean isReachable(Ref tagRef) throws IOException {
    checkState(isComplete(), "tag set of %s is out of date", holder.getProjectName());
    tagRef = db.getRefDatabase().peel(tagRef);
    ObjectId tagObj = tagRef.getPeeledObjectId();
    if (tagObj == null) {
      tagObj = tagRef.getObjectId();
      if (tagObj == null) {
        return false;
      }
    }

    return reachableTags.contains(tagObj);
  }
}
