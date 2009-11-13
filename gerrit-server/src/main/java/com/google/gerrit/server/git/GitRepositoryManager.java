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

package com.google.gerrit.server.git;

import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePath;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.LockFile;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.lib.WindowCache;
import org.eclipse.jgit.lib.WindowCacheConfig;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.util.NB;
import org.eclipse.jgit.util.RawParseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Class managing Git repositories. */
@Singleton
public class GitRepositoryManager {
  private static final Logger log = LoggerFactory.getLogger(GitRepositoryManager.class);

  private static final String UNNAMED =
      "Unnamed repository; edit this file to name it for gitweb.";

  public static class Lifecycle implements LifecycleListener {
    private final Config cfg;

    @Inject
    Lifecycle(@GerritServerConfig final Config cfg) {
      this.cfg = cfg;
    }

    @Override
    public void start() {
      final WindowCacheConfig c = new WindowCacheConfig();
      c.fromConfig(cfg);
      WindowCache.reconfigure(c);
    }

    @Override
    public void stop() {
    }
  }

  private final File sitePath;
  private final File basepath;

  @Inject
  GitRepositoryManager(@SitePath final File path, @GerritServerConfig final Config cfg) {
    sitePath = path;

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

  /** @return base directory under which all projects are stored. */
  public File getBasePath() {
    return basepath;
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
      if (!name.endsWith(".git")) {
        name = name + ".git";
      }
      final FileKey loc = FileKey.exact(new File(basepath, name));
      return RepositoryCache.open(loc, false);
    } catch (IOException e1) {
      final RepositoryNotFoundException e2;
      e2 = new RepositoryNotFoundException("Cannot open repository " + name);
      e2.initCause(e1);
      throw e2;
    }
  }

  /**
   * Read the {@code GIT_DIR/description} file for gitweb.
   * <p>
   * NB: This code should really be in JGit, as a member of the Repository
   * object. Until it moves there, its here.
   *
   * @param name the repository name, relative to the base directory.
   * @return description text; null if no description has been configured.
   * @throws RepositoryNotFoundException the named repository does not exist.
   * @throws IOException the description file exists, but is not readable by
   *         this process.
   */
  public String getProjectDescription(final String name)
      throws RepositoryNotFoundException, IOException {
    final Repository e = openRepository(name);
    final File d = new File(e.getDirectory(), "description");

    String description;
    try {
      description = RawParseUtils.decode(NB.readFully(d));
    } catch (FileNotFoundException err) {
      return null;
    }

    if (description != null) {
      description = description.trim();
      if (description.isEmpty()) {
        description = null;
      }
      if (UNNAMED.equals(description)) {
        description = null;
      }
    }
    return description;
  }

  /**
   * Set the {@code GIT_DIR/description} file for gitweb.
   * <p>
   * NB: This code should really be in JGit, as a member of the Repository
   * object. Until it moves there, its here.
   *
   * @param name the repository name, relative to the base directory.
   * @param description new description text for the repository.
   */
  public void setProjectDescription(final String name, final String description) {
    // Update git's description file, in case gitweb is being used
    //
    try {
      final Repository e;
      final LockFile f;

      e = openRepository(name);
      f = new LockFile(new File(e.getDirectory(), "description"));
      if (f.lock()) {
        String d = description;
        if (d != null) {
          d = d.trim();
          if (d.length() > 0) {
            d += "\n";
          }
        } else {
          d = "";
        }
        f.write(Constants.encode(d));
        f.commit();
      }
      e.close();
    } catch (RepositoryNotFoundException e) {
      log.error("Cannot update description for " + name, e);
    } catch (IOException e) {
      log.error("Cannot update description for " + name, e);
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

}
