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
import com.google.gerrit.client.admin.AccountGroupScreen;
import com.google.gerrit.client.admin.GroupListScreen;
import com.google.gerrit.client.admin.ProjectAccessScreen;
import com.google.gerrit.client.admin.ProjectBranchesScreen;
import com.google.gerrit.client.admin.ProjectInfoScreen;
import com.google.gerrit.client.admin.ProjectListScreen;
import com.google.gerrit.client.admin.ProjectScreen;
import com.google.gerrit.client.auth.openid.OpenIdSignInDialog;
import com.google.gerrit.client.auth.userpass.UserPassSignInDialog;
import com.google.gerrit.client.changes.AccountDashboardScreen;
import com.google.gerrit.client.changes.ChangeScreen;
import com.google.gerrit.client.changes.PatchTable;
import com.google.gerrit.client.changes.PublishCommentScreen;
import com.google.gerrit.client.changes.QueryScreen;
import com.google.gerrit.client.patches.PatchScreen;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.auth.SignInMode;
import com.google.gerrit.common.data.PatchSetDetail;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Patch;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.Project;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.RunAsyncCallback;
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

  public static String toAccountGroup(final AccountGroup.Id id) {
    return "/admin/group/" + id.toString();
  }

  public static String toGroup(final AccountGroup.UUID uuid) {
    return "/admin/group/uuid-" + uuid.toString();
  }

  public static String toProjectAdmin(final Project.NameKey n, final String tab) {
    return "/admin/project/" + tab + "/" + n.toString();
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
    if (matchPrefix("/patch/", token)) {
      patch(token, null, 0, null, null);

    } else if (matchPrefix("/change/publish/", token)) {
      publish(token);

    } else if (matchExact(MINE, token)) {
      Gerrit.display(token, mine(token));

    } else if (matchExact(SETTINGS, token) //
        || matchExact(REGISTER, token) //
        || matchPrefix("/settings/", token) //
        || matchPrefix("/register/", token) //
        || matchPrefix("VE,", token) //
        || matchPrefix("SignInFailure,", token)) {
      settings(token);

    } else if (matchPrefix("/admin/", token)) {
      admin(token);

    } else if (/* LEGACY URL */matchPrefix("all,", token)) {
      Gerrit.display(token, legacyAll(token));
    } else if (/* LEGACY URL */matchPrefix("mine,", token)) {
      Gerrit.display(token, legacyMine(token));
    } else if (/* LEGACY URL */matchPrefix("project,", token)) {
      Gerrit.display(token, legacyProject(token));

    } else {
      Gerrit.display(token, core(token));
    }
  }

  private static Screen mine(final String token) {
    if (Gerrit.isSignedIn()) {
      return new AccountDashboardScreen(Gerrit.getUserAccount().getId());

    } else {
      final Screen r = new AccountDashboardScreen(null);
      r.setRequiresSignIn(true);
      return r;
    }
  }

  private static Screen legacyMine(final String token) {
    if (matchExact("mine,starred", token)) {
      return QueryScreen.forQuery("is:starred");
    }

    if (matchExact("mine,drafts", token)) {
      return QueryScreen.forQuery("has:draft");
    }

    if (matchPrefix("mine,watched,", token)) {
      return QueryScreen.forQuery("is:watched status:open", skip(token));
    }

    return new NotFoundScreen();
  }

  private static Screen legacyAll(final String token) {
    if (matchPrefix("all,abandoned,", token)) {
      return QueryScreen.forQuery("status:abandoned", skip(token));
    }

    if (matchPrefix("all,merged,", token)) {
      return QueryScreen.forQuery("status:merged", skip(token));
    }

    if (matchPrefix("all,open,", token)) {
      return QueryScreen.forQuery("status:open", skip(token));
    }

    return new NotFoundScreen();
  }

  private static Screen legacyProject(final String token) {
    if (matchPrefix("project,open,", token)) {
      final String s = skip(token);
      final int c = s.indexOf(',');
      Project.NameKey proj = Project.NameKey.parse(s.substring(0, c));
      return QueryScreen.forQuery( //
          "status:open " + op("project", proj.get()), //
          s.substring(c + 1));
    }

    if (matchPrefix("project,merged,", token)) {
      final String s = skip(token);
      final int c = s.indexOf(',');
      Project.NameKey proj = Project.NameKey.parse(s.substring(0, c));
      return QueryScreen.forQuery( //
          "status:merged " + op("project", proj.get()), //
          s.substring(c + 1));
    }

    if (matchPrefix("project,abandoned,", token)) {
      final String s = skip(token);
      final int c = s.indexOf(',');
      Project.NameKey proj = Project.NameKey.parse(s.substring(0, c));
      return QueryScreen.forQuery( //
          "status:abandoned " + op("project", proj.get()), //
          s.substring(c + 1));
    }

    return new NotFoundScreen();
  }

  private static Screen core(final String token) {
    if (matchPrefix("/c/", token)) {
      String rest = skip(token);
      int c = rest.indexOf('/');
      if (c < 0) {
        return new ChangeScreen(Change.Id.parse(rest));
      }

      Change.Id id = Change.Id.parse(rest.substring(0, c));
      rest = rest.substring(c + 1);
      if (rest.isEmpty()) {
        return new ChangeScreen(id);
      }

      c = rest.indexOf('/');
      String part = 0 <= c ? rest.substring(0, c) : rest;
      PatchSet.Id ps = new PatchSet.Id(id, Integer.parseInt(part));
      if (c < 0 || c == rest.length() - 1) {
        return new ChangeScreen(ps);
      }
      rest = rest.substring(c + 1);
      c = rest.lastIndexOf(',');
      String type;
      if (0 <= c) {
        part = rest.substring(0, c);
        type = rest.substring(c + 1);
      } else {
        part = rest;
        type = null;
      }
      Patch.Key p = new Patch.Key(ps, part);
      patch(token, p, 0, null, null, type);
    }

    if (matchPrefix("/dashboard/", token))
      return new AccountDashboardScreen(Account.Id.parse(skip(token)));

    if (matchPrefix("/q/", token)) {
      final String s = skip(token);
      final int c = s.indexOf(',');
      return new QueryScreen(s.substring(0, c), s.substring(c + 1));
    }

    if (/* LEGACY URL */matchPrefix("change,", token)) {
      final String s = skip(token);
      final String q = "patchset=";
      final String t[] = s.split(",", 2);
      if (t.length > 1 && matchPrefix("patchset=", t[1])) {
        return new ChangeScreen(PatchSet.Id.parse(t[0] + "," + skip(t[1])));
      }
      return new ChangeScreen(Change.Id.parse(t[0]));
    }

    return new NotFoundScreen();
  }

  private static void publish(String token) {
    new AsyncSplit(token) {
      public void onSuccess() {
        Gerrit.display(token, select());
      }

      private Screen select() {
        if (matchPrefix("/change/publish/", token))
          return new PublishCommentScreen(PatchSet.Id.parse(skip(token)));
        return new NotFoundScreen();
      }
    }.onSuccess();
  }

  public static void patch(String token, final Patch.Key id,
      final int patchIndex, final PatchSetDetail patchSetDetail,
      final PatchTable patchTable) {
    patch(token, id, patchIndex, patchSetDetail, patchTable, "");
  }

  public static void patch(String token, final Patch.Key id,
      final int patchIndex, final PatchSetDetail patchSetDetail,
      final PatchTable patchTable,
      final String type) {
    GWT.runAsync(new AsyncSplit(token) {
      public void onSuccess() {
        Gerrit.display(token, select());
      }

      private Screen select() {
        if (matchPrefix("/c/", token) && id != null) {
          if (type == null || "".equals(type)) {
            return new PatchScreen.SideBySide( //
                id, //
                patchIndex, //
                patchSetDetail, //
                patchTable //
            );
          } else if ("unified".equals(type)) {
            return new PatchScreen.Unified( //
                id, //
                patchIndex, //
                patchSetDetail, //
                patchTable //
            );
          } else {
            return new NotFoundScreen();
          }
        }

        if (/* LEGACY URL */matchPrefix("patch,sidebyside,", token)) {
          return new PatchScreen.SideBySide( //
              id != null ? id : Patch.Key.parse(skip(token)), //
              patchIndex, //
              patchSetDetail, //
              patchTable //
          );
        }

        if (/* LEGACY URL */matchPrefix("patch,unified,", token)) {
          return new PatchScreen.Unified( //
              id != null ? id : Patch.Key.parse(skip(token)), //
              patchIndex, //
              patchSetDetail, //
              patchTable //
          );
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

        if (matchPrefix("/register/", token)) {
          return new RegisterScreen(skip(token));
        } else if (matchExact(REGISTER, token)) {
          return new RegisterScreen(MINE);
        }

        if (matchPrefix("VE,", token))
          return new ValidateEmailScreen(skip(token));

        if (matchPrefix("SignInFailure,", token)) {
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
        Gerrit.display(token, select());
      }

      private Screen select() {
        if (matchPrefix("/admin/group/uuid-", token))
          return new AccountGroupScreen(AccountGroup.UUID.parse(skip(token)));

        if (matchPrefix("/admin/group/", token))
          return new AccountGroupScreen(AccountGroup.Id.parse(skip(token)));

        if (matchPrefix("/admin/project/", token)) {
          String panel;
          Project.NameKey k;
          if (token.startsWith("/")) {
            // New-style (/admin/projects/PANEL/NAME).
            String rest = skip(token);
            int c = rest.indexOf('/');
            panel = rest.substring(0, c);
            k = Project.NameKey.parse(rest.substring(c + 1));
          } else {
            // Old-style URL (admin,projects,NAME,PANEL).
            String rest = skip(token);
            int c = rest.indexOf(',');
            panel = rest.substring(c + 1);
            k = Project.NameKey.parse(rest.substring(0, c));
          }

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

          return new NotFoundScreen();
        }

        if (matchExact(ADMIN_GROUPS, token)) {
          return new GroupListScreen();
        }

        if (matchExact(ADMIN_PROJECTS, token)) {
          return new ProjectListScreen();
        }

        return new NotFoundScreen();
      }
    });
  }

  private static boolean matchExact(String want, String token) {
    if (token.equals(want)) {
      return true;
    }

    if (token.startsWith("/")) { // New style token would never match old.
      return false;
    }

    // See if its an older style token.
    return !token.startsWith("/") && token.equals(want.substring(1).replace('/', ','));
  }

  private static int prefixlen;

  private static boolean matchPrefix(String want, String token) {
    if (token.startsWith(want)) {
      prefixlen = want.length();
      return true;
    }

    if (token.startsWith("/")) { // New style token would never match old.
      return false;
    }

    // See if its an older style token.
    if (!token.startsWith("/") && token.startsWith(want.substring(1).replace('/', ','))) {
      prefixlen = want.length() - 1;
      return true;
    }
    return false;
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
