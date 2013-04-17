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

package com.google.gerrit.client;

import com.google.gerrit.common.data.GitWebType;
import com.google.gerrit.common.data.ParameterizedString;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.http.client.URL;

import java.util.HashMap;
import java.util.Map;

/** Link to an external gitweb server. */
public class GitwebLink {
  protected String baseUrl;

  protected GitWebType type;

  public GitwebLink(com.google.gerrit.common.data.GitwebConfig link) {
    baseUrl = link.baseUrl;
    type = link.type;
  }

  /**
   * Can we link to a patch set if it's a draft
   *
   * @param ps Patch set to check draft status
   * @return true if it's not a draft, or we can link to drafts
   */
  public boolean canLink(final PatchSet ps) {
    return !ps.isDraft() || type.getLinkDrafts();
  }

  public String getLinkName() {
    return "(" + type.getLinkName() + ")";
  }

  public String toRevision(final Project.NameKey project, final PatchSet ps) {
    ParameterizedString pattern = new ParameterizedString(type.getRevision());

    final Map<String, String> p = new HashMap<String, String>();
    p.put("project", encode(project.get()));
    p.put("commit", encode(ps.getRevision().get()));
    return baseUrl + pattern.replace(p);
  }

  public String toProject(final Project.NameKey project) {
    ParameterizedString pattern = new ParameterizedString(type.getProject());

    final Map<String, String> p = new HashMap<String, String>();
    p.put("project", encode(project.get()));
    return baseUrl + pattern.replace(p);
  }

  public String toBranch(final Branch.NameKey branch) {
    ParameterizedString pattern = new ParameterizedString(type.getBranch());

    final Map<String, String> p = new HashMap<String, String>();
    p.put("project", encode(branch.getParentKey().get()));
    p.put("branch", encode(branch.get()));
    return baseUrl + pattern.replace(p);
  }

  public String toFileHistory(final Branch.NameKey branch, final String file) {
    ParameterizedString pattern = new ParameterizedString(type.getFileHistory());

    final Map<String, String> p = new HashMap<String, String>();
    p.put("project", encode(branch.getParentKey().get()));
    p.put("branch", encode(branch.get()));
    p.put("file", encode(file));
    return baseUrl + pattern.replace(p);
  }

  private String encode(String segment) {
    return URL.encodeQueryString(type.replacePathSeparator(segment));
  }
}
