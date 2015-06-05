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
  /** name of the type. */
  private String name;

  /** String for revision view url. */
  private String revision;

  /** ParameterizedString for project view url. */
  private String project;

  /** ParameterizedString for branch view url. */
  private String branch;

  /** ParameterizedString for root tree view url. */
  private String rootTree;

  /** ParameterizedString for file view url. */
  private String file;

  /** ParameterizedString for file history view url. */
  private String fileHistory;

  /** Character to substitute the standard path separator '/' in branch and
    * project names */
  private char pathSeparator = '/';

  /** Whether to include links to draft patch sets */
  private boolean linkDrafts = true;

  /** Whether to encode URL segments */
  private boolean urlEncode = true;

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
   * Get the String for root tree view.
   *
   * @return The String for root tree view
   */
  public String getRootTree() {
    return rootTree;
  }

  /**
   * Get the String for file view.
   *
   * @return The String for file view
   */
  public String getFile() {
    return file;
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
   * Get the path separator used for branch and project names.
   *
   * @return The path separator.
   */
  public char getPathSeparator() {
    return pathSeparator;
  }

  /**
   * Get whether to link to draft patch sets
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
    branch = pattern;
  }

  /**
   * Set the pattern for link-name type.
   *
   * @param name The link-name type
   */
  public void setLinkName(final String name) {
    this.name = name;
  }

  /**
   * Set the pattern for project view.
   *
   * @param pattern The pattern for project view
   */
  public void setProject(final String pattern) {
    project = pattern;
  }

  /**
   * Set the pattern for revision view.
   *
   * @param pattern The pattern for revision view
   */
  public void setRevision(final String pattern) {
    revision = pattern;
  }

  /**
   * Set the pattern for root tree view.
   *
   * @param pattern The pattern for root tree view
   */
  public void setRootTree(final String pattern) {
    rootTree = pattern;
  }

  /**
   * Set the pattern for file view.
   *
   * @param pattern The pattern for file view
   */
  public void setFile(final String pattern) {
    file = pattern;
  }

  /**
   * Set the pattern for file history view.
   *
   * @param pattern The pattern for file history view
   */
  public void setFileHistory(final String pattern) {
    fileHistory = pattern;
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

  public boolean isUrlEncode() {
    return urlEncode;
  }

  public void setUrlEncode(boolean urlEncode) {
    this.urlEncode = urlEncode;
  }
}
