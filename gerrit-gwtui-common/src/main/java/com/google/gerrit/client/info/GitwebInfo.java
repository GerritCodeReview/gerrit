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

package com.google.gerrit.client.info;

import com.google.gerrit.client.info.ChangeInfo.RevisionInfo;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.http.client.URL;

import java.util.HashMap;
import java.util.Map;

public class GitwebInfo extends JavaScriptObject {
  public final native String url() /*-{ return this.url; }-*/;
  public final native GitwebTypeInfo type() /*-{ return this.type; }-*/;

  /**
   * Checks whether the given patch set can be linked.
   *
   * Draft patch sets can only be linked if linking of drafts was enabled by
   * configuration.
   *
   * @param ps patch set to check whether it can be linked
   * @return true if the patch set can be linked, otherwise false
   */
  public final boolean canLink(PatchSet ps) {
    return !ps.isDraft() || type().linkDrafts();
  }

  /**
   * Checks whether the given revision can be linked.
   *
   * Draft revisions can only be linked if linking of drafts was enabled by
   * configuration.
   *
   * @param revision revision to check whether it can be linked
   * @return true if the revision can be linked, otherwise false
   */
  public final boolean canLink(RevisionInfo revision) {
    return revision.draft() || type().linkDrafts();
  }

  /**
   * Returns the name for gitweb links.
   *
   * @return the name for gitweb links
   */
  public final String getLinkName() {
    return "(" + type().name() + ")";
  }

  /**
   * Returns the gitweb link to a revision.
   *
   * @param project the name of the project
   * @param commit the commit ID
   * @return gitweb link to a revision
   */
  public final String toRevision(String  project, String commit) {
    ParameterizedString pattern = new ParameterizedString(type().revision());
    Map<String, String> p = new HashMap<>();
    p.put("project", encode(project));
    p.put("commit", encode(commit));
    return url() + pattern.replace(p);
  }

  /**
   * Returns the gitweb link to a revision.
   *
   * @param project the name of the project
   * @param ps the patch set
   * @return gitweb link to a revision
   */
  public final String toRevision(Project.NameKey project, PatchSet ps) {
    return toRevision(project.get(), ps.getRevision().get());
  }

  /**
   * Returns the gitweb link to a project.
   *
   * @param project the project name key
   * @return gitweb link to a project
   */
  public final String toProject(Project.NameKey project) {
    ParameterizedString pattern = new ParameterizedString(type().project());

    Map<String, String> p = new HashMap<>();
    p.put("project", encode(project.get()));
    return url() + pattern.replace(p);
  }

  /**
   * Returns the gitweb link to a branch.
   *
   * @param branch the branch name key
   * @return gitweb link to a branch
   */
  public final String toBranch(Branch.NameKey branch) {
    ParameterizedString pattern = new ParameterizedString(type().branch());

    Map<String, String> p = new HashMap<>();
    p.put("project", encode(branch.getParentKey().get()));
    p.put("branch", encode(branch.get()));
    return url() + pattern.replace(p);
  }

  /**
   * Returns the gitweb link to a file.
   *
   * @param project the branch name key
   * @param commit the commit ID
   * @param file the path of the file
   * @return gitweb link to a file
   */
  public final String toFile(String  project, String commit, String file) {
    Map<String, String> p = new HashMap<>();
    p.put("project", encode(project));
    p.put("commit", encode(commit));
    p.put("file", encode(file));

    ParameterizedString pattern = (file == null || file.isEmpty())
        ? new ParameterizedString(type().rootTree())
        : new ParameterizedString(type().file());
    return url() + pattern.replace(p);
  }

  /**
   * Returns the gitweb link to a file history.
   *
   * @param branch the branch name key
   * @param file the path of the file
   * @return gitweb link to a file history
   */
  public final String toFileHistory(Branch.NameKey branch, String file) {
    ParameterizedString pattern = new ParameterizedString(type().fileHistory());

    Map<String, String> p = new HashMap<>();
    p.put("project", encode(branch.getParentKey().get()));
    p.put("branch", encode(branch.get()));
    p.put("file", encode(file));
    return url() + pattern.replace(p);
  }

  private String encode(String segment) {
    if (type().urlEncode()) {
      return URL.encodeQueryString(type().replacePathSeparator(segment));
    }
    return segment;
  }

  protected GitwebInfo() {
  }
}
