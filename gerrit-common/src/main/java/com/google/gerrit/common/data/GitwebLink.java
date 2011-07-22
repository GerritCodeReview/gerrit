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

package com.google.gerrit.common.data;

import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.http.client.URL;

import java.util.HashMap;
import java.util.Map;

/** Link to an external gitweb server. */
public class GitwebLink {
  protected String baseUrl;

  protected GitWebType type;

  protected GitwebLink() {
  }

  public GitwebLink(final String base, final GitWebType gitWebType) {
    baseUrl = base;
    type = gitWebType;
  }

  public String toRevision(final Project.NameKey project, final PatchSet ps) {
    ParameterizedString pattern = new ParameterizedString(type.getRevision());

    final Map<String, String> p = new HashMap<String, String>();
    p.put("project", URL.encodeQueryString(project.get()));
    p.put("commit", URL.encodeQueryString(ps.getRevision().get()));
    return baseUrl + pattern.replace(p);
  }

  public String toProject(final Project.NameKey project) {
    ParameterizedString pattern = new ParameterizedString(type.getProject());

    final Map<String, String> p = new HashMap<String, String>();
    p.put("project", URL.encodeQueryString(project.get()));
    return baseUrl + pattern.replace(p);
  }

  public String toBranch(final Branch.NameKey branch) {
    ParameterizedString pattern = new ParameterizedString(type.getBranch());

    final Map<String, String> p = new HashMap<String, String>();
    p.put("project", URL.encodeQueryString(branch.getParentKey().get()));
    p.put("branch", URL.encodeQueryString(branch.get()));
    return baseUrl + pattern.replace(p);
  }
}
