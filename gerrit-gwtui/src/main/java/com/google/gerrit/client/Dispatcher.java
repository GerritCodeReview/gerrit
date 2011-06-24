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
import com.google.gerrit.client.admin.AccountGroupInfoScreen;
import com.google.gerrit.client.admin.AccountGroupMembersScreen;
import com.google.gerrit.client.admin.AccountGroupScreen;
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
import com.google.gwtorm.client.KeyUtil;

public class Dispatcher {
  public static String toPatchSideBySide(final Patch.Key id) {
    return toPatch("sidebyside", id);
  }

  public static String toPatchUnified(final Patch.Key id) {
    return toPatch("unified", id);
  }

  public static String toPatch(final String type, final Patch.Key id) {
    return "patch," + type + "," + id.toString();
  }

  public static String toPatch(final PatchScreen.Type type, final Patch.Key id) {
    if (type == PatchScreen.Type.SIDE_BY_SIDE) {
      return toPatchSideBySide(id);
    } else {
      return toPatchUnified(id);
    }
  }

  public static String toAccountGroup(final AccountGroup.Id id) {
    return "admin,group," + id.toString();
  }

  public static String toAccountGroup(final AccountGroup.Id id, final String tab) {
    return "admin,group," + id.toString() + "," + tab;
  }

  public static String toGroup(final AccountGroup.UUID uuid) {
    return "admin,group,uuid-" + uuid.toString();
  }

  public static String toGroup(final AccountGroup.UUID uuid, final String tab) {
    return "admin,group,uuid-" + uuid.toString() + "," + tab;
  }

  public static String toProjectAdmin(final Project.NameKey n, final String tab) {
    return "admin,project," + n.toString() + "," + tab;
  }

  static final String RELOAD_UI = "reload-ui,";
  private static boolean wasStartedByReloadUI;

  void display(String token) {
    assert token != null;
    try {
      try {
        if (token.startsWith(RELOAD_UI)) {
          wasStartedByReloadUI = true;
          token = skip(RELOAD_UI, token);
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
    if (token.startsWith("patch,")) {
      patch(token, null, 0, null, null);

    } else if (token.startsWith("change,publish,")) {
      publish(token);

    } else if (MINE.equals(token) || token.startsWith("mine,")) {
      Gerrit.display(token, mine(token));

    } else if (token.startsWith("all,")) {
      Gerrit.display(token, all(token));

    } else if (token.startsWith("project,")) {
      Gerrit.display(token, project(token));

    } else if (SETTINGS.equals(token) //
        || REGISTER.equals(token) //
        || token.startsWith("settings,") //
        || token.startsWith("register,") //
        || token.startsWith("VE,") //
        || token.startsWith("SignInFailure,")) {
      settings(token);

    } else if (token.startsWith("admin,")) {
      admin(token);

    } else {
      Gerrit.display(token, core(token));
    }
  }

  private static Screen mine(final String token) {
    if (MINE.equals(token)) {
      if (Gerrit.isSignedIn()) {
        return new AccountDashboardScreen(Gerrit.getUserAccount().getId());

      } else {
        final Screen r = new AccountDashboardScreen(null);
        r.setRequiresSignIn(true);
        return r;
      }

    } else if ("mine,starred".equals(token)) {
      return QueryScreen.forQuery("is:starred");

    } else if ("mine,drafts".equals(token)) {
      return QueryScreen.forQuery("has:draft");

    } else {
      String p = "mine,watched,";
      if (token.startsWith(p)) {
        return QueryScreen.forQuery("is:watched status:open", skip(p, token));
      }

      return new NotFoundScreen();
    }
  }

  private static Screen all(final String token) {
    String p;

    p = "all,abandoned,";
    if (token.startsWith(p)) {
      return QueryScreen.forQuery("status:abandoned", skip(p, token));
    }

    p = "all,merged,";
    if (token.startsWith(p)) {
      return QueryScreen.forQuery("status:merged", skip(p, token));
    }

    p = "all,open,";
    if (token.startsWith(p)) {
      return QueryScreen.forQuery("status:open", skip(p, token));
    }

    return new NotFoundScreen();
  }

  private static Screen project(final String token) {
    String p;

    p = "project,open,";
    if (token.startsWith(p)) {
      final String s = skip(p, token);
      final int c = s.indexOf(',');
      Project.NameKey proj = Project.NameKey.parse(s.substring(0, c));
      return QueryScreen.forQuery( //
          "status:open " + op("project", proj.get()), //
          s.substring(c + 1));
    }

    p = "project,merged,";
    if (token.startsWith(p)) {
      final String s = skip(p, token);
      final int c = s.indexOf(',');
      Project.NameKey proj = Project.NameKey.parse(s.substring(0, c));
      return QueryScreen.forQuery( //
          "status:merged " + op("project", proj.get()), //
          s.substring(c + 1));
    }

    p = "project,abandoned,";
    if (token.startsWith(p)) {
      final String s = skip(p, token);
      final int c = s.indexOf(',');
      Project.NameKey proj = Project.NameKey.parse(s.substring(0, c));
      return QueryScreen.forQuery( //
          "status:abandoned " + op("project", proj.get()), //
          s.substring(c + 1));
    }

    return new NotFoundScreen();
  }

  private static Screen core(final String token) {
    String p;

    p = "change,";
    if (token.startsWith(p)) {
      final String s = skip(p, token);
      final String q = "patchset=";
      final String t[] = s.split(",", 2);
      if (t.length > 1 && t[1].startsWith(q)) {
        return new ChangeScreen(PatchSet.Id.parse(t[0] + "," + skip(q, t[1])));
      }
      return new ChangeScreen(Change.Id.parse(t[0]));
    }

    p = "dashboard,";
    if (token.startsWith(p))
      return new AccountDashboardScreen(Account.Id.parse(skip(p, token)));

    p = "q,";
    if (token.startsWith(p)) {
      final String s = skip(p, token);
      final int c = s.indexOf(',');
      return new QueryScreen(s.substring(0, c), s.substring(c + 1));
    }

    return new NotFoundScreen();
  }

  private static void publish(String token) {
    new AsyncSplit(token) {
      public void onSuccess() {
        Gerrit.display(token, select());
      }

      private Screen select() {
        String p = "change,publish,";
        if (token.startsWith(p))
          return new PublishCommentScreen(PatchSet.Id.parse(skip(p, token)));
        return new NotFoundScreen();
      }
    }.onSuccess();
  }

  public static void patch(String token, final Patch.Key id,
      final int patchIndex, final PatchSetDetail patchSetDetail,
      final PatchTable patchTable) {
    GWT.runAsync(new AsyncSplit(token) {
      public void onSuccess() {
        Gerrit.display(token, select());
      }

      private Screen select() {
        String p;

        p = "patch,sidebyside,";
        if (token.startsWith(p)) {
          return new PatchScreen.SideBySide( //
              id != null ? id : Patch.Key.parse(skip(p, token)), //
              patchIndex, //
              patchSetDetail, //
              patchTable //
          );
        }

        p = "patch,unified,";
        if (token.startsWith(p)) {
          return new PatchScreen.Unified( //
              id != null ? id : Patch.Key.parse(skip(p, token)), //
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
        String p;

        if (token.equals(SETTINGS)) {
          return new MyProfileScreen();
        }

        if (token.equals(SETTINGS_PREFERENCES)) {
          return new MyPreferencesScreen();
        }

        if (token.equals(SETTINGS_PROJECTS)) {
          return new MyWatchedProjectsScreen();
        }

        if (token.equals(SETTINGS_CONTACT)) {
          return new MyContactInformationScreen();
        }

        if (token.equals(SETTINGS_SSHKEYS)) {
          return new MySshKeysScreen();
        }

        if (token.equals(SETTINGS_WEBIDENT)) {
          return new MyIdentitiesScreen();
        }

        if (token.equals(SETTINGS_HTTP_PASSWORD)) {
          return new MyPasswordScreen();
        }

        if (token.equals(SETTINGS_MYGROUPS)) {
          return new MyGroupsScreen();
        }

        if (token.equals(SETTINGS_AGREEMENTS)
            && Gerrit.getConfig().isUseContributorAgreements()) {
          return new MyAgreementsScreen();
        }

        p = "register,";
        if (token.startsWith(p)) {
          return new RegisterScreen(skip(p, token));
        } else if (REGISTER.equals(token)) {
          return new RegisterScreen(MINE);
        }

        p = "VE,";
        if (token.startsWith(p))
          return new ValidateEmailScreen(skip(p, token));

        p = "SignInFailure,";
        if (token.startsWith(p)) {
          final String[] args = skip(p, token).split(",");
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

        if (SETTINGS_NEW_AGREEMENT.equals(token))
          return new NewAgreementScreen();

        p = SETTINGS_NEW_AGREEMENT + ",";
        if (token.startsWith(p)) {
          return new NewAgreementScreen(skip(p, token));
        }

        return new NotFoundScreen();
      }
    });
  }

  private static void admin(String token) {
    GWT.runAsync(new AsyncSplit(token) {
      public void onSuccess() {
        if (token.startsWith("admin,group,")) {
          group();
        } else if (token.startsWith("admin,project,")) {
          Gerrit.display(token, selectProject());
        } else if (ADMIN_GROUPS.equals(token)) {
          Gerrit.display(token, new GroupListScreen());
        } else if (ADMIN_PROJECTS.equals(token)) {
          Gerrit.display(token, new ProjectListScreen());
        } else {
          Gerrit.display(token, new NotFoundScreen());
        }
      }

      private void group() {
        String p;
        final String tabToken;
        AccountGroup.Id groupId = null;
        AccountGroup.UUID groupUUID = null;

        p = "admin,group,uuid-";
        if (token.startsWith(p)) {
          p = skip(p, token);
          final int c = p.indexOf(',');
          if (c < 0) {
            groupUUID = AccountGroup.UUID.parse(p);
            tabToken = null;
          } else {
            groupUUID = AccountGroup.UUID.parse(p.substring(0, c));
            tabToken = p.substring(c + 1);
          }
        } else {
          p = skip("admin,group,", token);
          final int c = p.indexOf(',');
          if (c < 0) {
            groupId = AccountGroup.Id.parse(p);
            tabToken = null;
          } else {
            groupId = AccountGroup.Id.parse(p.substring(0, c));
            tabToken = p.substring(c + 1);
          }
        }

        Util.GROUP_SVC.groupDetail(groupId, groupUUID,
            new GerritCallback<GroupDetail>() {
              @Override
              public void onSuccess(final GroupDetail groupDetail) {
                if (tabToken == null) {
                  // the token does not say which group screen should be shown,
                  // as default for internal groups show the
                  // AccountGroupMembersScreen,
                  // as default for external and system groups show the
                  // AccountGroupInfoScreen (since for external and system
                  // groups the members cannot be shown)
                  if (groupDetail.group.getType() == AccountGroup.Type.INTERNAL) {
                    Gerrit.display(token + "," + AccountGroupScreen.MEMBERS,
                        new AccountGroupMembersScreen(groupDetail, token));
                  } else {
                    Gerrit.display(token + "," + AccountGroupScreen.INFO,
                        new AccountGroupInfoScreen(groupDetail, token));
                  }
                } else if (AccountGroupScreen.INFO.equals(tabToken)) {
                  Gerrit.display(token, new AccountGroupInfoScreen(groupDetail,
                      token));
                } else if (AccountGroupScreen.MEMBERS.equals(tabToken)) {
                  Gerrit.display(token, new AccountGroupMembersScreen(
                      groupDetail, token));
                } else {
                  Gerrit.display(token, new NotFoundScreen());
                }
              }
            });
      }

      private Screen selectProject() {
        String p = "admin,project,";
        if (token.startsWith(p)) {
          p = skip(p, token);
          final int c = p.indexOf(',');
          final Project.NameKey k = Project.NameKey.parse(p.substring(0, c));
          final boolean isWild = k.equals(Gerrit.getConfig().getWildProject());
          p = p.substring(c + 1);

          if (ProjectScreen.INFO.equals(p)) {
            return new ProjectInfoScreen(k);
          }

          if (!isWild && ProjectScreen.BRANCH.equals(p)) {
            return new ProjectBranchesScreen(k);
          }

          if (ProjectScreen.ACCESS.equals(p)) {
            return new ProjectAccessScreen(k);
          }

          return new NotFoundScreen();
        }

        return new NotFoundScreen();
      }
    });
  }

  private static String skip(final String prefix, final String in) {
    return in.substring(prefix.length());
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
