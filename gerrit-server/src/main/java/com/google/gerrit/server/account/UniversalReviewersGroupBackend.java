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

package com.google.gerrit.server.account;

import static com.google.gerrit.server.account.GroupBackends.GROUP_REF_NAME_COMPARATOR;

import com.google.common.collect.Sets;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Collection;
import java.util.Set;

@Singleton
public class UniversalReviewersGroupBackend {
  private final DynamicSet<ReviewersGroupBackend> backends;
  private final GroupBackend groupBackend;

  @Inject
  UniversalReviewersGroupBackend(DynamicSet<ReviewersGroupBackend> backends,
      GroupBackend groupBackend) {
    this.backends = backends;
    this.groupBackend = groupBackend;
  }

  public Collection<GroupReference> suggest(String name, ProjectControl project) {
    Set<GroupReference> groups = Sets.newTreeSet(GROUP_REF_NAME_COMPARATOR);
    groups.addAll(groupBackend.suggest(name, project));
    for (ReviewersGroupBackend g : backends) {
      groups.addAll(g.suggestReviewersGroup(name, project));
    }
    return groups;
  }
}
