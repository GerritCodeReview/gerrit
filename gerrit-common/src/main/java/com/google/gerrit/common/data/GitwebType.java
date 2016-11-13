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

/** Class to store information about different source browser types. */
public class GitwebType {
  private String name;

  private String branch;
  private String file;
  private String fileHistory;
  private String project;
  private String revision;
  private String rootTree;

  private char pathSeparator = '/';
  private boolean urlEncode = true;

  /** @return name displayed in links. */
  public String getLinkName() {
    return name;
  }

  /**
   * Set the name displayed in links.
   *
   * @param name new name.
   */
  public void setLinkName(String name) {
    this.name = name;
  }

  /** @return parameterized string for the branch URL. */
  public String getBranch() {
    return branch;
  }

  /**
   * Set the parameterized string for the branch URL.
   *
   * @param str new string.
   */
  public void setBranch(String str) {
    branch = str;
  }

  /** @return parameterized string for the file URL. */
  public String getFile() {
    return file;
  }

  /**
   * Set the parameterized string for the file URL.
   *
   * @param str new string.
   */
  public void setFile(String str) {
    file = str;
  }

  /** @return parameterized string for the file history URL. */
  public String getFileHistory() {
    return fileHistory;
  }

  /**
   * Set the parameterized string for the file history URL.
   *
   * @param str new string.
   */
  public void setFileHistory(String str) {
    fileHistory = str;
  }

  /** @return parameterized string for the project URL. */
  public String getProject() {
    return project;
  }

  /**
   * Set the parameterized string for the project URL.
   *
   * @param str new string.
   */
  public void setProject(String str) {
    project = str;
  }

  /** @return parameterized string for the revision URL. */
  public String getRevision() {
    return revision;
  }

  /**
   * Set the parameterized string for the revision URL.
   *
   * @param str new string.
   */
  public void setRevision(String str) {
    revision = str;
  }

  /** @return parameterized string for the root tree URL. */
  public String getRootTree() {
    return rootTree;
  }

  /**
   * Set the parameterized string for the root tree URL.
   *
   * @param str new string.
   */
  public void setRootTree(String str) {
    rootTree = str;
  }

  /** @return path separator used for branch and project names. */
  public char getPathSeparator() {
    return pathSeparator;
  }

  /**
   * Set the custom path separator.
   *
   * @param separator new separator.
   */
  public void setPathSeparator(char separator) {
    this.pathSeparator = separator;
  }

  /** @return whether to URL encode path segments. */
  public boolean getUrlEncode() {
    return urlEncode;
  }

  /**
   * Set whether to URL encode path segments.
   *
   * @param urlEncode new value.
   */
  public void setUrlEncode(boolean urlEncode) {
    this.urlEncode = urlEncode;
  }

  /**
   * Replace standard path separator with custom configured path separator.
   *
   * @param urlSegment URL segment (e.g. branch or project name) in which to replace the path
   *     separator.
   * @return the segment with the standard path separator replaced by the custom {@link
   *     #getPathSeparator()}.
   */
  public String replacePathSeparator(String urlSegment) {
    if ('/' != pathSeparator) {
      return urlSegment.replace('/', pathSeparator);
    }
    return urlSegment;
  }
}
