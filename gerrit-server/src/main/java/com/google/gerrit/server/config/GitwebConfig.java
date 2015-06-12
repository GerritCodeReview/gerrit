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
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;

import com.google.common.base.Strings;
import com.google.gerrit.common.data.GitwebType;
import com.google.inject.Inject;

import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitwebConfig {
  private static final Logger log = LoggerFactory.getLogger(GitwebConfig.class);

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

  /**
   * Get a GitwebType based on the given config.
   *
   * @param cfg Gerrit config.
   * @return GitwebType from the given name, else null if not found.
   */
  public static GitwebType typeFromConfig(Config cfg) {
    GitwebType defaultType = defaultType(cfg.getString("gitweb", null, "type"));
    if (defaultType == null) {
      return null;
    }
    GitwebType type = new GitwebType();

    type.setLinkName(firstNonNull(
        cfg.getString("gitweb", null, "linkname"),
        defaultType.getLinkName()));
    type.setBranch(firstNonNull(
        cfg.getString("gitweb", null, "branch"),
        defaultType.getBranch()));
    type.setProject(firstNonNull(
        cfg.getString("gitweb", null, "project"),
        defaultType.getProject()));
    type.setRevision(firstNonNull(
        cfg.getString("gitweb", null, "revision"),
        defaultType.getRevision()));
    type.setRootTree(firstNonNull(
        cfg.getString("gitweb", null, "roottree"),
        defaultType.getRootTree()));
    type.setFile(firstNonNull(
        cfg.getString("gitweb", null, "file"),
        defaultType.getFile()));
    type.setFileHistory(firstNonNull(
        cfg.getString("gitweb", null, "filehistory"),
        defaultType.getFileHistory()));
    type.setLinkDrafts(
        cfg.getBoolean("gitweb", null, "linkdrafts",
            defaultType.getLinkDrafts()));
    type.setUrlEncode(
        cfg.getBoolean("gitweb", null, "urlencode",
            defaultType.getUrlEncode()));
    String pathSeparator = cfg.getString("gitweb", null, "pathSeparator");
    if (pathSeparator != null) {
      if (pathSeparator.length() == 1) {
        char c = pathSeparator.charAt(0);
        if (isValidPathSeparator(c)) {
          type.setPathSeparator(
              firstNonNull(c, defaultType.getPathSeparator()));
        } else {
          log.warn("Invalid gitweb.pathSeparator: " + c);
        }
      } else {
        log.warn(
            "gitweb.pathSeparator is not a single character: " + pathSeparator);
      }
    }
    return type;
  }

  private static GitwebType defaultType(String typeName) {
    GitwebType type = new GitwebType();
    switch (nullToEmpty(typeName)) {
      case "":
      case "gitweb":
        type.setLinkName("gitweb");
        type.setProject("?p=${project}.git;a=summary");
        type.setRevision("?p=${project}.git;a=commit;h=${commit}");
        type.setBranch("?p=${project}.git;a=shortlog;h=${branch}");
        type.setRootTree("?p=${project}.git;a=tree;hb=${commit}");
        type.setFile("?p=${project}.git;hb=${commit};f=${file}");
        type.setFileHistory(
            "?p=${project}.git;a=history;hb=${branch};f=${file}");
        break;
      case "cgit":
        type.setLinkName("cgit");
        type.setProject("${project}.git/summary");
        type.setRevision("${project}.git/commit/?id=${commit}");
        type.setBranch("${project}.git/log/?h=${branch}");
        type.setRootTree("${project}.git/tree/?h=${commit}");
        type.setFile("${project}.git/tree/${file}?h=${commit}");
        type.setFileHistory("${project}.git/log/${file}?h=${branch}");
        break;
      case "custom":
        // For a custom type with no explicit link name, just reuse "gitweb".
        type.setLinkName("gitweb");
        type.setProject("");
        type.setRevision("");
        type.setBranch("");
        type.setRootTree("");
        type.setFile("");
        type.setFileHistory("");
        break;
      default:
        return null;
    }
    return type;
  }

  private final String url;
  private final GitwebType type;

  @Inject
  GitwebConfig(GitwebCgiConfig cgiConfig, @GerritServerConfig Config cfg) {
    if (isDisabled(cfg)) {
      type = null;
      url = null;
      return;
    }

    String cfgUrl = cfg.getString("gitweb", null, "url");
    GitwebType type = typeFromConfig(cfg);
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

    if (isNullOrEmpty(type.getBranch())) {
      log.warn("No Pattern specified for gitweb.branch, disabling.");
      this.type = null;
    } else if (isNullOrEmpty(type.getProject())) {
      log.warn("No Pattern specified for gitweb.project, disabling.");
      this.type = null;
    } else if (isNullOrEmpty(type.getRevision())) {
      log.warn("No Pattern specified for gitweb.revision, disabling.");
      this.type = null;
    } else if (isNullOrEmpty(type.getRootTree())) {
      log.warn("No Pattern specified for gitweb.roottree, disabling.");
      this.type = null;
    } else if (isNullOrEmpty(type.getFile())) {
      log.warn("No Pattern specified for gitweb.file, disabling.");
      this.type = null;
    } else if (isNullOrEmpty(type.getFileHistory())) {
      log.warn("No Pattern specified for gitweb.filehistory, disabling.");
      this.type = null;
    } else {
      this.type = type;
    }
  }

  /** @return GitwebType for gitweb viewer. */
  public GitwebType getGitwebType() {
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
