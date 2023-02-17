// Copyright (C) 2023 The Android Open Source Project
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

import com.google.gerrit.server.cache.PerThreadCache;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.internal.storage.file.RefDirectory;
import org.eclipse.jgit.internal.storage.file.SnapshotRefDirectory;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;

public class MyFileRepository extends FileRepository {
  public static class MyFileKey extends RepositoryCache.FileKey {
    public static MyFileKey lenient(File directory, FS fs) {
      final File gitdir = resolve(directory, fs);
      return new MyFileKey(gitdir != null ? gitdir : directory, fs);
    }

    private final FS fs;

    /**
     * @param directory exact location of the repository.
     * @param fs the file system abstraction which will be necessary to perform certain file system
     *     operations.
     */
    public MyFileKey(File directory, FS fs) {
      super(canonical(directory), fs);
      this.fs = fs;
    }

    @Override
    public Repository open(boolean mustExist) throws IOException {
      if (mustExist && !isGitRepository(getFile(), fs))
        throw new RepositoryNotFoundException(getFile());
      return new MyFileRepository(getFile());
    }

    private static File canonical(File path) {
      try {
        return path.getCanonicalFile();
      } catch (IOException e) {
        return path.getAbsoluteFile();
      }
    }
  }

  private final File path;

  public MyFileRepository(File path) throws IOException {
    super(path);
    this.path = path;
  }

  @Override
  public RefDatabase getRefDatabase() {
    if (isPerRequestRefCache() && PerThreadCache.get() != null) {
      PerThreadRefDbCache perThreadRefDbCache = PerThreadRefDbCache.get(PerThreadCache.get());
      return perThreadRefDbCache.computeIfAbsent(
          path, (path) -> new SnapshotRefDirectory((RefDirectory) super.getRefDatabase()));
    }
    return super.getRefDatabase();
  }

  private boolean isPerRequestRefCache() {
    return true;
  }

  protected static class PerThreadRefDbCache {
    protected static final PerThreadCache.Key<PerThreadRefDbCache> REFDB_CACHE_KEY =
        PerThreadCache.Key.create(PerThreadRefDbCache.class);

    Map<File, RefDatabase> refDbByPath = new HashMap<>();

    private PerThreadRefDbCache() {}

    static PerThreadRefDbCache get(PerThreadCache perThreadCache) {
      return perThreadCache.get(REFDB_CACHE_KEY, PerThreadRefDbCache::new);
    }

    public RefDatabase computeIfAbsent(
        File path, Function<? super File, ? extends RefDatabase> mappingFunction) {
      return refDbByPath.computeIfAbsent(path, mappingFunction);
    }

    public void evict() {}
  }
}
