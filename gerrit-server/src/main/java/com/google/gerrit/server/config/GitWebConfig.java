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

package com.google.gerrit.server.config;

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.common.base.Strings;
import com.google.gerrit.common.data.GitWebType;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitWebConfig {
  private static final Logger log = LoggerFactory.getLogger(GitWebConfig.class);

  public static boolean isDisabled(Config cfg) {
    return isEmptyString(cfg, "gitweb", null, "url")
        || isEmptyString(cfg, "gitweb", null, "cgi");
  }

  private static boolean isEmptyString(Config cfg, String section,
      String subsection, String name) {
    // This is currently the only way to check for the empty string in a JGit
    // config. Fun!
    String[] values = cfg.getStringList(section, subsection, name);
    return values.length > 0 && Strings.isNullOrEmpty(values[0]);
  }

  private final String url;
  private final GitWebType type;

  @Inject
  GitWebConfig(GitWebCgiConfig cgiConfig, @GerritServerConfig Config cfg) {
    if (isDisabled(cfg)) {
      type = null;
      url = null;
      return;
    }

    String cfgUrl = cfg.getString("gitweb", null, "url");
    GitWebType type = GitWebType.fromName(cfg.getString("gitweb", null, "type"));
    if (type == null) {
      this.type = null;
      url = null;
      return;
    } else if (cgiConfig.getGitwebCgi() == null) {
      // Use an externally managed gitweb instance, and not an internal one.
      url = cfgUrl;
    } else {
      url = firstNonNull(cfgUrl, "gitweb");
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
      this.type = null;
    } else if (type.getProject() == null) {
      log.warn("No Pattern specified for gitweb.project, disabling.");
      this.type = null;
    } else if (type.getRevision() == null) {
      log.warn("No Pattern specified for gitweb.revision, disabling.");
      this.type = null;
    } else if (type.getRootTree() == null) {
      log.warn("No Pattern specified for gitweb.roottree, disabling.");
      this.type = null;
    } else if (type.getFile() == null) {
      log.warn("No Pattern specified for gitweb.file, disabling.");
      this.type = null;
    } else if (type.getFileHistory() == null) {
      log.warn("No Pattern specified for gitweb.filehistory, disabling.");
      this.type = null;
    } else {
      this.type = type;
    }
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
