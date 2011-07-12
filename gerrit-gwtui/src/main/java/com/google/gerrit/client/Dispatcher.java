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

import static com.google.gerrit.common.PageLinks.ADMIN_CREATE_PROJECT;
import static com.google.gerrit.common.PageLinks.ADMIN_GROUPS;
import static com.google.gerrit.common.PageLinks.ADMIN_PROJECTS;
import static com.google.gerrit.common.PageLinks.MINE;
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
import com.google.gerrit.client.admin.CreateProjectScreen;
import com.google.gerrit.client.admin.GroupListScreen;
import com.google.gerrit.client.admin.ProjectAccessScreen;
import com.google.gerrit.client.admin.ProjectBranchesScreen;
import com.google.gerrit.client.admin.ProjectInfoScreen;
import com.google.gerrit.client.admin.ProjectListScreen;
import com.google.gerrit.client.admin.ProjectScreen;
import com.google.gerrit.client.admin.Util;
import com.google.gerrit.client.auth.openid.OpenIdSignInDialog;
import com.google.gerrit.client.auth.userpass.UserPassSignInDialog;
import com.google.gerrit.client.changes.AccountDashboardScreen;
import com.google.gerrit.client.changes.ChangeScreen;
import com.google.gerrit.client.changes.PatchTable;
import com.google.gerrit.client.changes.PublishCommentScreen;
import com.google.gerrit.client.changes.QueryScreen;
import com.google.gerrit.client.patches.PatchScreen;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.auth.SignInMode;
import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.RunAsyncCallback;
import com.google.gwt.user.client.Window;
import com.google.gwtorm.client.KeyUtil;

public class Dispatcher {
  public static String toPatchSideBySide(final Patch.Key id) {
    return toPatch("", id);
  }

  public static String toPatchUnified(final Patch.Key id) {
    return toPatch("unified", id);
  }

  private static String toPatch(String type, final Patch.Key id) {
    PatchSet.Id ps = id.getParentKey();
    Change.Id c = ps.getParentKey();
    if (type != null && !type.isEmpty()) {
      type = "," + type;
    }
    return "/c/" + c + "/" + ps.get() + "/" + KeyUtil.encode(id.get()) + type;
  }

  public static String toPatch(final PatchScreen.Type type, final Patch.Key id) {
    if (type == PatchScreen.Type.SIDE_BY_SIDE) {
      return toPatchSideBySide(id);
    } else {
      return toPatchUnified(id);
    }
  }

  public static String toPublish(PatchSet.Id ps) {
    Change.Id c = ps.getParentKey();
    return "/c/" + c + "/" + ps.get() + ",publish";
  }

  public static String toGroup(final AccountGroup.Id id) {
    return "/admin/groups/" + id.toString();
  }

  public static String toGroup(AccountGroup.Id id, String panel) {
    return "/admin/groups/" + id.toString() + "," + panel;
  }

  public static String toGroup(final AccountGroup.UUID uuid) {
    return "/admin/groups/uuid-" + uuid.toString();
  }

  public static String toGroup(AccountGroup.UUID uuid, String panel) {
    return "/admin/groups/uuid-" + uuid.toString() + "," + panel;
  }

  public static String toProjectAdmin(Project.NameKey n, String panel) {
    if (ProjectScreen.INFO.equals(panel)) {
      return "/admin/projects/" + n.toString();
    }
    return "/admin/projects/" + n.toString() + "," + panel;
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
    if (matchPrefix("/q/", token)) {
      query(token);

    } else if (matchPrefix("/c/", token)) {
      change(token);

    } else if (matchExact(MINE, token)) {
      Gerrit.display(token, mine(token));

    } else if (matchPrefix("/dashboard/", token)) {
      dashboard(token);

    } else if (matchExact(SETTINGS, token) //
        || matchPrefix("/settings/", token) //
        || matchExact("register", token) //
        || matchExact(REGISTER, token) //
        || matchPrefix("/register/", token) //
        || matchPrefix("/VE/", token) || matchPrefix("VE,", token) //
        || matchPrefix("/SignInFailure,", token)) {
      settings(token);

    } else if (matchPrefix("/admin/", token)) {
      admin(token);

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
      return PageLinks.toChangeQuery("is:starred");
    }

    if (matchExact("mine,drafts", token)) {
      return PageLinks.toChangeQuery("has:draft");
    }

    if (matchPrefix("mine,watched,", token)) {
      return PageLinks.toChangeQuery("is:watched status:open", skip(token));
    }

    return null;
  }

  private static String legacyAll(final String token) {
    if (matchPrefix("all,abandoned,", token)) {
      return PageLinks.toChangeQuery("status:abandoned", skip(token));
    }

    if (matchPrefix("all,merged,", token)) {
      return PageLinks.toChangeQuery("status:merged", skip(token));
    }

    if (matchPrefix("all,open,", token)) {
      return PageLinks.toChangeQuery("status:open", skip(token));
    }

    return null;
  }

  private static String legacyProject(final String token) {
    if (matchPrefix("project,open,", token)) {
      final String s = skip(token);
      final int c = s.indexOf(',');
      Project.NameKey proj = Project.NameKey.parse(s.substring(0, c));
      return PageLinks.toChangeQuery( //
          "status:open " + op("project", proj.get()), //
          s.substring(c + 1));
    }

    if (matchPrefix("project,merged,", token)) {
      final String s = skip(token);
      final int c = s.indexOf(',');
      Project.NameKey proj = Project.NameKey.parse(s.substring(0, c));
      return PageLinks.toChangeQuery( //
          "status:merged " + op("project", proj.get()), //
          s.substring(c + 1));
    }

    if (matchPrefix("project,abandoned,", token)) {
      final String s = skip(token);
      final int c = s.indexOf(',');
      Project.NameKey proj = Project.NameKey.parse(s.substring(0, c));
      return PageLinks.toChangeQuery( //
          "status:abandoned " + op("project", proj.get()), //
          s.substring(c + 1));
    }

    return null;
  }

  private static String legacyChange(final String token) {
    final String s = skip(token);
    final String q = "patchset=";
    final String t[] = s.split(",", 2);
    if (t.length > 1 && matchPrefix("patchset=", t[1])) {
      return PageLinks.toChange(PatchSet.Id.parse(t[0] + "," + skip(t[1])));
    }
    return PageLinks.toChange(Change.Id.parse(t[0]));
  }

  private static String legacyPatch(String token) {
    if (/* LEGACY URL */matchPrefix("patch,sidebyside,", token)) {
      return toPatchSideBySide(Patch.Key.parse(skip(token)));
    }

    if (/* LEGACY URL */matchPrefix("patch,unified,", token)) {
      return toPatchUnified(Patch.Key.parse(skip(token)));
    }

    return null;
  }

  private static String legacyAdmin(String token) {
    if (matchPrefix("admin,group,", token)) {
      return "/admin/groups/" + skip(token);
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

  private static void query(final String token) {
    final String s = skip(token);
    final int c = s.indexOf(',');
    Gerrit.display(token, new QueryScreen(s.substring(0, c), s.substring(c + 1)));
  }

  private static Screen mine(final String token) {
    if (Gerrit.isSignedIn()) {
      return new AccountDashboardScreen(Gerrit.getUserAccount().getId());

    } else {
      Screen r = new AccountDashboardScreen(null);
      r.setRequiresSignIn(true);
      return r;
    }
  }

  private static void dashboard(final String token) {
    Gerrit.display(token, //
        new AccountDashboardScreen(Account.Id.parse(skip(token))));
  }

  private static void change(final String token) {
    String rest = skip(token);
    int c = rest.lastIndexOf(',');
    String panel = null;
    if (0 <= c) {
      panel = rest.substring(c + 1);
      rest = rest.substring(0, c);
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
      Gerrit.display(token, panel== null //
          ? new ChangeScreen(id) //
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

    PatchSet.Id ps = new PatchSet.Id(id, Integer.parseInt(psIdStr));
    if (!rest.isEmpty()) {
      Patch.Key p = new Patch.Key(ps, rest);
      patch(token, p, 0, null, null, panel);
    } else {
      if (panel == null) {
        Gerrit.display(token, new ChangeScreen(ps));
      } else if ("publish".equals(panel)) {
        publish(ps);
      } else {
        Gerrit.display(token, new NotFoundScreen());
      }
    }
  }

  private static void publish(final PatchSet.Id ps) {
    String token = toPublish(ps);
    new AsyncSplit(token) {
      public void onSuccess() {
        Gerrit.display(token, select());
      }

      private Screen select() {
        return new PublishCommentScreen(ps);
      }
    }.onSuccess();
  }

  public static void patch(String token, final Patch.Key id,
      final int patchIndex, final PatchSetDetail patchSetDetail,
      final PatchTable patchTable, final PatchScreen.TopView topView) {
    patch(token, id, patchIndex, patchSetDetail, patchTable, topView, null);
  }

  public static void patch(String token, final Patch.Key id,
      final int patchIndex, final PatchSetDetail patchSetDetail,
      final PatchTable patchTable, final String panelType) {
    patch(token, id, patchIndex, patchSetDetail, patchTable,
        null, panelType);
  }

  public static void patch(String token, final Patch.Key id,
      final int patchIndex, final PatchSetDetail patchSetDetail,
      final PatchTable patchTable, final PatchScreen.TopView topView,
      final String panelType) {

    final PatchScreen.TopView top =  topView == null ?
        Gerrit.getPatchScreenTopView() : topView;

    GWT.runAsync(new AsyncSplit(token) {
      public void onSuccess() {
        Gerrit.display(token, select());
      }

      private Screen select() {
        if (id != null) {
          String panel = panelType;
          if (panel == null) {
            int c = token.lastIndexOf(',');
            panel = 0 <= c ? token.substring(c + 1) : "";
          }

          if ("".equals(panel)) {
            return new PatchScreen.SideBySide( //
                id, //
                patchIndex, //
                patchSetDetail, //
                patchTable, //
                top //
            );
          } else if ("unified".equals(panel)) {
            return new PatchScreen.Unified( //
                id, //
                patchIndex, //
                patchSetDetail, //
                patchTable, //
                top //
            );
          }
        }

        return new NotFoundScreen();
      }
    });
  }

  private static void settings(String token) {
    GWT.runAsync(new AsyncSplit(token) {
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

        if (matchExact(SETTINGS_MYGROUPS, token)) {
          return new MyGroupsScreen();
        }

        if (matchExact(SETTINGS_AGREEMENTS, token)
            && Gerrit.getConfig().isUseContributorAgreements()) {
          return new MyAgreementsScreen();
        }

        if (matchExact(REGISTER, token)
            || matchExact("/register/", token)
            || matchExact("register", token)) {
          return new RegisterScreen(MINE);
        } else if (matchPrefix("/register/", token)) {
          return new RegisterScreen("/" + skip(token));
        }

        if (matchPrefix("/VE/", token) || matchPrefix("VE,", token))
          return new ValidateEmailScreen(skip(token));

        if (matchPrefix("/SignInFailure,", token)) {
          final String[] args = skip(token).split(",");
          final SignInMode mode = SignInMode.valueOf(args[0]);
          final String msg = KeyUtil.decode(args[1]);
          final String to = MINE;
          switch (Gerrit.getConfig().getAuthType()) {
            case OPENID:
              new OpenIdSignInDialog(mode, to, msg).center();
              break;
            case LDAP:
            case LDAP_BIND:
              new UserPassSignInDialog(to, msg).center();
              break;
            default:
              return null;
          }
          switch (mode) {
            case SIGN_IN:
              return QueryScreen.forQuery("status:open");
            case LINK_IDENTIY:
              return new MyIdentitiesScreen();
          }
        }

        if (matchExact(SETTINGS_NEW_AGREEMENT, token))
          return new NewAgreementScreen();

        if (matchPrefix(SETTINGS_NEW_AGREEMENT + "/", token)) {
          return new NewAgreementScreen(skip(token));
        }

        return new NotFoundScreen();
      }
    });
  }

  private static void admin(String token) {
    GWT.runAsync(new AsyncSplit(token) {
      public void onSuccess() {
        if (matchExact(ADMIN_GROUPS, token)
            || matchExact("/admin/groups", token)) {
          Gerrit.display(token, new GroupListScreen());

        } else if (matchPrefix("/admin/groups/", token)) {
          group();

        } else if (matchExact(ADMIN_PROJECTS, token)
            || matchExact("/admin/projects", token)) {
          Gerrit.display(token, new ProjectListScreen());

        } else if (matchPrefix("/admin/projects/", token)) {
          Gerrit.display(token, selectProject());

        } else if (matchExact(ADMIN_CREATE_PROJECT, token)
            || matchExact("/admin/create-project", token)) {
          Gerrit.display(token, new CreateProjectScreen());

        } else {
          Gerrit.display(token, new NotFoundScreen());
        }
      }

      private void group() {
        final String panel;
        AccountGroup.Id groupId = null;
        AccountGroup.UUID groupUUID = null;

        if (matchPrefix("/admin/groups/uuid-", token)) {
          String p = skip(token);
          int c = p.indexOf(',');
          if (c < 0) {
            groupUUID = AccountGroup.UUID.parse(p);
            panel = null;
          } else {
            groupUUID = AccountGroup.UUID.parse(p.substring(0, c));
            panel = p.substring(c + 1);
          }
        } else if (matchPrefix("/admin/groups/", token)) {
          String p = skip(token);
          int c = p.indexOf(',');
          if (c < 0) {
            groupId = AccountGroup.Id.parse(p);
            panel = null;
          } else {
            groupId = AccountGroup.Id.parse(p.substring(0, c));
            panel = p.substring(c + 1);
          }
        } else {
          Gerrit.display(token, new NotFoundScreen());
          return;
        }

        Util.GROUP_SVC.groupDetail(groupId, groupUUID,
            new GerritCallback<GroupDetail>() {
              @Override
              public void onSuccess(GroupDetail groupDetail) {
                if (panel == null || panel.isEmpty()) {
                  // The token does not say which group screen should be shown,
                  // as default for internal groups show the members, as default
                  // for external and system groups show the info screen (since
                  // for external and system groups the members cannot be
                  // shown in the web UI).
                  //
                  if (groupDetail.group.getType() == AccountGroup.Type.INTERNAL) {
                    Gerrit.display(toGroup(groupDetail.group.getId(),
                        AccountGroupScreen.MEMBERS),
                        new AccountGroupMembersScreen(groupDetail, token));
                  } else {
                    Gerrit.display(toGroup(groupDetail.group.getId(),
                        AccountGroupScreen.INFO),
                        new AccountGroupInfoScreen(groupDetail, token));
                  }
                } else if (AccountGroupScreen.INFO.equals(panel)) {
                  Gerrit.display(token,
                      new AccountGroupInfoScreen(groupDetail, token));
                } else if (AccountGroupScreen.MEMBERS.equals(panel)) {
                  Gerrit.display(token,
                      new AccountGroupMembersScreen(groupDetail, token));
                } else {
                  Gerrit.display(token,new NotFoundScreen());
                }
              }
            });
      }

      private Screen selectProject() {
        if (matchPrefix("/admin/projects/", token)) {
          String rest = skip(token);
          int c = rest.lastIndexOf(',');
          if (c < 0) {
            return new ProjectInfoScreen(Project.NameKey.parse(rest));
          } else if (c == 0) {
            return new NotFoundScreen();
          }

          Project.NameKey k = Project.NameKey.parse(rest.substring(0, c));
          String panel = rest.substring(c + 1);

          if (ProjectScreen.INFO.equals(panel)) {
            return new ProjectInfoScreen(k);
          }

          if (ProjectScreen.BRANCH.equals(panel)
              && !k.equals(Gerrit.getConfig().getWildProject())) {
            return new ProjectBranchesScreen(k);
          }

          if (ProjectScreen.ACCESS.equals(panel)) {
            return new ProjectAccessScreen(k);
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

  private static abstract class AsyncSplit implements RunAsyncCallback {
    private final boolean isReloadUi;
    protected final String token;

    protected AsyncSplit(String token) {
      this.isReloadUi = wasStartedByReloadUI;
      this.token = token;
    }

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
}
