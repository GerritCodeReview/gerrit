// Copyright (C) 2011 The Android Open Source Project
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

import com.google.common.cache.Cache;
import com.google.gerrit.entities.Project;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.serialize.StringCacheSerializer;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectId;

@Singleton
public class TagCache {
  private static final String CACHE_NAME = "git_tags";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, String.class, TagSetHolder.class)
            .version(2)
            .keySerializer(StringCacheSerializer.INSTANCE)
            .valueSerializer(TagSetHolder.Serializer.INSTANCE);
        bind(TagCache.class);
      }
    };
  }

  private final Cache<String, TagSetHolder> cache;

  @Inject
  TagCache(@Named(CACHE_NAME) Cache<String, TagSetHolder> cache) {
    this.cache = cache;
  }

  /**
   * Advise the cache that a reference fast-forwarded.
   *
   * <p>This operation is not necessary, the cache will automatically detect changes made to
   * references and update itself on demand. However, this method may allow the cache to update more
   * quickly and reuse the caller's computation of the fast-forward status of a branch.
   *
   * @param name project the branch is contained in.
   * @param refName the branch name.
   * @param oldValue the old value, before the fast-forward. The cache will only update itself if it
   *     is still using this old value.
   * @param newValue the current value, after the fast-forward.
   */
  public void updateFastForward(
      Project.NameKey name, String refName, ObjectId oldValue, ObjectId newValue) {
    // Be really paranoid and null check everything. This method should
    // never fail with an exception. Some of these references can be null
    // (e.g. not all projects are cached, or the cache is not current).
    //
    TagSetHolder holder = cache.getIfPresent(name.get());
    if (holder != null) {
      TagSet tags = holder.getTagSet();
      if (tags != null) {
        if (tags.updateFastForward(refName, oldValue, newValue)) {
          cache.put(name.get(), holder);
        }
      }
    }
  }

  public TagSetHolder get(Project.NameKey name) {
    try {
      return cache.get(name.get(), () -> new TagSetHolder(name));
    } catch (ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }

  void put(Project.NameKey name, TagSetHolder tags) {
    cache.put(name.get(), tags);
  }
}
