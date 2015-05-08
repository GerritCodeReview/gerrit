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

package com.google.gerrit.client.config;

import com.google.gerrit.client.changes.ChangeInfo.RevisionInfo;
import com.google.gerrit.common.data.GitWebType;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.http.client.URL;

import java.util.HashMap;
import java.util.Map;

public class GitWebInfo extends JavaScriptObject {
  public final native String url() /*-{ return this.url; }-*/;
  public final native GitWebType type() /*-{ return this.type; }-*/;

  /**
   * Can we link to a patch set if it's a draft
   *
   * @param ps Patch set to check draft status
   * @return true if it's not a draft, or we can link to drafts
   */
  public final boolean canLink(PatchSet ps) {
    return !ps.isDraft() || type().getLinkDrafts();
  }

  public final boolean canLink(RevisionInfo revision) {
    return revision.draft() || type().getLinkDrafts();
  }

  public final String getLinkName() {
    return "(" + type().getLinkName() + ")";
  }

  public final String toRevision(String  project, String commit) {
    ParameterizedString pattern = new ParameterizedString(type().getRevision());
    Map<String, String> p = new HashMap<>();
    p.put("project", encode(project));
    p.put("commit", encode(commit));
    return url() + pattern.replace(p);
  }

  public final String toRevision(Project.NameKey project, PatchSet ps) {
    return toRevision(project.get(), ps.getRevision().get());
  }

  public final String toProject(Project.NameKey project) {
    ParameterizedString pattern = new ParameterizedString(type().getProject());

    Map<String, String> p = new HashMap<>();
    p.put("project", encode(project.get()));
    return url() + pattern.replace(p);
  }

  public final String toBranch(Branch.NameKey branch) {
    ParameterizedString pattern = new ParameterizedString(type().getBranch());

    Map<String, String> p = new HashMap<>();
    p.put("project", encode(branch.getParentKey().get()));
    p.put("branch", encode(branch.get()));
    return url() + pattern.replace(p);
  }

  public final String toFile(String  project, String commit, String file) {
    Map<String, String> p = new HashMap<>();
    p.put("project", encode(project));
    p.put("commit", encode(commit));
    p.put("file", encode(file));

    ParameterizedString pattern = (file == null || file.isEmpty())
        ? new ParameterizedString(type().getRootTree())
        : new ParameterizedString(type().getFile());
    return url() + pattern.replace(p);
  }

  public final String toFileHistory(Branch.NameKey branch, String file) {
    ParameterizedString pattern = new ParameterizedString(type().getFileHistory());

    Map<String, String> p = new HashMap<>();
    p.put("project", encode(branch.getParentKey().get()));
    p.put("branch", encode(branch.get()));
    p.put("file", encode(file));
    return url() + pattern.replace(p);
  }

  private final String encode(String segment) {
    if (type().isUrlEncode()) {
      return URL.encodeQueryString(type().replacePathSeparator(segment));
    } else {
      return segment;
    }
  }

  protected GitWebInfo() {
  }
}
