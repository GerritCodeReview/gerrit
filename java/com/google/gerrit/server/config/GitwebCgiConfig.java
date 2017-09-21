// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.config;

import static java.nio.file.Files.isExecutable;
import static java.nio.file.Files.isRegularFile;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class GitwebCgiConfig {
  private static final Logger log = LoggerFactory.getLogger(GitwebCgiConfig.class);

  public GitwebCgiConfig disabled() {
    return new GitwebCgiConfig();
  }

  private final Path cgi;
  private final Path css;
  private final Path js;
  private final Path logoPng;

  @Inject
  GitwebCgiConfig(SitePaths sitePaths, @GerritServerConfig Config cfg) {
    if (GitwebConfig.isDisabled(cfg)) {
      cgi = null;
      css = null;
      js = null;
      logoPng = null;
      return;
    }

    String cfgCgi = cfg.getString("gitweb", null, "cgi");
    Path pkgCgi = Paths.get("/usr/lib/cgi-bin/gitweb.cgi");
    String[] resourcePaths = {
      "/usr/share/gitweb/static", "/usr/share/gitweb", "/var/www/static", "/var/www",
    };
    Path cgi;

    if (cfgCgi != null) {
      // Use the CGI script configured by the administrator, failing if it
      // cannot be used as specified.
      //
      cgi = sitePaths.resolve(cfgCgi);
      if (!isRegularFile(cgi)) {
        throw new IllegalStateException("Cannot find gitweb.cgi: " + cgi);
      }
      if (!isExecutable(cgi)) {
        throw new IllegalStateException("Cannot execute gitweb.cgi: " + cgi);
      }

      if (!cgi.equals(pkgCgi)) {
        // Assume the administrator pointed us to the distribution,
        // which also has the corresponding CSS and logo file.
        //
        String absPath = cgi.getParent().toAbsolutePath().toString();
        resourcePaths = new String[] {absPath + "/static", absPath};
      }

    } else if (cfg.getString("gitweb", null, "url") != null) {
      // Use an externally managed gitweb instance, and not an internal one.
      //
      cgi = null;
      resourcePaths = new String[] {};

    } else if (isRegularFile(pkgCgi) && isExecutable(pkgCgi)) {
      // Use the OS packaged CGI.
      //
      log.debug("Assuming gitweb at " + pkgCgi);
      cgi = pkgCgi;

    } else {
      log.warn("gitweb not installed (no " + pkgCgi + " found)");
      cgi = null;
      resourcePaths = new String[] {};
    }

    Path css = null;
    Path js = null;
    Path logo = null;
    for (String path : resourcePaths) {
      Path dir = Paths.get(path);
      css = dir.resolve("gitweb.css");
      js = dir.resolve("gitweb.js");
      logo = dir.resolve("git-logo.png");
      if (isRegularFile(css) && isRegularFile(logo)) {
        break;
      }
    }

    this.cgi = cgi;
    this.css = css;
    this.js = js;
    this.logoPng = logo;
  }

  private GitwebCgiConfig() {
    this.cgi = null;
    this.css = null;
    this.js = null;
    this.logoPng = null;
  }

  /** @return local path to the CGI executable; null if we shouldn't execute. */
  public Path getGitwebCgi() {
    return cgi;
  }

  /** @return local path of the {@code gitweb.css} matching the CGI. */
  public Path getGitwebCss() {
    return css;
  }

  /** @return local path of the {@code gitweb.js} for the CGI. */
  public Path getGitwebJs() {
    return js;
  }

  /** @return local path of the {@code git-logo.png} for the CGI. */
  public Path getGitLogoPng() {
    return logoPng;
  }
}
