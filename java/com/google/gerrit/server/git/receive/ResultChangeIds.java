// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.git.receive;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.reviewdb.client.Change;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Keeps track of the change IDs thus far updated by ReceiveCommit.
 *
 * <p>This class is thread-safe.
 */
public class ResultChangeIds {
  public enum Key {
    CREATED,
    REPLACED,
    AUTOCLOSED,
  }

  private final Map<Key, List<Change.Id>> ids;

  ResultChangeIds() {
    ids = new EnumMap<>(Key.class);
    for (Key k : Key.values()) {
      ids.put(k, new ArrayList<>());
    }
  }

  /** Record a change ID update as having completed. Thread-safe. */
  public void add(Key key, Change.Id id) {
    synchronized (this) {
      ids.get(key).add(id);
    }
  }

  /** Returns change IDs of the given type for which the BatchUpdate succeeded. Thread-safe. */
  public List<Change.Id> get(Key key) {
    synchronized (this) {
      return ImmutableList.copyOf(ids.get(key));
    }
  }
}
