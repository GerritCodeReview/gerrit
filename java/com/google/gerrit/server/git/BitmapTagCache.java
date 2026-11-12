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
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/** Keeps, per tag, a bitmap of the refs that reach it. See {@link TagSet}. */
@Singleton
public class BitmapTagCache implements TagCache {
  private static final String CACHE_NAME = "git_tags";

  static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, String.class, TagSetHolder.class)
            .version(3)
            .keySerializer(StringCacheSerializer.INSTANCE)
            .valueSerializer(TagSetHolder.Serializer.INSTANCE);
        bind(TagCache.class).to(BitmapTagCache.class);
      }
    };
  }

  private final Cache<String, TagSetHolder> cache;

  @Inject
  BitmapTagCache(@Named(CACHE_NAME) Cache<String, TagSetHolder> cache) {
    this.cache = cache;
  }

  @Override
  public TagCache.Matcher matcher(
      Project.NameKey project, Repository repo, Collection<Ref> refs, Collection<Ref> tags) {
    return get(project).matcher(this, repo, refs);
  }

  TagSetHolder get(Project.NameKey name) {
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
