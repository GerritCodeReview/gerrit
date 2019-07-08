// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.index.group;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.events.GroupIndexedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.index.Index;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

public class GroupIndexerImpl implements GroupIndexer {
  public interface Factory {
    GroupIndexerImpl create(GroupIndexCollection indexes);

    GroupIndexerImpl create(@Nullable GroupIndex index);
  }

  private final GroupCache groupCache;
  private final DynamicSet<GroupIndexedListener> indexedListener;
  private final GroupIndexCollection indexes;
  private final GroupIndex index;

  @AssistedInject
  GroupIndexerImpl(
      GroupCache groupCache,
      DynamicSet<GroupIndexedListener> indexedListener,
      @Assisted GroupIndexCollection indexes) {
    this.groupCache = groupCache;
    this.indexedListener = indexedListener;
    this.indexes = indexes;
    this.index = null;
  }

  @AssistedInject
  GroupIndexerImpl(
      GroupCache groupCache,
      DynamicSet<GroupIndexedListener> indexedListener,
      @Assisted GroupIndex index) {
    this.groupCache = groupCache;
    this.indexedListener = indexedListener;
    this.indexes = null;
    this.index = index;
  }

  @Override
  public void index(AccountGroup.UUID uuid) throws IOException {
    for (Index<?, AccountGroup> i : getWriteIndexes()) {
      i.replace(groupCache.get(uuid));
    }
    fireGroupIndexedEvent(uuid.get());
  }

  private void fireGroupIndexedEvent(String uuid) {
    for (GroupIndexedListener listener : indexedListener) {
      listener.onGroupIndexed(uuid);
    }
  }

  private Collection<GroupIndex> getWriteIndexes() {
    if (indexes != null) {
      return indexes.getWriteIndexes();
    }

    return index != null ? Collections.singleton(index) : ImmutableSet.of();
  }
}
