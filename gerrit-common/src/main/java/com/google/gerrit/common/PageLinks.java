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

package com.google.gerrit.common;

import com.google.gerrit.common.data.ChangeInfo;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Status;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwtorm.client.KeyUtil;

public class PageLinks {
  public static final String SETTINGS = "/settings/";
  public static final String SETTINGS_PREFERENCES = "/settings/preferences";
  public static final String SETTINGS_SSHKEYS = "/settings/ssh-keys";
  public static final String SETTINGS_HTTP_PASSWORD = "/settings/http-password";
  public static final String SETTINGS_WEBIDENT = "/settings/web-identities";
  public static final String SETTINGS_MYGROUPS = "/settings/group-memberships";
  public static final String SETTINGS_AGREEMENTS = "/settings/agreements";
  public static final String SETTINGS_CONTACT = "/settings/contact";
  public static final String SETTINGS_PROJECTS = "/settings/projects";
  public static final String SETTINGS_NEW_AGREEMENT = "/settings/new-agreement";
  public static final String REGISTER = "/register";

  public static final String MINE = "/";
  public static final String QUERY = "/q/";
  public static final String PROJECTS = "/projects/";
  public static final String DASHBOARDS = ",dashboards/";
  public static final String ADMIN_GROUPS = "/admin/groups/";
  public static final String ADMIN_CREATE_GROUP = "/admin/create-group/";
  public static final String ADMIN_PROJECTS = "/admin/projects/";
  public static final String ADMIN_CREATE_PROJECT = "/admin/create-project/";
  public static final String ADMIN_PLUGINS = "/admin/plugins/";
  public static final String MY_GROUPS = "/groups/self";

  public static String toChange(final ChangeInfo c) {
    return toChange(c.getId());
  }

  public static String toChangeInEditMode(Change.Id c) {
    return "/c/" + c + ",edit/";
  }

  public static String toChange(final Change.Id c) {
    return "/c/" + c + "/";
  }

  public static String toChange(Change.Id c, String p) {
    return "/c/" + c + "/" + p;
  }

  public static String toChange(Change.Id c, String b, String p) {
    String u = "/c/" + c + "/";
    if (b != null) {
      u += b + "..";
    }
    u += p;
    return u;
  }

  public static String toChange(final PatchSet.Id ps) {
    return "/c/" + ps.getParentKey() + "/" + ps.getId();
  }

  public static String toProject(final Project.NameKey p) {
    return ADMIN_PROJECTS + p.get();
  }

  public static String toProjectAcceess(final Project.NameKey p) {
    return "/admin/projects/" + p.get() + ",access";
  }

  public static String toProjectBranches(Project.NameKey p) {
    return "/admin/projects/" + p.get() + ",branches";
  }

  public static String toAccountQuery(String fullname, Status status) {
    return toChangeQuery(op("owner", fullname) + " " + status(status));
  }

  public static String toCustomDashboard(final String params) {
    return "/dashboard/?" + params;
  }

  public static String toProjectDashboards(Project.NameKey proj) {
    return ADMIN_PROJECTS + proj.get() + ",dashboards";
  }

  public static String toChangeQuery(String query) {
    return QUERY + KeyUtil.encode(query);
  }

  public static String toChangeQuery(String query, String start) {
    int s = Integer.parseInt(start);
    return QUERY + KeyUtil.encode(query) + (s > 0 ? "," + s : "");
  }

  public static String toProjectDashboard(Project.NameKey name, String id) {
    return PROJECTS + name.get() + DASHBOARDS + id;
  }

  public static String toProjectDefaultDashboard(Project.NameKey name) {
    return PROJECTS + name.get() + DASHBOARDS + "default";
  }

  public static String projectQuery(Project.NameKey proj) {
    return op("project", proj.get());
  }

  public static String projectQuery(Project.NameKey proj, Status status) {
      return status(status) + " " + op("project", proj.get());
  }

  public static String toGroup(AccountGroup.UUID uuid) {
    return ADMIN_GROUPS + "uuid-" + uuid;
  }

  private static String status(Status status) {
    switch (status) {
      case ABANDONED:
        return "status:abandoned";
      case MERGED:
        return "status:merged";
      case NEW:
      case SUBMITTED:
      default:
        return "status:open";
    }
  }

  public static String op(String op, int value) {
    return op + ":" + value;
  }

  public static String op(String op, String value) {
    if (isSingleWord(value)) {
      return op + ":" + value;
    }
    return op + ":\"" + value + "\"";
  }

  private static boolean isSingleWord(String value) {
    if (value.startsWith("-")) {
      return false;
    }
    return value.matches("[^\u0000-\u0020!\"#$%&'():;?\\[\\]{}~]+");
  }

  protected PageLinks() {
  }
}
