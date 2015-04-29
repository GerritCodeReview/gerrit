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

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;

@AutoValue
public abstract class ChangeSet {
  public static ChangeSet create(Iterable<Change> changes) {
    ImmutableSet.Builder<Branch.NameKey> bb = ImmutableSet.builder();
    ImmutableSet.Builder<Change.Id> ib = ImmutableSet.builder();
    for (Change c : changes) {
      bb.add(c.getDest());
      ib.add(c.getId());
    }
    return new AutoValue_ChangeSet(
        bb.build(), ib.build());
  }
  abstract ImmutableSet<Branch.NameKey> branches();
  abstract ImmutableSet<Change.Id> ids();

  @Override
  public int hashCode() {
    return ids().hashCode();
  }
}