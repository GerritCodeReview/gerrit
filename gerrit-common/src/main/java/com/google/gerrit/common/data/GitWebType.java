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

package com.google.gerrit.common.data;

/** Class to store information about different gitweb types. */
public class GitWebType {
  /**
   * Get a GitWebType based on the given name.
   *
   * @param name Name to look for.
   * @return GitWebType from the given name, else null if not found.
   */
  public static GitWebType fromName(final String name) {
    final GitWebType type;

    if (name == null || name.isEmpty() || name.equalsIgnoreCase("gitweb")) {
      type = new GitWebType();
      type.setLinkName("gitweb");
      type.setProject("?p=${project}.git;a=summary");
      type.setRevision("?p=${project}.git;a=commit;h=${commit}");
      type.setBranch("?p=${project}.git;a=shortlog;h=${branch}");
      type.setFileHistory("?p=${project}.git;a=history;hb=${branch};f=${file}");

    } else if (name.equalsIgnoreCase("cgit")) {
      type = new GitWebType();
      type.setLinkName("cgit");
      type.setProject("${project}.git/summary");
      type.setRevision("${project}.git/commit/?id=${commit}");
      type.setBranch("${project}.git/log/?h=${branch}");
      type.setFileHistory("${project}.git/log/${file}?h=${branch}");

    } else if (name.equalsIgnoreCase("custom")) {
      type = new GitWebType();
      // The custom name is not defined, let's keep the old style of using GitWeb
      type.setLinkName("gitweb");

    } else if (name.equalsIgnoreCase("disabled")) {
      type = null;

    } else {
      type = null;
    }

    return type;
  }

  /** name of the type. */
  private String name;

  /** String for revision view url. */
  private String revision;

  /** ParamertizedString for project view url. */
  private String project;

  /** ParamertizedString for branch view url. */
  private String branch;

  /** ParamertizedString for file history view url. */
  private String fileHistory;

  /** Character to substitute the standard path separator '/' in branch and
    * project names */
  private char pathSeparator = '/';

  /** Whether to include links to draft patchsets */
  private boolean linkDrafts;

  /** Private default constructor for gson. */
  protected GitWebType() {
  }

  /**
   * Get the String for branch view.
   *
   * @return The String for branch view
   */
  public String getBranch() {
    return branch;
  }

  /**
   * Get the String for link-name of the type.
   *
   * @return The String for link-name of the type
   */
  public String getLinkName() {
    return name;
  }

  /**
   * Get the String for project view.
   *
   * @return The String for project view
   */
  public String getProject() {
    return project;
  }

  /**
   * Get the String for revision view.
   *
   * @return The String for revision view
   */
  public String getRevision() {
    return revision;
  }

  /**
   * Get the String for file history view.
   *
   * @return The String for file history view
   */
  public String getFileHistory() {
    return fileHistory;
  }

  /**
   * Get whether to link to draft patchsets
   *
   * @return True to link
   */
  public boolean getLinkDrafts() {
    return linkDrafts;
  }

  /**
   * Set the pattern for branch view.
   *
   * @param pattern The pattern for branch view
   */
  public void setBranch(final String pattern) {
    if (pattern != null && !pattern.isEmpty()) {
      branch = pattern;
    }
  }

  /**
   * Set the pattern for link-name type.
   *
   * @param pattern The pattern for link-name type
   */
  public void setLinkName(final String name) {
    if (name != null && !name.isEmpty()) {
      this.name = name;
    }
  }

  /**
   * Set the pattern for project view.
   *
   * @param pattern The pattern for project view
   */
  public void setProject(final String pattern) {
    if (pattern != null && !pattern.isEmpty()) {
      project = pattern;
    }
  }

  /**
   * Set the pattern for revision view.
   *
   * @param pattern The pattern for revision view
   */
  public void setRevision(final String pattern) {
    if (pattern != null && !pattern.isEmpty()) {
      revision = pattern;
    }
  }

  /**
   * Set the pattern for file history view.
   *
   * @param pattern The pattern for file history view
   */
  public void setFileHistory(final String pattern) {
    if (pattern != null && !pattern.isEmpty()) {
      fileHistory = pattern;
    }
  }

  /**
   * Replace the standard path separator ('/') in a branch name or project
   * name with a custom path separator configured by the property
   * gitweb.pathSeparator.
   * @param urlSegment The branch or project to replace the path separator in
   * @return the urlSegment with the standard path separator replaced by the
   * custom path separator
   */
  public String replacePathSeparator(String urlSegment) {
    if ('/' != pathSeparator) {
      return urlSegment.replace('/', pathSeparator);
    }
    return urlSegment;
  }

  /**
   * Set the custom path separator
   * @param separator The custom path separator
   */
  public void setPathSeparator(char separator) {
    this.pathSeparator = separator;
  }

  public void setLinkDrafts(boolean linkDrafts) {
    this.linkDrafts = linkDrafts;
  }
}
