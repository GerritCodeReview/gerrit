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
  }

  /** Record a change ID update as having completed. Thread-safe. */
  public synchronized void add(Key key, Change.Id id) {
    if (!ids.containsKey(key)) {
      ids.put(key, new ArrayList<>());
    }

    ids.get(key).add(id);
  }

  /** Indicate that the ReceiveCommits call involved a magic branch. */
  public synchronized void setMagicPush() {
    if (!ids.containsKey(Key.REPLACED)) {
      ids.put(Key.REPLACED, new ArrayList<>());
    }
    if (!ids.containsKey(Key.CREATED)) {
      ids.put(Key.CREATED, new ArrayList<>());
    }
  }

  /** Indicate that the ReceiveCommits call involved a regular branch. */
  public synchronized void setRegularPush() {
    if (!ids.containsKey(Key.AUTOCLOSED)) {
      ids.put(Key.AUTOCLOSED, new ArrayList<>());
    }
  }

  /** Returns the keys for which values were set. */
  public synchronized Iterable<Key> keys() {
    return ids.keySet();
  }

  /**
   * Returns change IDs of the given type for which the BatchUpdate succeeded, or empty list if
   * there are none. Thread-safe.
   */
  public synchronized List<Change.Id> get(Key key) {
    if (ids.containsKey(key)) {
      return ImmutableList.copyOf(ids.get(key));
    }
    return ImmutableList.of();
  }
}
