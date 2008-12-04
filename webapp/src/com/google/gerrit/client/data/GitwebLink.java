// Copyright 2008 Google Inc.
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
    final StringBuffer r = new StringBuffer();
    p(r, project);
    h(r, ps);
    return baseUrl + r;
  }


  public String toProject(final Project.NameKey project) {
    final StringBuffer r = new StringBuffer();
    p(r, project);
    a(r, "summary");
    return baseUrl + r;
  }

  public String toBranch(final Branch.NameKey branch) {
    final StringBuffer r = new StringBuffer();
    p(r, branch.getParentKey());
    h(r, branch);
    a(r, "shortlog");
    return baseUrl + r;
  }

  private static void p(final StringBuffer r, final Project.NameKey project) {
    String n = project.get();
    if (!n.endsWith(".git")) {
      n += ".git";
    }
    var(r, "p", n);
  }

  private static void h(final StringBuffer r, final PatchSet ps) {
    var(r, "h", ps.getRevision());
  }

  private static void h(final StringBuffer r, final Branch.NameKey branch) {
    var(r, "h", branch.get());
  }

  private static void a(final StringBuffer r, final String where) {
    var(r, "a", where);
  }

  private static void var(final StringBuffer r, final String n, final String v) {
    if (r.length() > 0) {
      r.append(";");
    }
    r.append(n);
    r.append("=");
    r.append(URL.encodeComponent(v));
  }
}
