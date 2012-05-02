// Copyright (C) 2012 The Android Open Source Project
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

import com.google.gerrit.common.data.GroupReference;

import java.util.Collection;
import java.util.Comparator;

import javax.annotation.Nullable;

/**
 * Utility class for dealing with a GroupBackend.
 */
public class GroupBackends {

  public static final Comparator<GroupReference> GROUP_REF_NAME_COMPARATOR =
      new Comparator<GroupReference>() {
    @Override
    public int compare(GroupReference a, GroupReference b) {
      return a.getName().compareTo(b.getName());
    }
  };

  /**
   * Runs {@link GroupBackend#suggest(String)} and filters the result to return
   * the best suggestion, or null if one does not exist.
   *
   * @param groupBackend the group backend
   * @param name the name for which to suggest groups
   * @return the best single GroupReference suggestion
   */
  @Nullable
  public static GroupReference findBestSuggestion(
      GroupBackend groupBackend, String name) {
    Collection<GroupReference> refs = groupBackend.suggest(name);
    if (refs.size() == 1) {
      return refs.iterator().next();
    }

    for (GroupReference ref : refs) {
      if (ref.getName().equals(name) || ref.getUUID().get().equals(name)) {
        return ref;
      }
    }
    return null;
  }

  private GroupBackends() {
  }
}
