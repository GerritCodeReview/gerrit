// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.patch.gitdiff;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.proto.Protos;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.cache.proto.Cache.ModifiedFilesProto;
import com.google.gerrit.server.cache.serialize.CacheSerializer;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

/** Implementation of the {@link GitModifiedFilesCache} */
@Singleton
public class GitModifiedFilesCacheImpl implements GitModifiedFilesCache {
  private static final String GIT_MODIFIED_FILES = "git_modified_files";

  private LoadingCache<GitModifiedFilesCacheKey, ImmutableList<ModifiedFile>> cache;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(GitModifiedFilesCache.class).to(GitModifiedFilesCacheImpl.class);

        persist(
                GIT_MODIFIED_FILES,
                GitModifiedFilesCacheKey.class,
                new TypeLiteral<ImmutableList<ModifiedFile>>() {})
            .keySerializer(GitModifiedFilesCacheKey.Serializer.INSTANCE)
            .valueSerializer(ValueSerializer.INSTANCE)
            // The documentation has some defaults and recommendations for setting the cache
            // attributes:
            // https://gerrit-review.googlesource.com/Documentation/config-gerrit.html#cache.
            .maximumWeight(10 << 20)
            .weigher(GitModifiedFilesWeigher.class)
            // The cache is using the default disk limit as per section cache.<name>.diskLimit
            // in the cache documentation link.
            .version(2)
            .loader(GitModifiedFilesCacheImpl.Loader.class);
      }
    };
  }

  @Inject
  public GitModifiedFilesCacheImpl(
      @Named(GIT_MODIFIED_FILES)
          LoadingCache<GitModifiedFilesCacheKey, ImmutableList<ModifiedFile>> cache) {
    this.cache = cache;
  }

  @Override
  public ImmutableList<ModifiedFile> get(GitModifiedFilesCacheKey key)
      throws DiffNotAvailableException {
    try {
      return cache.get(key);
    } catch (ExecutionException e) {
      throw new DiffNotAvailableException(e);
    }
  }

  static class Loader extends CacheLoader<GitModifiedFilesCacheKey, ImmutableList<ModifiedFile>> {
    private final GitRepositoryManager repoManager;

    @Inject
    Loader(GitRepositoryManager repoManager) {
      this.repoManager = repoManager;
    }

    @Override
    public ImmutableList<ModifiedFile> load(GitModifiedFilesCacheKey key) throws IOException {
      try (Repository repo = repoManager.openRepository(key.project());
          ObjectReader reader = repo.newObjectReader()) {
        GitModifiedFilesLoader loader = new GitModifiedFilesLoader();
        if (key.renameDetection()) {
          loader.withRenameDetection(key.renameScore());
        }
        return loader.load(repo.getConfig(), reader, key.aTree(), key.bTree());
      }
    }
  }

  public enum ValueSerializer implements CacheSerializer<ImmutableList<ModifiedFile>> {
    INSTANCE;

    @Override
    public byte[] serialize(ImmutableList<ModifiedFile> modifiedFiles) {
      ModifiedFilesProto.Builder builder = ModifiedFilesProto.newBuilder();
      modifiedFiles.forEach(
          f -> builder.addModifiedFile(ModifiedFile.Serializer.INSTANCE.toProto(f)));
      return Protos.toByteArray(builder.build());
    }

    @Override
    public ImmutableList<ModifiedFile> deserialize(byte[] in) {
      ImmutableList.Builder<ModifiedFile> modifiedFiles = ImmutableList.builder();
      ModifiedFilesProto modifiedFilesProto =
          Protos.parseUnchecked(ModifiedFilesProto.parser(), in);
      modifiedFilesProto
          .getModifiedFileList()
          .forEach(f -> modifiedFiles.add(ModifiedFile.Serializer.INSTANCE.fromProto(f)));
      return modifiedFiles.build();
    }
  }
}
