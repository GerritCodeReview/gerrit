// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.spearce.jgit.errors.RepositoryNotFoundException;
import org.spearce.jgit.lib.Config;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.RepositoryCache;
import org.spearce.jgit.lib.UserConfig;
import org.spearce.jgit.lib.RepositoryCache.FileKey;

import java.io.File;
import java.io.IOException;

/** Global server-side state for Gerrit. */
@Singleton
public class GerritServer {
  private final File sitePath;
  private final Config gerritConfigFile;
  private final File basepath;

  @Inject
  GerritServer(final SystemConfig sConfig, @SitePath final File path,
      @GerritServerConfig final Config cfg, final AuthConfig authConfig) {
    sitePath = path;
    gerritConfigFile = cfg;

    final String basePath = cfg.getString("gerrit", null, "basepath");
    if (basePath != null) {
      File root = new File(basePath);
      if (!root.isAbsolute()) {
        root = new File(sitePath, basePath);
      }
      basepath = root;
    } else {
      basepath = null;
    }
  }

  private Config getGerritConfig() {
    return gerritConfigFile;
  }

  /**
   * Get (or open) a repository by name.
   *
   * @param name the repository name, relative to the base directory.
   * @return the cached Repository instance. Caller must call {@code close()}
   *         when done to decrement the resource handle.
   * @throws RepositoryNotFoundException the name does not denote an existing
   *         repository, or the name cannot be read as a repository.
   */
  public Repository openRepository(String name)
      throws RepositoryNotFoundException {
    if (basepath == null) {
      throw new RepositoryNotFoundException("No gerrit.basepath configured");
    }

    if (isUnreasonableName(name)) {
      throw new RepositoryNotFoundException("Invalid name: " + name);
    }

    try {
      final FileKey loc = FileKey.lenient(new File(basepath, name));
      return RepositoryCache.open(loc);
    } catch (IOException e1) {
      final RepositoryNotFoundException e2;
      e2 = new RepositoryNotFoundException("Cannot open repository " + name);
      e2.initCause(e1);
      throw e2;
    }
  }

  /**
   * Create (and open) a repository by name.
   *
   * @param name the repository name, relative to the base directory.
   * @return the cached Repository instance. Caller must call {@code close()}
   *         when done to decrement the resource handle.
   * @throws RepositoryNotFoundException the name does not denote an existing
   *         repository, or the name cannot be read as a repository.
   */
  public Repository createRepository(String name)
      throws RepositoryNotFoundException {
    if (basepath == null) {
      throw new RepositoryNotFoundException("No gerrit.basepath configured");
    }

    if (isUnreasonableName(name)) {
      throw new RepositoryNotFoundException("Invalid name: " + name);
    }

    try {
      final FileKey loc = FileKey.exact(new File(basepath, name));
      return RepositoryCache.open(loc, false);
    } catch (IOException e1) {
      final RepositoryNotFoundException e2;
      e2 = new RepositoryNotFoundException("Cannot open repository " + name);
      e2.initCause(e1);
      throw e2;
    }
  }

  private boolean isUnreasonableName(final String name) {
    if (name.length() == 0) return true; // no empty paths

    if (name.indexOf('\\') >= 0) return true; // no windows/dos stlye paths
    if (name.charAt(0) == '/') return true; // no absolute paths
    if (new File(name).isAbsolute()) return true; // no absolute paths

    if (name.startsWith("../")) return true; // no "l../etc/passwd"
    if (name.contains("/../")) return true; // no "foo/../etc/passwd"
    if (name.contains("/./")) return true; // "foo/./foo" is insane to ask
    if (name.contains("//")) return true; // windows UNC path can be "//..."

    return false; // is a reasonable name
  }

  /** Get a new identity representing this Gerrit server in Git. */
  public PersonIdent newGerritPersonIdent() {
    String name = getGerritConfig().getString("user", null, "name");
    if (name == null) {
      name = "Gerrit Code Review";
    }
    String email = getGerritConfig().get(UserConfig.KEY).getCommitterEmail();
    if (email == null || email.length() == 0) {
      email = "gerrit@localhost";
    }
    return new PersonIdent(name, email);
  }
}
