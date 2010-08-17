// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;

import java.util.List;
import java.util.TreeMap;

/**
 * Keeps db changes list and repositories list to be used in search by commit
 * messages. It also get the changes represented by specific RevCommit's
 * retrieved in searching.
 */
public class SearchChangesInfo {

  private final TreeMap<String, Change> changes;
  private final List<Project.NameKey> repositories;

  public SearchChangesInfo(final TreeMap<String, Change> changes,
      final List<Project.NameKey> repositories) {
    this.changes = changes;
    this.repositories = repositories;
  }

  public TreeMap<String, Change> getFilteredChanges(
      final List<String> filterResult) {
    final TreeMap<String, Change> changesFiltered =
        new TreeMap<String, Change>();

    for (final String s : filterResult) {
      final Change change = changes.get(s);
      if (change != null) {
        changesFiltered.put(change.getSortKey(), change);
      }
    }

    return changesFiltered;
  }

  public Project.NameKey getNextRepo() {
    return repositories.get(0);
  }

  public int getReposCount() {
    return repositories.size();
  }

  public void removeCurrentRepo() {
    repositories.remove(0);
  }
}
