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

import java.io.File;
import java.io.IOException;
import java.util.function.BiFunction;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.util.FS;

/** A FileRepository with a dynamic RefDatabase supplied via a BiFunction. */
public class DynamicRefDbRepository extends FileRepository {
  public static class FileKey extends RepositoryCache.FileKey {
    private BiFunction<File, RefDatabase, RefDatabase> refDatabaseSupplier;

    public static FileKey lenient(
        File directory, FS fs, BiFunction<File, RefDatabase, RefDatabase> refDatabaseSupplier) {
      final File gitdir = resolve(directory, fs);
      return new FileKey(gitdir != null ? gitdir : directory, fs, refDatabaseSupplier);
    }

    private final FS fs;

    /**
     * @param directory exact location of the repository.
     * @param fs the file system abstraction which will be necessary to perform certain file system
     *     operations.
     */
    public FileKey(
        File directory, FS fs, BiFunction<File, RefDatabase, RefDatabase> refDatabaseSupplier) {
      super(canonical(directory), fs);
      this.fs = fs;
      this.refDatabaseSupplier = refDatabaseSupplier;
    }

    @Override
    public Repository open(boolean mustExist) throws IOException {
      if (mustExist && !isGitRepository(getFile(), fs))
        throw new RepositoryNotFoundException(getFile());
      return new DynamicRefDbRepository(getFile(), refDatabaseSupplier);
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
  private final BiFunction<File, RefDatabase, RefDatabase> refDatabaseSupplier;

  public DynamicRefDbRepository(
      File path, BiFunction<File, RefDatabase, RefDatabase> refDatabaseSupplier)
      throws IOException {
    super(path);
    this.path = path;
    this.refDatabaseSupplier = refDatabaseSupplier;
  }

  @Override
  public RefDatabase getRefDatabase() {
    return refDatabaseSupplier.apply(path, super.getRefDatabase());
  }
}
