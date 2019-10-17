// Copyright (C) 2019 The Android Open Source Project
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

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.RefNames;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;

/**
 * Simple cache for accessing refs by name, prefix or {@link ObjectId}. Intended to be used when
 * processing a {@code git push}.
 *
 * <p>This class is not thread safe.
 */
public interface ReceivePackRefCache {

  /**
   * Returns an instance that delegates all calls to the provided {@link RefDatabase}. To be used in
   * tests or when the ref database is fast with forward (name to {@code ObjectId}) and inverse
   * ({@code ObjectId} to name) lookups.
   */
  static ReceivePackRefCache noCache(RefDatabase delegate) {
    return new NoCache(delegate);
  }

  /**
   * Returns an instance that answers calls based on refs previously advertised and captured in
   * {@link AllRefsWatcher}. Speeds up inverse lookups by building a {@code Map<ObjectId,
   * List<Ref>>} and a {@code Map<Change.Id, List<Ref>>}.
   *
   * <p>This implementation speeds up lookups when the ref database does not support inverse ({@code
   * ObjectId} to name) lookups.
   */
  static ReceivePackRefCache withAdvertisedRefs(Supplier<Map<String, Ref>> allRefsSupplier) {
    return new WithAdvertisedRefs(allRefsSupplier);
  }

  /** Returns a list of refs whose name starts with {@code prefix} that point to {@code id}. */
  ImmutableList<Ref> tipsFromObjectId(ObjectId id, @Nullable String prefix) throws IOException;

  /** Returns all refs whose name starts with {@code prefix}. */
  ImmutableList<Ref> byPrefix(String prefix) throws IOException;

  /** Returns a ref whose name matches {@code ref} or {@code null} if such a ref does not exist. */
  @Nullable
  Ref exactRef(String ref) throws IOException;

  class NoCache implements ReceivePackRefCache {
    private final RefDatabase delegate;

    NoCache(RefDatabase delegate) {
      this.delegate = delegate;
    }

    @Override
    public ImmutableList<Ref> tipsFromObjectId(ObjectId id, @Nullable String prefix)
        throws IOException {
      return delegate.getTipsWithSha1(id).stream()
          .filter(r -> prefix == null || r.getName().startsWith(prefix))
          .collect(toImmutableList());
    }

    @Override
    public ImmutableList<Ref> byPrefix(String prefix) throws IOException {
      return delegate.getRefsByPrefix(prefix).stream().collect(toImmutableList());
    }

    @Override
    @Nullable
    public Ref exactRef(String name) throws IOException {
      return delegate.exactRef(name);
    }
  }

  class WithAdvertisedRefs implements ReceivePackRefCache {
    private final Supplier<Map<String, Ref>> allRefsSupplier;

    // Collections lazily populated during processing.
    private Map<String, Ref> allRefs;
    private ListMultimap<Change.Id, Ref> refsByChange; // Contains only patch set refs
    private ListMultimap<ObjectId, Ref> refsByObjectId; // Contains all refs

    WithAdvertisedRefs(Supplier<Map<String, Ref>> allRefsSupplier) {
      this.allRefsSupplier = allRefsSupplier;
    }

    @Override
    public ImmutableList<Ref> tipsFromObjectId(ObjectId id, String prefix) {
      initRefMaps();
      return refsByObjectId.get(id).stream()
          .filter(r -> prefix == null || r.getName().startsWith(prefix))
          .collect(toImmutableList());
    }

    @Override
    public ImmutableList<Ref> byPrefix(String prefix) {
      initRefMaps();
      if (RefNames.isRefsChanges(prefix)) {
        Change.Id cId = Change.Id.fromRefPart(prefix);
        if (cId != null) {
          return refsByChange.get(cId).stream()
              .filter(r -> r.getName().startsWith(prefix))
              .collect(toImmutableList());
        }
      }
      return allRefs().values().stream()
          .filter(r -> r.getName().startsWith(prefix))
          .collect(toImmutableList());
    }

    @Override
    @Nullable
    public Ref exactRef(String name) {
      return allRefs().get(name);
    }

    private Map<String, Ref> allRefs() {
      if (allRefs == null) {
        allRefs = allRefsSupplier.get();
      }
      return allRefs;
    }

    private void initRefMaps() {
      if (refsByChange != null) {
        return;
      }

      int estRefsPerChange = 5; // Expect 4 patch sets and the meta ref
      refsByObjectId = MultimapBuilder.hashKeys().arrayListValues().build();
      refsByChange =
          MultimapBuilder.hashKeys(allRefs().size() / estRefsPerChange)
              .arrayListValues(estRefsPerChange)
              .build();
      for (Ref ref : allRefs().values()) {
        ObjectId obj = ref.getObjectId();
        if (obj != null) {
          refsByObjectId.put(obj, ref);
          Change.Id cId = Change.Id.fromRef(ref.getName());
          if (cId != null) {
            refsByChange.put(cId, ref);
          }
        }
      }
    }
  }
}
