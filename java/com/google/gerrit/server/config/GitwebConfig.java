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
import static com.google.common.base.Strings.emptyToNull;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GitwebType;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.config.GerritServerConfig;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.extensions.webui.BranchWebLink;
import com.google.gerrit.extensions.webui.FileHistoryWebLink;
import com.google.gerrit.extensions.webui.FileWebLink;
import com.google.gerrit.extensions.webui.ParentWebLink;
import com.google.gerrit.extensions.webui.PatchSetWebLink;
import com.google.gerrit.extensions.webui.ProjectWebLink;
import com.google.gerrit.extensions.webui.TagWebLink;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GitwebConfig {
  private static final Logger log = LoggerFactory.getLogger(GitwebConfig.class);

  public static boolean isDisabled(Config cfg) {
    return isEmptyString(cfg, "gitweb", null, "url")
        || isEmptyString(cfg, "gitweb", null, "cgi")
        || "disabled".equals(cfg.getString("gitweb", null, "type"));
  }

  public static class LegacyModule extends AbstractModule {
    private final Config cfg;

    public LegacyModule(Config cfg) {
      this.cfg = cfg;
    }

    @Override
    protected void configure() {
      GitwebType type = typeFromConfig(cfg);
      if (type != null) {
        bind(GitwebType.class).toInstance(type);

        if (!isNullOrEmpty(type.getBranch())) {
          DynamicSet.bind(binder(), BranchWebLink.class).to(GitwebLinks.class);
        }

        if (!isNullOrEmpty(type.getTag())) {
          DynamicSet.bind(binder(), TagWebLink.class).to(GitwebLinks.class);
        }

        if (!isNullOrEmpty(type.getFile()) || !isNullOrEmpty(type.getRootTree())) {
          DynamicSet.bind(binder(), FileWebLink.class).to(GitwebLinks.class);
        }

        if (!isNullOrEmpty(type.getFileHistory())) {
          DynamicSet.bind(binder(), FileHistoryWebLink.class).to(GitwebLinks.class);
        }

        if (!isNullOrEmpty(type.getRevision())) {
          DynamicSet.bind(binder(), PatchSetWebLink.class).to(GitwebLinks.class);
          DynamicSet.bind(binder(), ParentWebLink.class).to(GitwebLinks.class);
        }

        if (!isNullOrEmpty(type.getProject())) {
          DynamicSet.bind(binder(), ProjectWebLink.class).to(GitwebLinks.class);
        }
      }
    }
  }

  private static boolean isEmptyString(Config cfg, String section, String subsection, String name) {
    // This is currently the only way to check for the empty string in a JGit
    // config. Fun!
    String[] values = cfg.getStringList(section, subsection, name);
    return values.length > 0 && isNullOrEmpty(values[0]);
  }

  private static GitwebType typeFromConfig(Config cfg) {
    GitwebType defaultType = defaultType(cfg.getString("gitweb", null, "type"));
    if (defaultType == null) {
      return null;
    }
    GitwebType type = new GitwebType();

    type.setLinkName(
        firstNonNull(cfg.getString("gitweb", null, "linkname"), defaultType.getLinkName()));
    type.setBranch(firstNonNull(cfg.getString("gitweb", null, "branch"), defaultType.getBranch()));
    type.setTag(firstNonNull(cfg.getString("gitweb", null, "tag"), defaultType.getTag()));
    type.setProject(
        firstNonNull(cfg.getString("gitweb", null, "project"), defaultType.getProject()));
    type.setRevision(
        firstNonNull(cfg.getString("gitweb", null, "revision"), defaultType.getRevision()));
    type.setRootTree(
        firstNonNull(cfg.getString("gitweb", null, "roottree"), defaultType.getRootTree()));
    type.setFile(firstNonNull(cfg.getString("gitweb", null, "file"), defaultType.getFile()));
    type.setFileHistory(
        firstNonNull(cfg.getString("gitweb", null, "filehistory"), defaultType.getFileHistory()));
    type.setUrlEncode(cfg.getBoolean("gitweb", null, "urlencode", defaultType.getUrlEncode()));
    String pathSeparator = cfg.getString("gitweb", null, "pathSeparator");
    if (pathSeparator != null) {
      if (pathSeparator.length() == 1) {
        char c = pathSeparator.charAt(0);
        if (isValidPathSeparator(c)) {
          type.setPathSeparator(firstNonNull(c, defaultType.getPathSeparator()));
        } else {
          log.warn("Invalid gitweb.pathSeparator: " + c);
        }
      } else {
        log.warn("gitweb.pathSeparator is not a single character: " + pathSeparator);
      }
    }
    return type;
  }

  private static GitwebType defaultType(String typeName) {
    GitwebType type = new GitwebType();
    switch (nullToEmpty(typeName)) {
      case "gitweb":
        type.setLinkName("gitweb");
        type.setProject("?p=${project}.git;a=summary");
        type.setRevision("?p=${project}.git;a=commit;h=${commit}");
        type.setBranch("?p=${project}.git;a=shortlog;h=${branch}");
        type.setTag("?p=${project}.git;a=tag;h=${tag}");
        type.setRootTree("?p=${project}.git;a=tree;hb=${commit}");
        type.setFile("?p=${project}.git;hb=${commit};f=${file}");
        type.setFileHistory("?p=${project}.git;a=history;hb=${branch};f=${file}");
        break;
      case "cgit":
        type.setLinkName("cgit");
        type.setProject("${project}.git/summary");
        type.setRevision("${project}.git/commit/?id=${commit}");
        type.setBranch("${project}.git/log/?h=${branch}");
        type.setTag("${project}.git/tag/?h=${tag}");
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
        type.setTag("");
        type.setRootTree("");
        type.setFile("");
        type.setFileHistory("");
        break;
      case "":
      case "disabled":
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
    } else {
      String cfgUrl = cfg.getString("gitweb", null, "url");
      type = typeFromConfig(cfg);
      if (type == null) {
        url = null;
      } else if (cgiConfig.getGitwebCgi() == null) {
        // Use an externally managed gitweb instance, and not an internal one.
        url = cfgUrl;
      } else {
        url = firstNonNull(cfgUrl, "gitweb");
      }
    }
  }

  /** @return GitwebType for gitweb viewer. */
  @Nullable
  public GitwebType getGitwebType() {
    return type;
  }

  /**
   * @return URL of the entry point into gitweb. This URL may be relative to our context if gitweb
   *     is hosted by ourselves; or absolute if its hosted elsewhere; or null if gitweb has not been
   *     configured.
   */
  public String getUrl() {
    return url;
  }

  /**
   * Determines if a given character can be used unencoded in an URL as a replacement for the path
   * separator '/'.
   *
   * <p>Reasoning: http://www.ietf.org/rfc/rfc1738.txt § 2.2:
   *
   * <p>... only alphanumerics, the special characters "$-_.+!*'(),", and reserved characters used
   * for their reserved purposes may be used unencoded within a URL.
   *
   * <p>The following characters might occur in file names, however:
   *
   * <p>alphanumeric characters,
   *
   * <p>"$-_.+!',"
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

  @Singleton
  static class GitwebLinks
      implements BranchWebLink,
          FileHistoryWebLink,
          FileWebLink,
          PatchSetWebLink,
          ParentWebLink,
          ProjectWebLink,
          TagWebLink {
    private final String url;
    private final GitwebType type;
    private final ParameterizedString branch;
    private final ParameterizedString file;
    private final ParameterizedString fileHistory;
    private final ParameterizedString project;
    private final ParameterizedString revision;
    private final ParameterizedString tag;

    @Inject
    GitwebLinks(GitwebConfig config, GitwebType type) {
      this.url = config.getUrl();
      this.type = type;
      this.branch = parse(type.getBranch());
      this.file = parse(firstNonNull(emptyToNull(type.getFile()), nullToEmpty(type.getRootTree())));
      this.fileHistory = parse(type.getFileHistory());
      this.project = parse(type.getProject());
      this.revision = parse(type.getRevision());
      this.tag = parse(type.getTag());
    }

    @Override
    public WebLinkInfo getBranchWebLink(String projectName, String branchName) {
      if (branch != null) {
        return link(
            branch
                .replace("project", encode(projectName))
                .replace("branch", encode(branchName))
                .toString());
      }
      return null;
    }

    @Override
    public WebLinkInfo getTagWebLink(String projectName, String tagName) {
      if (tag != null) {
        return link(
            tag.replace("project", encode(projectName)).replace("tag", encode(tagName)).toString());
      }
      return null;
    }

    @Override
    public WebLinkInfo getFileHistoryWebLink(String projectName, String revision, String fileName) {
      if (fileHistory != null) {
        return link(
            fileHistory
                .replace("project", encode(projectName))
                .replace("branch", encode(revision))
                .replace("file", encode(fileName))
                .toString());
      }
      return null;
    }

    @Override
    public WebLinkInfo getFileWebLink(String projectName, String revision, String fileName) {
      if (file != null) {
        return link(
            file.replace("project", encode(projectName))
                .replace("commit", encode(revision))
                .replace("file", encode(fileName))
                .toString());
      }
      return null;
    }

    @Override
    public WebLinkInfo getPatchSetWebLink(String projectName, String commit) {
      if (revision != null) {
        return link(
            revision
                .replace("project", encode(projectName))
                .replace("commit", encode(commit))
                .toString());
      }
      return null;
    }

    @Override
    public WebLinkInfo getParentWebLink(String projectName, String commit) {
      // For Gitweb treat parent revision links the same as patch set links
      return getPatchSetWebLink(projectName, commit);
    }

    @Override
    public WebLinkInfo getProjectWeblink(String projectName) {
      if (project != null) {
        return link(project.replace("project", encode(projectName)).toString());
      }
      return null;
    }

    private String encode(String val) {
      if (type.getUrlEncode()) {
        return Url.encode(type.replacePathSeparator(val));
      }
      return val;
    }

    private WebLinkInfo link(String rest) {
      return new WebLinkInfo(type.getLinkName(), null, url + rest, null);
    }

    private static ParameterizedString parse(String pattern) {
      if (!isNullOrEmpty(pattern)) {
        return new ParameterizedString(pattern);
      }
      return null;
    }
  }
}
