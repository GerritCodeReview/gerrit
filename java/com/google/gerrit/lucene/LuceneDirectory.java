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

package com.google.gerrit.lucene;

import java.io.IOException;
import java.nio.file.Path;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockFactory;
import org.apache.lucene.store.NativeFSLockFactory;
import org.apache.lucene.store.SimpleFSLockFactory;
import org.eclipse.jgit.lib.Config;

/**
 * Opens the on-disk {@link Directory} of a Lucene index, using the lock implementation configured
 * for this site.
 */
final class LuceneDirectory {
  /** Value of the {@code index.lockFactory} setting. */
  enum LockFactoryType {
    /** Use {@link NativeFSLockFactory}. */
    NATIVE,

    /** Use {@link SimpleFSLockFactory}. */
    SIMPLE
  }

  static Directory open(Config cfg, Path path) throws IOException {
    return FSDirectory.open(path, lockFactory(cfg));
  }

  private static LockFactory lockFactory(Config cfg) {
    return switch (cfg.getEnum(
        LockFactoryType.values(), "index", null, "lockFactory", LockFactoryType.NATIVE)) {
      case NATIVE -> NativeFSLockFactory.INSTANCE;
      case SIMPLE -> SimpleFSLockFactory.INSTANCE;
    };
  }

  private LuceneDirectory() {}
}
