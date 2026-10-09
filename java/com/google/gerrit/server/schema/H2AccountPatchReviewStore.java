// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.DuplicateKeyException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.config.ThreadSettingsConfig;
import com.google.gerrit.server.util.H2Url;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Config;

@Singleton
public class H2AccountPatchReviewStore extends JdbcAccountPatchReviewStore {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String LOCK_TYPE_MARKER = "locktype";
  static final String LOCK_TYPE_H2_BUILTIN = "h2-builtin-lock";
  private final String url;
  private final File dbFile;

  @Inject
  H2AccountPatchReviewStore(
      @GerritServerConfig Config cfg,
      SitePaths sitePaths,
      ThreadSettingsConfig threadSettingsConfig) {
    super(cfg, sitePaths, threadSettingsConfig);
    url = getUrl(cfg, sitePaths);
    dbFile = dbFileFromUrl(url);
  }

  protected H2AccountPatchReviewStore(String url) {
    super();
    this.url = url;
    dbFile = dbFileFromUrl(url);
  }

  @Override
  public void start() {
    checkLockTypeMarker(dbFile, lockType());
    super.start();
  }

  static File dbFileFromUrl(String h2Url) {
    String path;
    if (h2Url.startsWith(H2Url.ASYNC_PREFIX)) {
      path = h2Url.substring(H2Url.ASYNC_PREFIX.length());
    } else if (h2Url.startsWith(H2Url.FILE_PREFIX)) {
      path = h2Url.substring(H2Url.FILE_PREFIX.length());
    } else {
      throw new IllegalArgumentException("Not a valid H2 file URL: " + h2Url);
    }

    // Split on first unescaped ";" to drop options, then unescape "\;" in the path
    return new File(
        Iterables.get(Splitter.on(Pattern.compile("(?<!\\\\);")).split(path), 0)
            .replace("\\;", ";"));
  }

  /**
   * Records the lock type next to the DB on first use and fails if the DB was already claimed by a
   * different lock type, since primaries using different locking would not exclude each other.
   *
   * <p>The marker is a directory containing a single file. A claim is made by atomically renaming a
   * fully written temp directory over the (empty) marker directory. A rename onto an empty
   * directory succeeds, onto a non-empty one it fails, so exactly one primary wins and the marker
   * is never visible without its content.
   */
  static void checkLockTypeMarker(File dbFile, String lockType) {
    Path markerDir =
        dbFile
            .getAbsoluteFile()
            .toPath()
            .resolveSibling(dbFile.getName() + "." + LOCK_TYPE_MARKER);
    Path marker = markerDir.resolve(LOCK_TYPE_MARKER);
    Path tmpDir = null;
    try {
      // Create the parent dir to support initial site init, when db/ does not exist yet.
      Files.createDirectories(markerDir);
      tmpDir = Files.createTempDirectory(markerDir.getParent(), dbFile.getName() + ".locktype.tmp");
      Files.writeString(tmpDir.resolve(LOCK_TYPE_MARKER), lockType);
      try {
        Files.move(tmpDir, markerDir, StandardCopyOption.ATOMIC_MOVE);
        return;
      } catch (FileSystemException e) {
        // Fails if markerDir is not empty, i.e. another primary already claimed the DB. Any other
        // failure leaves no marker and is propagated.
        if (!Files.exists(marker)) {
          throw e;
        }
      }
      String existing = Files.readString(marker).trim();
      if (!existing.equals(lockType)) {
        throw new IllegalStateException(
            String.format(
                "H2 account patch review DB %s is used with h2LockType=%s by other primaries"
                    + " (see %s), but this server is configured with h2LockType=%s. All primaries"
                    + " must use the same locking. To change it, stop all primaries and delete"
                    + " the marker directory.",
                dbFile, existing, markerDir, lockType));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read or write lock type marker " + markerDir, e);
    } finally {
      if (tmpDir != null) {
        try {
          Files.deleteIfExists(tmpDir.resolve(LOCK_TYPE_MARKER));
          Files.deleteIfExists(tmpDir);
        } catch (IOException e) {
          logger.atWarning().withCause(e).log("Cannot delete temp dir %s", tmpDir);
        }
      }
    }
  }

  @Override
  public StorageException convertError(String op, SQLException err) {
    switch (getSQLStateInt(err)) {
      case 23001, 23505 -> {
        // UNIQUE CONSTRAINT VIOLATION
        // DUPLICATE_KEY_1
        return new DuplicateKeyException("account_patch_reviews", err);
      }
      default -> {
        if (err.getCause() == null && err.getNextException() != null) {
          err.initCause(err.getNextException());
        }
        return new StorageException(op + " failure on account_patch_reviews", err);
      }
    }
  }

  protected String lockType() {
    return LOCK_TYPE_H2_BUILTIN;
  }

  protected String getUrl() {
    return url;
  }

  protected File getDbFile() {
    return dbFile;
  }
}
