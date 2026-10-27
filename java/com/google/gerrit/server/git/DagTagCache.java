// Copyright (C) 2026 The Android Open Source Project
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
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

/**
 * Keeps a DAG of the commits that refs point to, and walks it from the visible refs on each lookup.
 * See {@link DagTagSet}.
 */
@Singleton
public class DagTagCache implements TagCache {
  private static final String CACHE_NAME = "git_tags_dag";

  static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        persist(CACHE_NAME, String.class, DagTagSetHolder.class)
            .version(1)
            .keySerializer(StringCacheSerializer.INSTANCE)
            .valueSerializer(DagTagSetHolder.Serializer.INSTANCE);
        bind(TagCache.class).to(DagTagCache.class);
      }
    };
  }

  private final Cache<String, DagTagSetHolder> cache;

  @Inject
  DagTagCache(@Named(CACHE_NAME) Cache<String, DagTagSetHolder> cache) {
    this.cache = cache;
  }

  @Override
  public TagCache.Matcher matcher(
      Project.NameKey project, Repository repo, Collection<Ref> refs, Collection<Ref> tags)
      throws IOException {
    return get(project).matcher(this, repo, refs, tags);
  }

  DagTagSetHolder get(Project.NameKey name) {
    try {
      return cache.get(name.get(), () -> new DagTagSetHolder(name));
    } catch (ExecutionException e) {
      throw new IllegalStateException(e);
    }
  }

  void put(Project.NameKey name, DagTagSetHolder tags) {
    cache.put(name.get(), tags);
  }
}
