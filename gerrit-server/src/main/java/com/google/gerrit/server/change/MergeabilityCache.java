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

import static com.google.gerrit.server.ioutil.BasicSerialization.readVarInt32;
import static com.google.gerrit.server.ioutil.BasicSerialization.writeVarInt32;
import static org.eclipse.jgit.lib.ObjectIdSerialization.readNotNull;
import static org.eclipse.jgit.lib.ObjectIdSerialization.writeNotNull;

import com.google.common.base.Optional;
import com.google.common.cache.Cache;
import com.google.common.cache.Weigher;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.google.inject.name.Names;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;

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
    return Key.get(new TypeLiteral<Cache<EntryKey, EntryVal>>() {},
        Names.named(CACHE_NAME));
  }

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, EntryKey.class, EntryVal.class)
            .maximumWeight(1 << 20)
            .weigher(MergeabilityWeigher.class);
        bind(MergeabilityCache.class);
      }
    };
  }

  private final Cache<EntryKey, EntryVal> cache;

  @Inject
  MergeabilityCache(@Named(CACHE_NAME) Cache<EntryKey, EntryVal> cache) {
    this.cache = cache;
  }

  public Optional<Boolean> get(Change c, @Nullable Ref ref) {
    return getImpl(c, ref != null ? ref.getObjectId() : ObjectId.zeroId());
  }

  public Optional<Boolean> get(Change c) {
    return getImpl(c, null);
  }

  private Optional<Boolean> getImpl(Change c, ObjectId tip) {
    if (c.getStatus() == Change.Status.MERGED) {
      return Optional.of(true);
    }
    EntryVal v = cache.getIfPresent(new EntryKey(c));
    // For null tip, return the mergeable bit without checking. Check against
    // a missing ref by passing zeroId.
    if (v != null && (tip == null || v.tip.equals(tip))) {
      return Optional.of(v.mergeable);
    } else {
      return Optional.absent();
    }
  }

  public void save(Change change, Ref branchTip, boolean mergeable) {
    cache.put(new EntryKey(change), new EntryVal(branchTip, mergeable));
  }

  private static class MergeabilityWeigher
      implements Weigher<EntryKey, EntryVal> {
    @Override
    public int weigh(EntryKey k, EntryVal v) {
      return 16 + 8 // Size of EntryKey, 64-bit JVM.
          + 16 + (16 + 20) + 4 + 1; // Size of EntryVal, 64-bit JVM.
    }
  }

  public static class EntryKey implements Serializable {
    private static final long serialVersionUID = 17L;

    private transient int changeId;
    private transient int psId;

    public EntryKey(Change c) {
      this.changeId = c.getId().get();
      this.psId = c.currentPatchSetId().get();
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof EntryKey) {
        EntryKey k = (EntryKey) o;
        return changeId == k.changeId
            && psId == k.psId;
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(changeId, psId);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
      writeVarInt32(out, changeId);
      writeVarInt32(out, psId);
    }

    private void readObject(ObjectInputStream in) throws IOException {
      changeId = readVarInt32(in);
      psId = readVarInt32(in);
    }
  }

  public static class EntryVal implements Serializable {
    private static final long serialVersionUID = 17L;

    private transient ObjectId tip;
    private transient boolean mergeable;

    public EntryVal(ObjectId tip, boolean mergeable) {
      this.tip = tip;
      this.mergeable = mergeable;
    }

    private EntryVal(Ref ref, boolean mergeable) {
      this(ref != null ? ref.getObjectId() : ObjectId.zeroId(), mergeable);
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
      writeNotNull(out, tip);
      out.writeBoolean(mergeable);
    }

    private void readObject(ObjectInputStream in) throws IOException {
      tip = readNotNull(in);
      mergeable = in.readBoolean();
    }
  }
}
