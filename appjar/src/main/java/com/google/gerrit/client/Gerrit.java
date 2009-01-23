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

package com.google.gerrit.client;

import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.client.data.SystemInfoService;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.LinkMenuBar;
import com.google.gerrit.client.ui.LinkMenuItem;
import com.google.gerrit.client.ui.Screen;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.HistoryListener;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwtjsonrpc.client.JsonUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

public class Gerrit implements EntryPoint {
  /**
   * Name of the Cookie our authentication data is stored in.
   * <p>
   * If this cookie has a value we assume we are signed in.
   * 
   * @see #isSignedIn()
   */
  public static final String ACCOUNT_COOKIE = "GerritAccount";

  public static final GerritConstants C = GWT.create(GerritConstants.class);
  public static final GerritMessages M = GWT.create(GerritMessages.class);
  public static final GerritIcons ICONS = GWT.create(GerritIcons.class);
  public static final SystemInfoService SYSTEM_SVC;

  private static Account myAccount;
  private static final ArrayList<SignedInListener> signedInListeners =
      new ArrayList<SignedInListener>();

  private static LinkMenuBar menuBar;
  private static RootPanel body;
  private static Screen currentScreen;
  private static final LinkedHashMap<Object, Screen> priorScreens =
      new LinkedHashMap<Object, Screen>(10, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(final Entry<Object, Screen> eldest) {
          return 3 <= size();
        }
      };

  static {
    SYSTEM_SVC = GWT.create(SystemInfoService.class);
    JsonUtil.bind(SYSTEM_SVC, "rpc/SystemInfoService");
  }

  public static void display(final String historyToken, final boolean go) {
    History.newItem(historyToken, go);
    if (!go && historyHooks != null) {
      dispatchHistoryHooks(historyToken);
    }
  }

  public static void display(final String historyToken, final Screen view) {
    History.newItem(historyToken, false);
    display(view);
    if (historyHooks != null) {
      dispatchHistoryHooks(historyToken);
    }
  }

  public static void display(final Screen view) {
    if (view.isRequiresSignIn() && !isSignedIn()) {
      doSignIn(new AsyncCallback<Object>() {
        public void onSuccess(final Object result) {
          display(view);
        }

        public void onFailure(final Throwable caught) {
        }
      });
      return;
    }

    if (currentScreen != null) {
      body.remove(currentScreen);
      final Object sct = currentScreen.getScreenCacheToken();
      if (sct != null) {
        priorScreens.put(sct, currentScreen);
      }
    }

    final Screen p = priorScreens.get(view.getScreenCacheToken());
    currentScreen = p != null ? p.recycleThis(view) : view;
    body.add(currentScreen);
  }

  public static void uncache(final Screen view) {
    priorScreens.remove(view.getScreenCacheToken());
  }

  /** @return the currently signed in user's account data; null if no account */
  public static Account getUserAccount() {
    return myAccount;
  }

  /** @return true if the user is currently authenticated */
  public static boolean isSignedIn() {
    return getUserAccount() != null;
  }

  /**
   * Sign the user into the application.
   * 
   * @param callback optional; if sign in is successful the onSuccess method
   *        will be called.
   */
  public static void doSignIn(final AsyncCallback<?> callback) {
    new SignInDialog(callback).center();
  }

  /** Sign the user out of the application (and discard the cookies). */
  public static void doSignOut() {
    myAccount = null;
    Cookies.removeCookie(ACCOUNT_COOKIE);

    for (final SignedInListener l : signedInListeners) {
      l.onSignOut();
    }
    refreshMenuBar();

    if (currentScreen != null) {
      currentScreen.onSignOut();
    }
  }

  /** Add a listener to monitor sign-in status. */
  public static void addSignedInListener(final SignedInListener l) {
    if (!signedInListeners.contains(l)) {
      signedInListeners.add(l);
    }
  }

  /** Remove a previously added sign in listener. */
  public static void removeSignedInListener(final SignedInListener l) {
    signedInListeners.remove(l);
  }

  public void onModuleLoad() {
    initHistoryHooks();
    populateBottomMenu();

    final RootPanel topMenu = RootPanel.get("gerrit_topmenu");
    menuBar = new LinkMenuBar();
    topMenu.add(menuBar);

    body = RootPanel.get("gerrit_body");
    JsonUtil.addRpcStatusListener(new RpcStatus(topMenu));
    SYSTEM_SVC.loadGerritConfig(new GerritCallback<GerritConfig>() {
      public void onSuccess(final GerritConfig result) {
        Common.setGerritConfig(result);
        onModuleLoad2();
      }
    });
  }

  private static ArrayList<JavaScriptObject> historyHooks;

  private static native void initHistoryHooks()
  /*-{ $wnd['gerrit_addHistoryHook'] = function(h) { @com.google.gerrit.client.Gerrit::addHistoryHook(Lcom/google/gwt/core/client/JavaScriptObject;)(h); }; }-*/;

  static void addHistoryHook(final JavaScriptObject hook) {
    if (historyHooks == null) {
      historyHooks = new ArrayList<JavaScriptObject>();
      History.addHistoryListener(new HistoryListener() {
        public void onHistoryChanged(final String historyToken) {
          dispatchHistoryHooks(historyToken);
        }
      });
    }
    historyHooks.add(hook);
  }

  private static native void callHistoryHook(JavaScriptObject hook, String url)
  /*-{ hook(url); }-*/;

  private static void dispatchHistoryHooks(final String historyToken) {
    final String url = Window.Location.getPath() + "#" + historyToken;
    for (final JavaScriptObject hook : historyHooks) {
      callHistoryHook(hook, url);
    }
  }

  private static void populateBottomMenu() {
    final RootPanel btmmenu = RootPanel.get("gerrit_btmmenu");
    final String vs;
    if (GWT.isScript()) {
      final GerritVersion v = GWT.create(GerritVersion.class);
      vs = v.version();
    } else {
      vs = "dev";
    }
    final HTML version = new HTML(M.poweredBy(vs));
    version.setStyleName("gerrit-version");
    btmmenu.add(version);
  }

  private void onModuleLoad2() {
    if (Cookies.getCookie(ACCOUNT_COOKIE) != null
        || Common.getGerritConfig().getLoginType() == SystemConfig.LoginType.HTTP) {
      // If the user is likely to already be signed into their account,
      // load the account data and update the UI with that.
      //
      com.google.gerrit.client.account.Util.ACCOUNT_SVC
          .myAccount(new AsyncCallback<Account>() {
            public void onSuccess(final Account result) {
              if (result != null) {
                postSignIn(result, null);
              } else {
                Cookies.removeCookie(ACCOUNT_COOKIE);
                refreshMenuBar();
              }
              showInitialScreen();
            }

            public void onFailure(final Throwable caught) {
              if (!GWT.isScript() && !GerritCallback.isNotSignedIn(caught)) {
                GWT.log("Unexpected failure from validating account", caught);
              }
              Cookies.removeCookie(ACCOUNT_COOKIE);
              refreshMenuBar();
              showInitialScreen();
            }
          });
    } else {
      refreshMenuBar();
      showInitialScreen();
    }
  }

  private void showInitialScreen() {
    History.addHistoryListener(new Link());
    if ("".equals(History.getToken())) {
      if (isSignedIn()) {
        History.newItem(Link.MINE);
      } else {
        History.newItem(Link.ALL_OPEN);
      }
    } else {
      History.fireCurrentHistoryState();
    }
  }

  /** Hook from {@link SignInDialog} to let us know to refresh the UI. */
  static void postSignIn(final Account acct, final AsyncCallback<?> ac) {
    myAccount = acct;
    refreshMenuBar();

    for (final SignedInListener l : signedInListeners) {
      l.onSignIn();
    }

    if (currentScreen != null) {
      currentScreen.onSignIn();
    }
    if (ac != null) {
      ac.onSuccess(null);
    }
  }

  public static void refreshMenuBar() {
    menuBar.clearItems();

    final boolean signedIn = isSignedIn();
    MenuBar m;

    m = new MenuBar(true);
    addLink(m, C.menuAllOpen(), Link.ALL_OPEN);
    addLink(m, C.menuAllMerged(), Link.ALL_MERGED);
    addLink(m, C.menuAllAbandoned(), Link.ALL_ABANDONED);
    menuBar.addItem(C.menuAll(), m);

    if (signedIn) {
      m = new MenuBar(true);
      addLink(m, C.menuMyChanges(), Link.MINE);
      addLink(m, C.menyMyDrafts(), Link.MINE_DRAFTS);
      addLink(m, C.menuMyStarredChanges(), Link.MINE_STARRED);
      addLink(m, C.menuSettings(), Link.SETTINGS);
      menuBar.addItem(C.menuMine(), m);
    }

    if (signedIn) {
      m = new MenuBar(true);
      addLink(m, C.menuGroups(), Link.ADMIN_GROUPS);
      addLink(m, C.menuProjects(), Link.ADMIN_PROJECTS);
      menuBar.addItem(C.menuAdmin(), m);
    }

    menuBar.lastInGroup();
    menuBar.addGlue();

    if (signedIn) {
      whoAmI();
      menuBar.addItem(new LinkMenuItem(C.menuSettings(), Link.SETTINGS));
      switch (Common.getGerritConfig().getLoginType()) {
        case HTTP:
          break;

        case OPENID:
        default:
          menuBar.addItem(C.menuSignOut(), new Command() {
            public void execute() {
              doSignOut();
            }
          });
          break;
      }
    } else {
      switch (Common.getGerritConfig().getLoginType()) {
        case HTTP:
          break;

        case OPENID:
        default:
          menuBar.addItem(C.menuSignIn(), new Command() {
            public void execute() {
              doSignIn(null);
            }
          });
          break;
      }
    }
    menuBar.lastInGroup();
  }

  private static void whoAmI() {
    final String name = FormatUtil.nameEmail(getUserAccount());
    final MenuItem me = menuBar.addItem(name, (Command) null);
    me.removeStyleName("gwt-MenuItem");
    me.addStyleName("gerrit-MenuBarUserName");
  }

  private static void addLink(final MenuBar m, final String text,
      final String historyToken) {
    m.addItem(new LinkMenuItem(text, historyToken));
  }
}
