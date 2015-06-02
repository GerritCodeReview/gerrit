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
import com.google.gerrit.reviewdb.client.Project;

/**
 * A ChangeSet is a set of changes grouped together to be submitted atomically.
 */
@AutoValue
public abstract class ChangeSet {
  public static ChangeSet create(Iterable<Change> changes) {
    ImmutableSet.Builder<Project.NameKey> rb = ImmutableSet.builder();
    ImmutableSet.Builder<Branch.NameKey> bb = ImmutableSet.builder();
    ImmutableSet.Builder<Change.Id> ib = ImmutableSet.builder();
    for (Change c : changes) {
      rb.add(c.getDest().getParentKey());
      bb.add(c.getDest());
      ib.add(c.getId());
    }
    return new AutoValue_ChangeSet(rb.build(), bb.build(), ib.build());
  }

  public static ChangeSet create(Change change) {
    ImmutableSet.Builder<Project.NameKey> rb = ImmutableSet.builder();
    ImmutableSet.Builder<Branch.NameKey> bb = ImmutableSet.builder();
    ImmutableSet.Builder<Change.Id> ib = ImmutableSet.builder();
    rb.add(change.getDest().getParentKey());
    bb.add(change.getDest());
    ib.add(change.getId());
    return new AutoValue_ChangeSet(rb.build(), bb.build(), ib.build());
  }

  public abstract ImmutableSet<Project.NameKey> repos();
  public abstract ImmutableSet<Branch.NameKey> branches();
  public abstract ImmutableSet<Change.Id> ids();

  @Override
  public int hashCode() {
    return ids().hashCode();
  }
}