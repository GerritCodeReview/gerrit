// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * This is a 'temporary' class for mapping sets of changes to the
 * set of branches required for merging.
 */
public class MergeOpMapper {
  public interface Factory {
    MergeOpMapper create(Set<Change> changes);
  }

  private final Set<Change> changes;
  private final Provider<MergeOp.Factory> bgFactory;

  @Inject
  MergeOpMapper(
      Provider<MergeOp.Factory> bgFactory,
      @Assisted Set<Change> changes) {
    this.bgFactory = bgFactory;
    this.changes = changes;
  }

  public void merge()
      throws MergeException, NoSuchChangeException, IOException {
    HashSet<Branch.NameKey> set = new HashSet<>();
    for (Change c : changes) {
      set.add(c.getDest());
    }
    for (Branch.NameKey branch : set) {
      bgFactory.get().create(branch).merge();
    }
  }
}