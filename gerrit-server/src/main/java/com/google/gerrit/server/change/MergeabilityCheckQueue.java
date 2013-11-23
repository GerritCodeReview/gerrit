// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.collect.Sets;
import com.google.gerrit.reviewdb.client.Change;

import java.util.Collection;
import java.util.Set;

import javax.inject.Singleton;

@Singleton
class MergeabilityCheckQueue {
  private final Set<Change.Id> pending = Sets.newHashSet();

  synchronized Set<Change> addAll(Collection<Change> changes) {
    Set<Change> r = Sets.newLinkedHashSetWithExpectedSize(changes.size());
    for (Change c : changes) {
      if (pending.add(c.getId())) {
        r.add(c);
      }
    }
    return r;
  }

  synchronized void updatingMergeabilityFlag(Change change) {
    pending.remove(change.getId());
  }
}
