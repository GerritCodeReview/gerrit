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
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.MenuBar;
import com.google.gwt.user.client.ui.MenuItem;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwtexpui.user.client.UserAgent;
import com.google.gwtexpui.user.client.ViewSite;
import com.google.gwtjsonrpc.client.JsonUtil;

import java.util.ArrayList;

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

  private static String myHost;
  private static String myVersion;
  private static Account myAccount;
  private static final ArrayList<SignedInListener> signedInListeners =
      new ArrayList<SignedInListener>();

  private static LinkMenuBar menuBar;
  private static RootPanel siteHeader;
  private static RootPanel siteFooter;
  private static ViewSite<Screen> body;

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
      doSignIn();
    } else {
      body.setView(view);
    }
  }

  public static void setWindowTitle(final Screen screen, final String text) {
    if (screen == body.getView()) {
      if (text == null || text.length() == 0) {
        Window.setTitle(M.windowTitle1(myHost));
      } else {
        Window.setTitle(M.windowTitle2(text, myHost));
      }
    }
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
   */
  public static void doSignIn() {
    new SignInDialog().center();
  }

  /** Sign the user out of the application (and discard the cookies). */
  public static void doSignOut() {
    myAccount = null;
    Cookies.removeCookie(ACCOUNT_COOKIE);

    for (final SignedInListener l : signedInListeners) {
      l.onSignOut();
    }
    refreshMenuBar();

    final Screen cs = body.getView();
    if (cs != null) {
      cs.onSignOut();
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
    UserAgent.assertNotInIFrame();
    initHostname();
    Window.setTitle(M.windowTitle1(myHost));
    initHistoryHooks();
    populateBottomMenu();

    final RootPanel topMenu = RootPanel.get("gerrit_topmenu");
    menuBar = new LinkMenuBar();
    topMenu.add(menuBar);

    siteHeader = RootPanel.get("gerrit_header");
    siteFooter = RootPanel.get("gerrit_footer");

    body = new ViewSite<Screen>() {
      @Override
      protected void onShowView(Screen view) {
        super.onShowView(view);
        view.onShowView();
      }
    };
    RootPanel.get("gerrit_body").add(body);

    JsonUtil.addRpcStatusListener(new RpcStatus(topMenu));
    SYSTEM_SVC.loadGerritConfig(new GerritCallback<GerritConfig>() {
      public void onSuccess(final GerritConfig result) {
        Common.setGerritConfig(result);
        onModuleLoad2();
      }
    });
  }

  private static void initHostname() {
    myHost = Window.Location.getHostName();
    final int d1 = myHost.indexOf('.');
    if (d1 < 0) {
      return;
    }
    final int d2 = myHost.indexOf('.', d1 + 1);
    if (d2 >= 0) {
      myHost = myHost.substring(0, d2);
    }
  }

  private static ArrayList<JavaScriptObject> historyHooks;

  private static native void initHistoryHooks()
  /*-{ $wnd['gerrit_addHistoryHook'] = function(h) { @com.google.gerrit.client.Gerrit::addHistoryHook(Lcom/google/gwt/core/client/JavaScriptObject;)(h); }; }-*/;

  static void addHistoryHook(final JavaScriptObject hook) {
    if (historyHooks == null) {
      historyHooks = new ArrayList<JavaScriptObject>();
      History.addValueChangeHandler(new ValueChangeHandler<String>() {
        @Override
        public void onValueChange(ValueChangeEvent<String> event) {
          dispatchHistoryHooks(event.getValue());
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
    final String vs = getVersion();
    final HTML version = new HTML(M.poweredBy(vs));
    version.setStyleName("gerrit-version");
    btmmenu.add(version);
  }

  /** @return version number of the Gerrit application software */
  public static String getVersion() {
    if (myVersion == null) {
      if (GWT.isScript()) {
        final GerritVersion v = GWT.create(GerritVersion.class);
        myVersion = v.version();
      } else {
        myVersion = "dev";
      }
    }
    return myVersion;
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
    final RootPanel sg = RootPanel.get("gerrit_startinggerrit");
    sg.getElement().getParentElement().removeChild(sg.getElement());
    RootPanel.detachNow(sg);

    History.addValueChangeHandler(new Link());
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

    final Screen cs = body.getView();
    if (cs != null) {
      cs.onSignIn();
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
      boolean signout = false;
      switch (Common.getGerritConfig().getLoginType()) {
        case HTTP:
          break;

        case OPENID:
        default:
          signout = true;
          break;
      }
      if (signout || (GWT.isClient() && !GWT.isScript())) {
        menuBar.addItem(C.menuSignOut(), new Command() {
          public void execute() {
            doSignOut();
          }
        });
      }
    } else {
      switch (Common.getGerritConfig().getLoginType()) {
        case HTTP:
          break;

        case OPENID:
        default:
          menuBar.addItem(C.menuSignIn(), new Command() {
            public void execute() {
              doSignIn();
            }
          });
          break;
      }
      if (GWT.isClient() && !GWT.isScript()) {
        menuBar.addItem("Become", new Command() {
          public void execute() {
            Window.Location.assign(GWT.getHostPageBaseURL() + "become");
          }
        });
      }
    }
    menuBar.lastInGroup();

    final boolean view = myAccount == null || myAccount.isShowSiteHeader();
    if (siteHeader != null) {
      siteHeader.setVisible(view);
    }
    if (siteFooter != null) {
      siteFooter.setVisible(view);
    }
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
