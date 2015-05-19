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

import static com.google.gerrit.common.PageLinks.ADMIN_CREATE_GROUP;
import static com.google.gerrit.common.PageLinks.ADMIN_CREATE_PROJECT;
import static com.google.gerrit.common.PageLinks.ADMIN_GROUPS;
import static com.google.gerrit.common.PageLinks.ADMIN_PLUGINS;
import static com.google.gerrit.common.PageLinks.ADMIN_PROJECTS;
import static com.google.gerrit.common.PageLinks.DASHBOARDS;
import static com.google.gerrit.common.PageLinks.MINE;
import static com.google.gerrit.common.PageLinks.MY_GROUPS;
import static com.google.gerrit.common.PageLinks.PROJECTS;
import static com.google.gerrit.common.PageLinks.QUERY;
import static com.google.gerrit.common.PageLinks.REGISTER;
import static com.google.gerrit.common.PageLinks.SETTINGS;
import static com.google.gerrit.common.PageLinks.SETTINGS_AGREEMENTS;
import static com.google.gerrit.common.PageLinks.SETTINGS_CONTACT;
import static com.google.gerrit.common.PageLinks.SETTINGS_HTTP_PASSWORD;
import static com.google.gerrit.common.PageLinks.SETTINGS_MYGROUPS;
import static com.google.gerrit.common.PageLinks.SETTINGS_NEW_AGREEMENT;
import static com.google.gerrit.common.PageLinks.SETTINGS_PREFERENCES;
import static com.google.gerrit.common.PageLinks.SETTINGS_PROJECTS;
import static com.google.gerrit.common.PageLinks.SETTINGS_SSHKEYS;
import static com.google.gerrit.common.PageLinks.SETTINGS_WEBIDENT;
import static com.google.gerrit.common.PageLinks.op;
import static com.google.gerrit.common.PageLinks.toChangeQuery;

import com.google.gerrit.client.account.MyAgreementsScreen;
import com.google.gerrit.client.account.MyContactInformationScreen;
import com.google.gerrit.client.account.MyGroupsScreen;
import com.google.gerrit.client.account.MyIdentitiesScreen;
import com.google.gerrit.client.account.MyPasswordScreen;
import com.google.gerrit.client.account.MyPreferencesScreen;
import com.google.gerrit.client.account.MyProfileScreen;
import com.google.gerrit.client.account.MySshKeysScreen;
import com.google.gerrit.client.account.MyWatchedProjectsScreen;
import com.google.gerrit.client.account.NewAgreementScreen;
import com.google.gerrit.client.account.RegisterScreen;
import com.google.gerrit.client.account.ValidateEmailScreen;
import com.google.gerrit.client.admin.AccountGroupInfoScreen;
import com.google.gerrit.client.admin.AccountGroupMembersScreen;
import com.google.gerrit.client.admin.AccountGroupScreen;
import com.google.gerrit.client.admin.CreateGroupScreen;
import com.google.gerrit.client.admin.CreateProjectScreen;
import com.google.gerrit.client.admin.GroupListScreen;
import com.google.gerrit.client.admin.PluginListScreen;
import com.google.gerrit.client.admin.ProjectAccessScreen;
import com.google.gerrit.client.admin.ProjectBranchesScreen;
import com.google.gerrit.client.admin.ProjectDashboardsScreen;
import com.google.gerrit.client.admin.ProjectInfoScreen;
import com.google.gerrit.client.admin.ProjectListScreen;
import com.google.gerrit.client.admin.ProjectScreen;
import com.google.gerrit.client.api.ExtensionScreen;
import com.google.gerrit.client.change.ChangeScreen;
import com.google.gerrit.client.change.FileTable;
import com.google.gerrit.client.changes.AccountDashboardScreen;
import com.google.gerrit.client.changes.CustomDashboardScreen;
import com.google.gerrit.client.changes.ProjectDashboardScreen;
import com.google.gerrit.client.changes.QueryScreen;
import com.google.gerrit.client.dashboards.DashboardInfo;
import com.google.gerrit.client.dashboards.DashboardList;
import com.google.gerrit.client.diff.DisplaySide;
import com.google.gerrit.client.diff.SideBySide;
import com.google.gerrit.client.documentation.DocScreen;
import com.google.gerrit.client.editor.EditScreen;
import com.google.gerrit.client.groups.GroupApi;
import com.google.gerrit.client.groups.GroupInfo;
import com.google.gerrit.client.patches.UnifiedPatchScreen;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.RestApi;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences.DiffView;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Patch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.RunAsyncCallback;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.Window;
import com.google.gwtorm.client.KeyUtil;

public class Dispatcher {
  public static String toSideBySide(PatchSet.Id diffBase, Patch.Key id) {
    return toPatch("", diffBase, id);
  }

  public static String toSideBySide(PatchSet.Id diffBase,
      PatchSet.Id revision, String fileName) {
    return toPatch("", diffBase, revision, fileName, null, 0);
  }

  public static String toSideBySide(PatchSet.Id diffBase,
      PatchSet.Id revision, String fileName, DisplaySide side, int line) {
    return toPatch("", diffBase, revision, fileName, side, line);
  }

  public static String toUnified(PatchSet.Id diffBase,
      PatchSet.Id revision, String fileName) {
    return toPatch("unified", diffBase, revision, fileName, null, 0);
  }

  public static String toUnified(PatchSet.Id diffBase, Patch.Key id) {
    return toPatch("unified", diffBase, id);
  }

  public static String toPatch(String type, PatchSet.Id diffBase, Patch.Key id) {
    return toPatch(type, diffBase, id.getParentKey(), id.get(), null, 0);
  }

  public static String toEditScreen(PatchSet.Id revision, String fileName) {
    return toEditScreen(revision, fileName, 0);
  }

  public static String toEditScreen(PatchSet.Id revision, String fileName, int line) {
    return toPatch("edit", null, revision, fileName, null, line);
  }

  private static String toPatch(String type, PatchSet.Id diffBase,
      PatchSet.Id revision, String fileName, DisplaySide side, int line) {
    Change.Id c = revision.getParentKey();
    StringBuilder p = new StringBuilder();
    p.append("/c/").append(c).append("/");
    if (diffBase != null) {
      p.append(diffBase.get()).append("..");
    }
    p.append(revision.getId()).append("/").append(KeyUtil.encode(fileName));
    if (type != null && !type.isEmpty()
        && (!"sidebyside".equals(type) || preferUnified())) {
      p.append(",").append(type);
    }
    if (side == DisplaySide.A && line > 0) {
      p.append("@a").append(line);
    } else if (line > 0) {
      p.append("@").append(line);
    }
    return p.toString();
  }

  public static String toGroup(final AccountGroup.Id id) {
    return ADMIN_GROUPS + id.toString();
  }

  public static String toGroup(AccountGroup.Id id, String panel) {
    return ADMIN_GROUPS + id.toString() + "," + panel;
  }

  public static String toGroup(AccountGroup.UUID uuid) {
    return PageLinks.toGroup(uuid);
  }

  public static String toGroup(AccountGroup.UUID uuid, String panel) {
    return toGroup(uuid) + "," + panel;
  }

  public static String toProject(Project.NameKey n) {
    return toProjectAdmin(n, ProjectScreen.getSavedPanel());
  }

  public static String toProjectAdmin(Project.NameKey n, String panel) {
    if (panel == null || ProjectScreen.INFO.equals(panel)) {
      return ADMIN_PROJECTS + n.toString();
    }
    return ADMIN_PROJECTS + n.toString() + "," + panel;
  }

  static final String RELOAD_UI = "/reload-ui/";
  private static boolean wasStartedByReloadUI;

  void display(String token) {
    assert token != null;
    try {
      try {
        if (matchPrefix(RELOAD_UI, token)) {
          wasStartedByReloadUI = true;
          token = skip(token);
        }
        select(token);
      } finally {
        wasStartedByReloadUI = false;
      }
    } catch (RuntimeException err) {
      GWT.log("Error parsing history token: " + token, err);
      Gerrit.display(token, new NotFoundScreen());
    }
  }

  private static void select(final String token) {
    if (matchPrefix(QUERY, token)) {
      query(token);

    } else if (matchPrefix("/Documentation/", token)) {
      docSearch(token);

    } else if (matchPrefix("/c/", token)) {
      change(token);

    } else if (matchPrefix("/x/", token)) {
      extension(token);

    } else if (matchExact(MINE, token)) {
      String defaultScreenToken = Gerrit.getDefaultScreenToken();
      if (defaultScreenToken != null && !MINE.equals(defaultScreenToken)) {
        select(defaultScreenToken);
      } else {
        Gerrit.display(token, mine());
      }

    } else if (matchPrefix("/dashboard/", token)) {
      dashboard(token);

    } else if (matchPrefix(PROJECTS, token)) {
      projects(token);

    } else if (matchExact(SETTINGS, token)
        || matchPrefix("/settings/", token)
        || matchExact(MY_GROUPS, token)
        || matchExact("register", token)
        || matchExact(REGISTER, token)
        || matchPrefix("/register/", token)
        || matchPrefix("/VE/", token) || matchPrefix("VE,", token)
        || matchPrefix("/SignInFailure,", token)) {
      settings(token);

    } else if (matchPrefix("/admin/", token)) {
      admin(token);

    } else if (/* DEPRECATED URL */matchPrefix("/c2/", token)) {
      change(token);
    } else if (/* LEGACY URL */matchPrefix("all,", token)) {
      redirectFromLegacyToken(token, legacyAll(token));
    } else if (/* LEGACY URL */matchPrefix("mine,", token)
        || matchExact("mine", token)) {
      redirectFromLegacyToken(token, legacyMine(token));
    } else if (/* LEGACY URL */matchPrefix("project,", token)) {
      redirectFromLegacyToken(token, legacyProject(token));
    } else if (/* LEGACY URL */matchPrefix("change,", token)) {
      redirectFromLegacyToken(token, legacyChange(token));
    } else if (/* LEGACY URL */matchPrefix("patch,", token)) {
      redirectFromLegacyToken(token, legacyPatch(token));
    } else if (/* LEGACY URL */matchPrefix("admin,", token)) {
      redirectFromLegacyToken(token, legacyAdmin(token));
    } else if (/* LEGACY URL */matchPrefix("settings,", token)
        || matchPrefix("register,", token)
        || matchPrefix("q,", token)) {
      redirectFromLegacyToken(token, legacySettings(token));

    } else {
      Gerrit.display(token, new NotFoundScreen());
    }
  }

  private static void redirectFromLegacyToken(String oldToken, String newToken) {
    if (newToken != null) {
      Window.Location.replace(Window.Location.getPath() + "#" + newToken);
    } else {
      Gerrit.display(oldToken, new NotFoundScreen());
    }
  }

  private static String legacyMine(final String token) {
    if (matchExact("mine", token)) {
      return MINE;
    }

    if (matchExact("mine,starred", token)) {
      return toChangeQuery("is:starred");
    }

    if (matchExact("mine,drafts", token)) {
      return toChangeQuery("owner:self is:draft");
    }

    if (matchExact("mine,comments", token)) {
      return toChangeQuery("has:draft");
    }

    if (matchPrefix("mine,watched,", token)) {
      return toChangeQuery("is:watched status:open");
    }

    return null;
  }

  private static String legacyAll(final String token) {
    if (matchPrefix("all,abandoned,", token)) {
      return toChangeQuery("status:abandoned");
    }

    if (matchPrefix("all,merged,", token)) {
      return toChangeQuery("status:merged");
    }

    if (matchPrefix("all,open,", token)) {
      return toChangeQuery("status:open");
    }

    return null;
  }

  private static String legacyProject(final String token) {
    if (matchPrefix("project,open,", token)) {
      final String s = skip(token);
      final int c = s.indexOf(',');
      Project.NameKey proj = Project.NameKey.parse(s.substring(0, c));
      return toChangeQuery("status:open " + op("project", proj.get()));
    }

    if (matchPrefix("project,merged,", token)) {
      final String s = skip(token);
      final int c = s.indexOf(',');
      Project.NameKey proj = Project.NameKey.parse(s.substring(0, c));
      return toChangeQuery("status:merged " + op("project", proj.get()));
    }

    if (matchPrefix("project,abandoned,", token)) {
      final String s = skip(token);
      final int c = s.indexOf(',');
      Project.NameKey proj = Project.NameKey.parse(s.substring(0, c));
      return toChangeQuery("status:abandoned " + op("project", proj.get()));
    }

    return null;
  }

  private static String legacyChange(final String token) {
    final String s = skip(token);
    final String[] t = s.split(",", 2);
    if (t.length > 1 && matchPrefix("patchset=", t[1])) {
      return PageLinks.toChange(PatchSet.Id.parse(t[0] + "," + skip(t[1])));
    }
    return PageLinks.toChange(Change.Id.parse(t[0]));
  }

  private static String legacyPatch(String token) {
    if (/* LEGACY URL */matchPrefix("patch,sidebyside,", token)) {
      return toPatch("", null, Patch.Key.parse(skip(token)));
    }

    if (/* LEGACY URL */matchPrefix("patch,unified,", token)) {
      return toPatch("unified", null, Patch.Key.parse(skip(token)));
    }

    return null;
  }

  private static String legacyAdmin(String token) {
    if (matchPrefix("admin,group,", token)) {
      return ADMIN_GROUPS + skip(token);
    }

    if (matchPrefix("admin,project,", token)) {
      String rest = skip(token);
      int c = rest.indexOf(',');
      String panel;
      Project.NameKey k;
      if (0 < c) {
        panel = rest.substring(c + 1);
        k = Project.NameKey.parse(rest.substring(0, c));
      } else {
        panel = ProjectScreen.INFO;
        k = Project.NameKey.parse(rest);
      }
      return toProjectAdmin(k, panel);
    }

    return null;
  }

  private static String legacySettings(String token) {
    int c = token.indexOf(',');
    if (0 < c) {
      return "/" + token.substring(0, c) + "/" + token.substring(c + 1);
    }
    return null;
  }

  private static void query(String token) {
    String s = skip(token);
    int c = s.indexOf(',');
    Screen screen;
    if (c >= 0) {
      String prefix = s.substring(0, c);
      if (s.substring(c).equals(",n,z")) {
        // Respect legacy token with max sortkey.
        screen = new QueryScreen(prefix, 0);
      } else {
        screen = new QueryScreen(prefix, Integer.parseInt(s.substring(c + 1)));
      }
    } else {
      screen = new QueryScreen(s, 0);
    }
    Gerrit.display(token, screen);
  }

  private static Screen mine() {
    if (Gerrit.isSignedIn()) {
      return new AccountDashboardScreen(Gerrit.getUserAccount().getId());

    } else {
      Screen r = new AccountDashboardScreen(null);
      r.setRequiresSignIn(true);
      return r;
    }
  }

  private static void dashboard(final String token) {
    String rest = skip(token);
    if (rest.matches("[0-9]+")) {
      Gerrit.display(token, new AccountDashboardScreen(Account.Id.parse(rest)));
      return;
    }

    if (rest.equals("self")) {
      if (Gerrit.isSignedIn()) {
        Gerrit.display(token, new AccountDashboardScreen(Gerrit.getUserAccount().getId()));
      } else {
        Screen s = new AccountDashboardScreen(null);
        s.setRequiresSignIn(true);
        Gerrit.display(token, s);
      }
      return;
    }

    if (rest.startsWith("?")) {
      Gerrit.display(token, new CustomDashboardScreen(rest.substring(1)));
      return;
    }

    Gerrit.display(token, new NotFoundScreen());
  }

  private static void projects(final String token) {
    String rest = skip(token);
    int c = rest.indexOf(DASHBOARDS);
    if (0 <= c) {
      final String project = URL.decodePathSegment(rest.substring(0, c));
      rest = rest.substring(c);
      if (matchPrefix(DASHBOARDS, rest)) {
        final String dashboardId = skip(rest);
        GerritCallback<DashboardInfo> cb = new GerritCallback<DashboardInfo>() {
          @Override
          public void onSuccess(DashboardInfo result) {
            if (matchPrefix("/dashboard/", result.url())) {
              String params = skip(result.url()).substring(1);
              ProjectDashboardScreen dash = new ProjectDashboardScreen(
                  new Project.NameKey(project), params);
              Gerrit.display(token, dash);
            }
          }

          @Override
          public void onFailure(Throwable caught) {
            if ("default".equals(dashboardId) && RestApi.isNotFound(caught)) {
              Gerrit.display(toChangeQuery(
                  PageLinks.projectQuery(new Project.NameKey(project))));
            } else {
              super.onFailure(caught);
            }
          }
        };
        if ("default".equals(dashboardId)) {
          DashboardList.getDefault(new Project.NameKey(project), cb);
          return;
        }
        c = dashboardId.indexOf(":");
        if (0 <= c) {
          final String ref = URL.decodeQueryString(dashboardId.substring(0, c));
          final String path = URL.decodeQueryString(dashboardId.substring(c + 1));
          DashboardList.get(new Project.NameKey(project), ref + ":" + path, cb);
          return;
        }
      }
    }

    Gerrit.display(token, new NotFoundScreen());
  }

  private static void change(final String token) {
    String rest = skip(token);
    int c = rest.lastIndexOf(',');
    String panel = null;
    if (0 <= c) {
      panel = rest.substring(c + 1);
      rest = rest.substring(0, c);
      int at = panel.lastIndexOf('@');
      if (at > 0) {
        rest += panel.substring(at);
        panel = panel.substring(0, at);
      }
    }

    Change.Id id;
    int s = rest.indexOf('/');
    if (0 <= s) {
      id = Change.Id.parse(rest.substring(0, s));
      rest = rest.substring(s + 1);
    } else {
      id = Change.Id.parse(rest);
      rest = "";
    }

    if (rest.isEmpty()) {
      FileTable.Mode mode = FileTable.Mode.REVIEW;
      if (panel != null
          && (panel.equals("edit") || panel.startsWith("edit/"))) {
        mode = FileTable.Mode.EDIT;
        panel = null;
      }
      Gerrit.display(token, panel == null
          ? new ChangeScreen(id, null, null, false, mode)
          : new NotFoundScreen());
      return;
    }

    String psIdStr;
    s = rest.indexOf('/');
    if (0 <= s) {
      psIdStr = rest.substring(0, s);
      rest = rest.substring(s + 1);
    } else {
      psIdStr = rest;
      rest = "";
    }

    PatchSet.Id base = null;
    PatchSet.Id ps;
    int dotdot = psIdStr.indexOf("..");
    if (1 <= dotdot) {
      base = new PatchSet.Id(id, Integer.parseInt(psIdStr.substring(0, dotdot)));
      psIdStr = psIdStr.substring(dotdot + 2);
    }
    ps = toPsId(id, psIdStr);

    if (!rest.isEmpty()) {
      DisplaySide side = DisplaySide.B;
      int line = 0;
      int at = rest.lastIndexOf('@');
      if (at > 0) {
        String l = rest.substring(at+1);
        if (l.startsWith("a")) {
          side = DisplaySide.A;
          l = l.substring(1);
        }
        line = Integer.parseInt(l);
        rest = rest.substring(0, at);
      }
      Patch.Key p = new Patch.Key(ps, KeyUtil.decode(rest));
      patch(token, base, p, side, line, panel);
    } else {
      if (panel == null) {
        Gerrit.display(token,
            new ChangeScreen(id,
                base != null
                    ? String.valueOf(base.get())
                    : null,
                String.valueOf(ps.get()), false, FileTable.Mode.REVIEW));
      } else {
        Gerrit.display(token, new NotFoundScreen());
      }
    }
  }

  private static PatchSet.Id toPsId(Change.Id id, String psIdStr) {
    return new PatchSet.Id(id, psIdStr.equals("edit")
        ? 0
        : Integer.parseInt(psIdStr));
  }

  private static void extension(final String token) {
    ExtensionScreen view = new ExtensionScreen(skip(token));
    if (view.isFound()) {
      Gerrit.display(token, view);
    } else {
      Gerrit.display(token, new NotFoundScreen());
    }
  }

  private static void patch(String token,
      PatchSet.Id baseId,
      Patch.Key id,
      DisplaySide side,
      int line,
      String panelType) {
    String panel = panelType;
    if (panel == null) {
      int c = token.lastIndexOf(',');
      panel = 0 <= c ? token.substring(c + 1) : "";
    }

    if ("".equals(panel) || /* DEPRECATED URL */"cm".equals(panel)) {
      if (preferUnified()) {
        unified(token, baseId, id);
      } else {
        codemirror(token, baseId, id, side, line, false);
      }
    } else if ("sidebyside".equals(panel)) {
      codemirror(token, null, id, side, line, false);
    } else if ("unified".equals(panel)) {
      unified(token, baseId, id);
    } else if ("edit".equals(panel)) {
      codemirror(token, null, id, side, line, true);
    } else {
      Gerrit.display(token, new NotFoundScreen());
    }
  }

  private static boolean preferUnified() {
    return Gerrit.isSignedIn()
        && DiffView.UNIFIED_DIFF.equals(Gerrit.getUserAccount()
            .getGeneralPreferences()
            .getDiffView());
  }

  private static void unified(final String token,
      final PatchSet.Id baseId,
      final Patch.Key id) {
    GWT.runAsync(new AsyncSplit(token) {
      @Override
      public void onSuccess() {
        UnifiedPatchScreen.TopView top = Gerrit.getPatchScreenTopView();
        Gerrit.display(token, new UnifiedPatchScreen(id, top, baseId));
      }
    });
  }

  private static void codemirror(final String token, final PatchSet.Id baseId,
      final Patch.Key id, final DisplaySide side, final int line,
      final boolean edit) {
    GWT.runAsync(new AsyncSplit(token) {
      @Override
      public void onSuccess() {
        Gerrit.display(token, edit
            ? new EditScreen(baseId, id, line)
            : new SideBySide(baseId, id.getParentKey(), id.get(), side, line));
      }
    });
  }

  private static void settings(String token) {
    GWT.runAsync(new AsyncSplit(token) {
      @Override
      public void onSuccess() {
        Gerrit.display(token, select());
      }

      private Screen select() {
        if (matchExact(SETTINGS, token)) {
          return new MyProfileScreen();
        }

        if (matchExact(SETTINGS_PREFERENCES, token)) {
          return new MyPreferencesScreen();
        }

        if (matchExact(SETTINGS_PROJECTS, token)) {
          return new MyWatchedProjectsScreen();
        }

        if (matchExact(SETTINGS_CONTACT, token)) {
          return new MyContactInformationScreen();
        }

        if (matchExact(SETTINGS_SSHKEYS, token)) {
          return new MySshKeysScreen();
        }

        if (matchExact(SETTINGS_WEBIDENT, token)) {
          return new MyIdentitiesScreen();
        }

        if (matchExact(SETTINGS_HTTP_PASSWORD, token)) {
          return new MyPasswordScreen();
        }

        if (matchExact(MY_GROUPS, token)
            || matchExact(SETTINGS_MYGROUPS, token)) {
          return new MyGroupsScreen();
        }

        if (matchExact(SETTINGS_AGREEMENTS, token)
            && Gerrit.info().auth().useContributorAgreements()) {
          return new MyAgreementsScreen();
        }

        if (matchExact(REGISTER, token)
            || matchExact("/register/", token)
            || matchExact("register", token)) {
          return new RegisterScreen(MINE);
        } else if (matchPrefix("/register/", token)) {
          return new RegisterScreen("/" + skip(token));
        }

        if (matchPrefix("/VE/", token) || matchPrefix("VE,", token)) {
          return new ValidateEmailScreen(skip(token));
        }

        if (matchExact(SETTINGS_NEW_AGREEMENT, token)) {
          return new NewAgreementScreen();
        }

        if (matchPrefix(SETTINGS_NEW_AGREEMENT + "/", token)) {
          return new NewAgreementScreen(skip(token));
        }

        return new NotFoundScreen();
      }
    });
  }

  private static void admin(String token) {
    GWT.runAsync(new AsyncSplit(token) {
      @Override
      public void onSuccess() {
        if (matchExact(ADMIN_GROUPS, token)
            || matchExact("/admin/groups", token)) {
          Gerrit.display(token, new GroupListScreen());

        } else if (matchPrefix(ADMIN_GROUPS, token)) {
          String rest = skip(token);
          if (rest.startsWith("?")) {
            Gerrit.display(token, new GroupListScreen(rest.substring(1)));
          } else {
            group();
          }

        } else if (matchPrefix("/admin/groups", token)) {
          String rest = skip(token);
          if (rest.startsWith("?")) {
            Gerrit.display(token, new GroupListScreen(rest.substring(1)));
          }

        } else if (matchExact(ADMIN_PROJECTS, token)
            || matchExact("/admin/projects", token)) {
          Gerrit.display(token, new ProjectListScreen());

        } else if (matchPrefix(ADMIN_PROJECTS, token)) {
            String rest = skip(token);
            if (rest.startsWith("?")) {
              Gerrit.display(token, new ProjectListScreen(rest.substring(1)));
            } else {
              Gerrit.display(token, selectProject());
            }

        } else if (matchPrefix("/admin/projects", token)) {
          String rest = skip(token);
          if (rest.startsWith("?")) {
            Gerrit.display(token, new ProjectListScreen(rest.substring(1)));
          }

        } else if (matchPrefix(ADMIN_PLUGINS, token)
            || matchExact("/admin/plugins", token)) {
          Gerrit.display(token, new PluginListScreen());

        } else if (matchExact(ADMIN_CREATE_PROJECT, token)
            || matchExact("/admin/create-project", token)) {
          Gerrit.display(token, new CreateProjectScreen());

        } else if (matchExact(ADMIN_CREATE_GROUP, token)
            || matchExact("/admin/create-group", token)) {
          Gerrit.display(token, new CreateGroupScreen());

        } else {
          Gerrit.display(token, new NotFoundScreen());
        }
      }

      private void group() {
        final String panel;
        final String group;

        if (matchPrefix("/admin/groups/uuid-", token)) {
          String p = skip(token);
          int c = p.indexOf(',');
          if (c < 0) {
            group = p;
            panel = null;
          } else {
            group = p.substring(0, c);
            panel = p.substring(c + 1);
          }
        } else if (matchPrefix(ADMIN_GROUPS, token)) {
          String p = skip(token);
          int c = p.indexOf(',');
          if (c < 0) {
            group = p;
            panel = null;
          } else {
            group = p.substring(0, c);
            panel = p.substring(c + 1);
          }
        } else {
          Gerrit.display(token, new NotFoundScreen());
          return;
        }

        GroupApi.getGroupDetail(group, new GerritCallback<GroupInfo>() {
          @Override
          public void onSuccess(GroupInfo group) {
            if (panel == null || panel.isEmpty()) {
              // The token does not say which group screen should be shown,
              // as default for internal groups show the members, as default
              // for external and system groups show the info screen (since
              // for external and system groups the members cannot be
              // shown in the web UI).
              //
              if (AccountGroup.isInternalGroup(group.getGroupUUID())) {
                Gerrit.display(toGroup(group.getGroupId(), AccountGroupScreen.MEMBERS),
                    new AccountGroupMembersScreen(group, token));
              } else {
                Gerrit.display(toGroup(group.getGroupId(), AccountGroupScreen.INFO),
                    new AccountGroupInfoScreen(group, token));
              }
            } else if (AccountGroupScreen.INFO.equals(panel)) {
              Gerrit.display(token, new AccountGroupInfoScreen(group, token));
            } else if (AccountGroupScreen.MEMBERS.equals(panel)) {
              Gerrit.display(token, new AccountGroupMembersScreen(group, token));
            } else {
              Gerrit.display(token, new NotFoundScreen());
            }
          }
        });
      }

      private Screen selectProject() {
        if (matchPrefix(ADMIN_PROJECTS, token)) {
          String rest = skip(token);
          int c = rest.lastIndexOf(',');
          if (c < 0) {
            return new ProjectInfoScreen(Project.NameKey.parse(rest));
          } else if (c == 0) {
            return new NotFoundScreen();
          }

          int q = rest.lastIndexOf('?');
          if (q > 0 && rest.lastIndexOf(',', q) > 0) {
            c = rest.substring(0, q - 1).lastIndexOf(',');
          }

          Project.NameKey k = Project.NameKey.parse(rest.substring(0, c));
          String panel = rest.substring(c + 1);

          if (ProjectScreen.INFO.equals(panel)) {
            return new ProjectInfoScreen(k);
          }

          if (ProjectScreen.BRANCH.equals(panel)
              || matchPrefix(ProjectScreen.BRANCH, panel)) {
            return new ProjectBranchesScreen(k);
          }

          if (ProjectScreen.ACCESS.equals(panel)) {
            return new ProjectAccessScreen(k);
          }

          if (ProjectScreen.DASHBOARDS.equals(panel)) {
            return new ProjectDashboardsScreen(k);
          }
        }
        return new NotFoundScreen();
      }
    });
  }

  private static boolean matchExact(String want, String token) {
    return token.equals(want);
  }

  private static int prefixlen;

  private static boolean matchPrefix(String want, String token) {
    if (token.startsWith(want)) {
      prefixlen = want.length();
      return true;
    } else {
      return false;
    }
  }

  private static String skip(String token) {
    return token.substring(prefixlen);
  }

  private abstract static class AsyncSplit implements RunAsyncCallback {
    private final boolean isReloadUi;
    protected final String token;

    protected AsyncSplit(String token) {
      this.isReloadUi = wasStartedByReloadUI;
      this.token = token;
    }

    @Override
    public final void onFailure(Throwable reason) {
      if (!isReloadUi
          && "HTTP download failed with status 404".equals(reason.getMessage())) {
        // The server was upgraded since we last download the main script,
        // so the pointers to the splits aren't valid anymore.  Force the
        // page to reload itself and pick up the new code.
        //
        Gerrit.upgradeUI(token);
      } else {
        new ErrorDialog(reason).center();
      }
    }
  }

  private static void docSearch(final String token) {
    GWT.runAsync(new AsyncSplit(token) {
      @Override
      public void onSuccess() {
        Gerrit.display(token, new DocScreen(skip(token)));
      }
    });
  }
}
