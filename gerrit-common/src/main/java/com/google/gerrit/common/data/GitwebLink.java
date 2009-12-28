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

  private GitWebType type;
  
  protected GitwebLink() { }

  public GitwebLink(final String base, final GitWebType type) {
    baseUrl = base;
    this.type = type;
  }

  public String toRevision(final Project.NameKey project, final PatchSet ps) {
    final ParamertizedString pattern = new ParamertizedString(type.getRevision());

    final Map<String, String> replacements = new HashMap<String, String>();
    replacements.put("project", URL.encodeComponent(project.get()));
    replacements.put("commit", URL.encodeComponent(ps.getRevision().get()));
    return baseUrl + pattern.replace(replacements);
  }

  public String toProject(final Project.NameKey project) {
    final ParamertizedString pattern = new ParamertizedString(type.getProject());

    final Map<String, String> replacements = new HashMap<String, String>();
    replacements.put("project", URL.encodeComponent(project.get()));
    return baseUrl + pattern.replace(replacements);
  }

  public String toBranch(final Branch.NameKey branch) {
    final ParamertizedString pattern = new ParamertizedString(type.getBranch());

    final Map<String, String> replacements = new HashMap<String, String>();
    replacements.put("project", URL.encodeComponent(branch.getParentKey().get()));
    replacements.put("branch", URL.encodeComponent(branch.get()));
    return baseUrl + pattern.replace(replacements);
  }
}
