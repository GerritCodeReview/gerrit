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
      type.setProject("?p=${project}.git;a=summary");
      type.setRevision("?p=${project}.git;a=commit;h=${commit}");
      type.setBranch("?p=${project}.git;a=shortlog;h=${branch}");

    } else if (name.equalsIgnoreCase("gitweb-pathinfo")) {
      type = new GitWebType();
      type.setProject("/${project}.git/summary");
      type.setRevision("/${project}.git/commit/${commit}");
      type.setBranch("/${project}.git/shortlog/${branch}");
      type.setPathInfo(true);

    } else if (name.equalsIgnoreCase("cgit")) {
      type = new GitWebType();
      type.setProject("${project}/summary");
      type.setRevision("${project}/commit/?id=${commit}");
      type.setBranch("${project}/log/?h=${branch}");

    } else if (name.equalsIgnoreCase("custom")) {
      type = new GitWebType();

    } else {
      type = null;
    }

    return type;
  }

  /** String for revision view url. */
  private String revision;

  /** ParamertizedString for project view url. */
  private String project;

  /** ParamertizedString for branch view url. */
  private String branch;

  /** Whether a managed gitweb should default to path_info style or not. */
  private boolean is_path_info;

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
   * Return whether a managed gitweb should default to path_info enabled.
   */
  public boolean isPathInfo() {
    return is_path_info;
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
   * Set whether a managed gitweb should default to path_info enabled.
   */
  public void setPathInfo(final boolean b) {
    is_path_info = b;
  }
}
