// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd;

import static java.nio.file.Files.isExecutable;
import static java.nio.file.Files.isRegularFile;

import com.google.gerrit.common.data.GitWebType;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

public class GitWebConfig {
  private static final Logger log = LoggerFactory.getLogger(GitWebConfig.class);

  private final String url;
  private final Path gitweb_cgi;
  private final Path gitweb_css;
  private final Path gitweb_js;
  private final Path git_logo_png;
  private GitWebType type;

  @Inject
  GitWebConfig(final SitePaths sitePaths, @GerritServerConfig final Config cfg) {
    final String cfgUrl = cfg.getString("gitweb", null, "url");
    final String cfgCgi = cfg.getString("gitweb", null, "cgi");

    type = GitWebType.fromName(cfg.getString("gitweb", null, "type"));
    if (type == null) {
      url = null;
      gitweb_cgi = null;
      gitweb_css = null;
      gitweb_js = null;
      git_logo_png = null;
      return;
    }

    type.setLinkName(cfg.getString("gitweb", null, "linkname"));
    type.setBranch(cfg.getString("gitweb", null, "branch"));
    type.setProject(cfg.getString("gitweb", null, "project"));
    type.setRevision(cfg.getString("gitweb", null, "revision"));
    type.setRootTree(cfg.getString("gitweb", null, "roottree"));
    type.setFile(cfg.getString("gitweb", null, "file"));
    type.setFileHistory(cfg.getString("gitweb", null, "filehistory"));
    type.setLinkDrafts(cfg.getBoolean("gitweb", null, "linkdrafts", true));
    type.setUrlEncode(cfg.getBoolean("gitweb", null, "urlencode", true));
    String pathSeparator = cfg.getString("gitweb", null, "pathSeparator");
    if (pathSeparator != null) {
      if (pathSeparator.length() == 1) {
        char c = pathSeparator.charAt(0);
        if (isValidPathSeparator(c)) {
          type.setPathSeparator(c);
        } else {
          log.warn("Invalid value specified for gitweb.pathSeparator: " + c);
        }
      } else {
        log.warn("Value specified for gitweb.pathSeparator is not a single character:" + pathSeparator);
      }
    }

    if (type.getBranch() == null) {
      log.warn("No Pattern specified for gitweb.branch, disabling.");
      type = null;
    } else if (type.getProject() == null) {
      log.warn("No Pattern specified for gitweb.project, disabling.");
      type = null;
    } else if (type.getRevision() == null) {
      log.warn("No Pattern specified for gitweb.revision, disabling.");
      type = null;
    } else if (type.getRootTree() == null) {
      log.warn("No Pattern specified for gitweb.roottree, disabling.");
      type = null;
    } else if (type.getFile() == null) {
      log.warn("No Pattern specified for gitweb.file, disabling.");
      type = null;
    } else if (type.getFileHistory() == null) {
      log.warn("No Pattern specified for gitweb.filehistory, disabling.");
      type = null;
    }

    if ((cfgUrl != null && cfgUrl.isEmpty())
        || (cfgCgi != null && cfgCgi.isEmpty())) {
      // Either setting was explicitly set to the empty string disabling
      // gitweb for this server. Disable the configuration.
      //
      url = null;
      gitweb_cgi = null;
      gitweb_css = null;
      gitweb_js = null;
      git_logo_png = null;
      return;
    }

    if ((cfgUrl != null) && (cfgCgi == null || cfgCgi.isEmpty())) {
      // Use an externally managed gitweb instance, and not an internal one.
      //
      url = cfgUrl;
      gitweb_cgi = null;
      gitweb_css = null;
      gitweb_js = null;
      git_logo_png = null;
      return;
    }

    final Path pkgCgi = Paths.get("/usr/lib/cgi-bin/gitweb.cgi");
    String[] resourcePaths = {"/usr/share/gitweb/static", "/usr/share/gitweb",
        "/var/www/static", "/var/www"};
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

    if (cfgUrl == null || cfgUrl.isEmpty()) {
      url = cgi != null ? "gitweb" : null;
    } else {
      url = cgi != null ? cfgUrl : null;
    }
    gitweb_cgi = cgi;
    gitweb_css = css;
    gitweb_js = js;
    git_logo_png = logo;
  }

  /** @return GitWebType for gitweb viewer. */
  public GitWebType getGitWebType() {
    return type;
  }

  /**
   * @return URL of the entry point into gitweb. This URL may be relative to our
   *         context if gitweb is hosted by ourselves; or absolute if its hosted
   *         elsewhere; or null if gitweb has not been configured.
   */
  public String getUrl() {
    return url;
  }

  /** @return local path to the CGI executable; null if we shouldn't execute. */
  public Path getGitwebCGI() {
    return gitweb_cgi;
  }

  /** @return local path of the {@code gitweb.css} matching the CGI. */
  public Path getGitwebCSS() {
    return gitweb_css;
  }

  /** @return local path of the {@code gitweb.js} for the CGI. */
  public Path getGitwebJS() {
    return gitweb_js;
  }

  /** @return local path of the {@code git-logo.png} for the CGI. */
  public Path getGitLogoPNG() {
    return git_logo_png;
  }

  /**
   * Determines if a given character can be used unencoded in an URL as a
   * replacement for the path separator '/'.
   *
   * Reasoning: http://www.ietf.org/rfc/rfc1738.txt § 2.2:
   *
   * ... only alphanumerics, the special characters "$-_.+!*'(),", and
   *  reserved characters used for their reserved purposes may be used
   * unencoded within a URL.
   *
   * The following characters might occur in file names, however:
   *
   * alphanumeric characters,
   *
   * "$-_.+!',"
   */
  static boolean isValidPathSeparator(char c) {
    switch (c) {
      case '*':
      case '(':
      case ')':
        return true;
      default:
        return false;
    }
  }
}
