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

package com.google.gerrit.client.data;

import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gwt.http.client.URL;

/** Link to an external gitweb server. */
public class GitwebLink {
  protected String baseUrl;

  protected GitwebLink() {
  }

  public GitwebLink(final String base) {
    baseUrl = base + "?";
  }

  public String toRevision(final Project.NameKey project, final PatchSet ps) {
    final StringBuilder r = new StringBuilder();
    p(r, project);
    a(r, "commit");
    h(r, ps);
    return baseUrl + r;
  }

  public String toProject(final Project.NameKey project) {
    final StringBuilder r = new StringBuilder();
    p(r, project);
    a(r, "summary");
    return baseUrl + r;
  }

  public String toBranch(final Branch.NameKey branch) {
    final StringBuilder r = new StringBuilder();
    p(r, branch.getParentKey());
    h(r, branch);
    a(r, "shortlog");
    return baseUrl + r;
  }

  private static void p(final StringBuilder r, final Project.NameKey project) {
    String n = project.get();
    if (!n.endsWith(".git")) {
      n += ".git";
    }
    var(r, "p", n);
  }

  private static void h(final StringBuilder r, final PatchSet ps) {
    var(r, "h", ps.getRevision().get());
  }

  private static void h(final StringBuilder r, final Branch.NameKey branch) {
    var(r, "h", branch.get());
  }

  private static void a(final StringBuilder r, final String where) {
    var(r, "a", where);
  }

  private static void var(final StringBuilder r, final String n, final String v) {
    if (r.length() > 0) {
      r.append(";");
    }
    r.append(n);
    r.append("=");
    r.append(URL.encodeComponent(v));
  }
}
