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

@Singleton
public class TagCache {
  private static final String CACHE_NAME = "git_tags";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, String.class, TagSetHolder.class)
            .version(4)
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
