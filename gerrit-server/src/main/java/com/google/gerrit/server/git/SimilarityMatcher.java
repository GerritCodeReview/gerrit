// Copyright (C) 2016 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.git.SimilarityDecisionFunction.Features;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class SimilarityMatcher {
  @Inject
  SimilarityMatcher() {}

  Result matchChanges(List<RevCommit> commits, List<ChangeData> changes, Repository repo)
      throws IOException, OrmException {
    if (changes.isEmpty()) {
      return new Result(
          ImmutableList.copyOf(commits),
          ImmutableMap.<RevCommit, ChangeData>of(),
          ImmutableList.<ChangeData>of());
    }

    try (RevWalk walk = new RevWalk(repo)) {
      Map<ChangeData, Features> changeFeatures = new HashMap<>();
      for (ChangeData change : changes) {
        PatchSet ps = change.currentPatchSet();
        if (ps == null) {
          // Ignore the changes with no PatchSet.
          continue;
        }
        changeFeatures.put(
            change,
            new Features(
                walk, walk.parseCommit(ObjectId.fromString(ps.getRevision().get())), repo));
      }

      ImmutableList.Builder<RevCommit> newCommits = ImmutableList.builder();
      ImmutableMap.Builder<RevCommit, ChangeData> updateChanges = ImmutableMap.builder();
      for (RevCommit commit : commits) {
        Features commitFeatures = new Features(walk, commit, repo);

        ChangeData similarChange = null;
        for (Map.Entry<ChangeData, Features> entry : changeFeatures.entrySet()) {
          if (SimilarityDecisionFunction.INSTANCE.isSimilar(commitFeatures, entry.getValue())) {
            similarChange = entry.getKey();
            break;
          }
        }

        if (similarChange != null) {
          updateChanges.put(commit, similarChange);
          changeFeatures.remove(similarChange);
        } else {
          newCommits.add(commit);
        }
      }

      // Abandon the leftovers.
      return new Result(
          newCommits.build(), updateChanges.build(), ImmutableList.copyOf(changeFeatures.keySet()));
    }
  }

  static class Result {
    final List<RevCommit> newCommits;
    final Map<RevCommit, ChangeData> updateChanges;
    final List<ChangeData> abandonChanges;

    Result(
        List<RevCommit> newCommits,
        Map<RevCommit, ChangeData> updateChanges,
        List<ChangeData> abandonChanges) {
      this.newCommits = newCommits;
      this.updateChanges = updateChanges;
      this.abandonChanges = abandonChanges;
    }
  }
}
