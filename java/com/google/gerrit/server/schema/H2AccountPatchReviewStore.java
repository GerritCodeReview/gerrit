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
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.regex.Pattern;
import org.eclipse.jgit.lib.Config;

@Singleton
public class H2AccountPatchReviewStore extends JdbcAccountPatchReviewStore {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final String H2_DB_URL_PREFIX = "jdbc:h2:file:";
  private static final String LOCK_TYPE_MARKER_SUFFIX = ".locktype";
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
    if (!h2Url.startsWith(H2_DB_URL_PREFIX)) {
      throw new IllegalArgumentException("Not a valid H2 file URL: " + h2Url);
    }

    // URL format: "jdbc:h2:file:/path/to/db" - where ";" in the path is escaped as "\;"
    String path = h2Url.substring(H2_DB_URL_PREFIX.length());

    // Split on first unescaped ";" to drop options, then unescape "\;" in the path
    return new File(
        Iterables.get(Splitter.on(Pattern.compile("(?<!\\\\);")).split(path), 0)
            .replace("\\;", ";"));
  }

  /**
   * Records the lock type next to the DB on first use and fails if the DB was already claimed by a
   * different lock type, since primaries using different locking would not exclude each other.
   *
   * <p>The marker is published by hard-linking a fully written temp file, which is atomic and fails
   * if the marker already exists, so a marker is never visible without its content.
   */
  static void checkLockTypeMarker(File dbFile, String lockType) {
    Path marker =
        dbFile
            .getAbsoluteFile()
            .toPath()
            .resolveSibling(dbFile.getName() + LOCK_TYPE_MARKER_SUFFIX);
    Path tmp = null;
    try {
      // Create the parent dir to support initial site init, when db/ does not exist yet.
      Files.createDirectories(marker.getParent());
      tmp = Files.createTempFile(marker.getParent(), dbFile.getName(), ".locktype.tmp");
      Files.writeString(tmp, lockType);
      try {
        Files.createLink(marker, tmp);
        return;
      } catch (FileAlreadyExistsException e) {
        // Another primary already claimed the DB; compare below.
      }
      String existing = Files.readString(marker).trim();
      if (!existing.equals(lockType)) {
        throw new IllegalStateException(
            String.format(
                "H2 account patch review DB %s is used with h2LockType=%s by other primaries"
                    + " (see %s), but this server is configured with h2LockType=%s. All primaries"
                    + " must use the same locking. To change it, stop all primaries and delete"
                    + " the marker file.",
                dbFile, existing, marker, lockType));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Cannot read or write lock type marker " + marker, e);
    } finally {
      if (tmp != null) {
        try {
          Files.deleteIfExists(tmp);
        } catch (IOException e) {
          logger.atWarning().withCause(e).log("Cannot delete temp file %s", tmp);
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
