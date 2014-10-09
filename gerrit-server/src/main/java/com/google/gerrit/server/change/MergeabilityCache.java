// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.server.ioutil.BasicSerialization.readVarInt32;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeVarInt32;
import static org.eclipse.jgit.lib.ObjectIdSerialization.readNotNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.writeNotNull;

import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.Weigher;
import com.google.gerrit.extensions.common.SubmitType;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Objects;

@Singleton
public class MergeabilityCache {
  public static final String CACHE_NAME = "mergeability";

  @SuppressWarnings("rawtypes")
  public static Key bindingKey() {
    return Key.get(new TypeLiteral<Cache<EntryKey, Boolean>>() {},
        Names.named(CACHE_NAME));
  }

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, EntryKey.class, Boolean.class)
            .maximumWeight(1 << 20)
            .weigher(MergeabilityWeigher.class);
        bind(MergeabilityCache.class);
      }
    };
  }

  private final Cache<EntryKey, Boolean> cache;

  @Inject
  MergeabilityCache(@Named(CACHE_NAME) Cache<EntryKey, Boolean> cache) {
    this.cache = cache;
  }

  public Optional<Boolean> get(ObjectId commit, ObjectId into,
      SubmitType submitType) {
    return Optional.fromNullable(
        cache.getIfPresent(new EntryKey(commit, into, submitType)));
  }

  public void save(ObjectId commit, ObjectId into, SubmitType submitType,
      boolean mergeable) {
    cache.put(new EntryKey(commit, into, submitType), mergeable);
  }

  private static class MergeabilityWeigher
      implements Weigher<EntryKey, Boolean> {
    @Override
    public int weigh(EntryKey k, Boolean v) {
      return 16 + 2 * (16 + 20) + 1 // Size of EntryKey, 64-bit JVM.
          + 1; // Size of Boolean.
    }
  }

  public static class EntryKey implements Serializable {
    private static final long serialVersionUID = 1L;

    private ObjectId commit;
    private ObjectId into;
    private SubmitType submitType;

    public EntryKey(ObjectId commit, ObjectId into, SubmitType submitType) {
      this.commit = checkNotNull(commit, "commit");
      this.into = checkNotNull(into, "into");
      this.submitType = checkNotNull(submitType, "submitType");
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof EntryKey) {
        EntryKey k = (EntryKey) o;
        return commit.equals(k.commit)
            && into.equals(k.into)
            && submitType == k.submitType;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(commit, into, submitType);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
      writeNotNull(out, commit);
      writeNotNull(out, into);
      writeVarInt32(out, submitType.ordinal());
    }

    private void readObject(ObjectInputStream in) throws IOException {
      commit = readNotNull(in);
      into = readNotNull(in);
      submitType = SubmitType.values()[readVarInt32(in)];
    }
  }
}
