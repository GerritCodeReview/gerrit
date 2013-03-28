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

import static com.google.gerrit.common.data.GlobalCapability.ADMINISTRATE_SERVER;
import static com.google.gerrit.common.data.GlobalCapability.CREATE_GROUP;
import static com.google.gerrit.common.data.GlobalCapability.CREATE_PROJECT;

import com.google.gerrit.client.account.AccountCapabilities;
import com.google.gerrit.client.admin.ProjectScreen;
import com.google.gerrit.client.changes.ChangeConstants;
import com.google.gerrit.client.changes.ChangeListScreen;
import com.google.gerrit.client.patches.PatchScreen;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.ui.LinkMenuBar;
import com.google.gerrit.client.ui.LinkMenuItem;
import com.google.gerrit.client.ui.MorphingTabPanel;
import com.google.gerrit.client.ui.PatchLink;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.client.ui.ScreenLoadEvent;
import com.google.gerrit.common.ClientVersion;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.common.data.GitwebConfig;
import com.google.gerrit.common.data.HostPageData;
import com.google.gerrit.common.data.SystemInfoService;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountDiffPreference;
import com.google.gerrit.reviewdb.client.AccountGeneralPreferences;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.aria.client.Roles;
import com.google.gwt.core.client.Callback;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.ScriptInjector;
import com.google.gwt.dom.client.AnchorElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.http.client.URL;
import com.google.gwt.http.client.UrlBuilder;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.Location;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import com.google.gwtexpui.user.client.UserAgent;
import com.google.gwtexpui.user.client.ViewSite;
import com.google.gwtjsonrpc.client.CallbackHandle;
import com.google.gwtjsonrpc.client.JsonDefTarget;
import com.google.gwtjsonrpc.client.JsonUtil;
import com.google.gwtjsonrpc.client.XsrfManager;
import com.google.gwtjsonrpc.client.impl.ResultDeserializer;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtorm.client.KeyUtil;

import java.util.ArrayList;

public class Gerrit implements EntryPoint {
  public static final GerritConstants C = GWT.create(GerritConstants.class);
  public static final ChangeConstants CC = GWT.create(ChangeConstants.class);
  public static final GerritMessages M = GWT.create(GerritMessages.class);
  public static final GerritResources RESOURCES =
      GWT.create(GerritResources.class);
  public static final SystemInfoService SYSTEM_SVC;
  public static final EventBus EVENT_BUS = GWT.create(SimpleEventBus.class);

  private static String myHost;
  private static GerritConfig myConfig;
  private static HostPageData.Theme myTheme;
  private static Account myAccount;
  private static AccountDiffPreference myAccountDiffPref;
  private static String xGerritAuth;

  private static MorphingTabPanel menuLeft;
  private static LinkMenuBar menuRight;
  private static LinkMenuBar diffBar;
  private static LinkMenuBar projectsBar;
  private static RootPanel siteHeader;
  private static RootPanel siteFooter;
  private static SearchPanel searchPanel;
  private static final Dispatcher dispatcher = new Dispatcher();
  private static ViewSite<Screen> body;
  private static PatchScreen patchScreen;
  private static String lastChangeListToken;

  static {
    SYSTEM_SVC = GWT.create(SystemInfoService.class);
    JsonUtil.bind(SYSTEM_SVC, "rpc/SystemInfoService");
  }

  static void upgradeUI(String token) {
    History.newItem(Dispatcher.RELOAD_UI + token, false);
    Window.Location.reload();
  }

  public static PatchScreen.TopView getPatchScreenTopView() {
    if (patchScreen == null) {
      return null;
    }
    return patchScreen.getTopView();
  }

  public static void displayLastChangeList() {
    if (lastChangeListToken != null) {
      display(lastChangeListToken);
    } else if (isSignedIn()) {
      display(PageLinks.MINE);
    } else {
      display(PageLinks.toChangeQuery("status:open"));
    }
  }

  /**
   * Load the screen at the given location, displaying when ready.
   * <p>
   * If the URL is not already pointing at this location, a new item will be
   * added to the browser's history when the screen is fully loaded and
   * displayed on the UI.
   *
   * @param token location to parse, load, and render.
   */
  public static void display(final String token) {
    if (body.getView() == null || !body.getView().displayToken(token)) {
      dispatcher.display(token);
    }
  }

  /**
   * Load the screen passed, assuming token can be used to locate it.
   * <p>
   * The screen is loaded in the background. When it is ready to be visible a
   * new item will be added to the browser's history, the screen will be made
   * visible, and the window title may be updated.
   * <p>
   * If {@link Screen#isRequiresSignIn()} is true and the user is not signed in
   * yet the screen instance will be discarded, sign-in will take place, and
   * will redirect to this location upon success.
   *
   * @param token location that refers to {@code view}.
   * @param view the view to load.
   */
  public static void display(final String token, final Screen view) {
    if (view.isRequiresSignIn() && !isSignedIn()) {
      doSignIn(token);
    } else {
      view.setToken(token);
      body.setView(view);
    }
  }

  /**
   * Update any top level menus which can vary based on the view which was
   * loaded.
   * @param view the loaded view.
   */
  public static void updateMenus(Screen view) {
    if (view instanceof PatchScreen) {
      patchScreen = (PatchScreen) view;
      menuLeft.setVisible(diffBar, true);
      menuLeft.selectTab(menuLeft.getWidgetIndex(diffBar));
    } else {
      if (patchScreen != null && menuLeft.getSelectedWidget() == diffBar) {
        menuLeft.selectTab(isSignedIn() ? 1 : 0);
      }
      patchScreen = null;
      menuLeft.setVisible(diffBar, false);
    }
  }

  /**
   * Update the current history token after a screen change.
   * <p>
   * The caller has already updated the UI, but wants to publish a different
   * history token for the current browser state. This really only makes sense
   * if the caller is a {@code TabPanel} and is firing an event when the tab
   * changed to a different part.
   *
   * @param token new location that is already visible.
   */
  public static void updateImpl(final String token) {
    History.newItem(token, false);
    dispatchHistoryHooks(token);
  }

  public static void setQueryString(String query) {
    searchPanel.setText(query);
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

  /** Get the public configuration data used by this Gerrit instance. */
  public static GerritConfig getConfig() {
    return myConfig;
  }

  public static GitwebLink getGitwebLink() {
    GitwebConfig gw = getConfig().getGitwebLink();
    return gw != null ? new GitwebLink(gw) : null;
  }

  /** Site theme information (site specific colors)/ */
  public static HostPageData.Theme getTheme() {
    return myTheme;
  }

  /** @return the currently signed in user's account data; null if no account */
  public static Account getUserAccount() {
    return myAccount;
  }

  /** @return access token to prove user identity during REST API calls. */
  public static String getXGerritAuth() {
    return xGerritAuth;
  }

  /** @return the currently signed in users's diff preferences; null if no diff preferences defined for the account */
  public static AccountDiffPreference getAccountDiffPreference() {
    return myAccountDiffPref;
  }

  public static void setAccountDiffPreference(AccountDiffPreference accountDiffPref) {
    myAccountDiffPref = accountDiffPref;
  }

  /** @return true if the user is currently authenticated */
  public static boolean isSignedIn() {
    return getUserAccount() != null;
  }

  /** Sign the user into the application. */
  public static void doSignIn(String token) {
    Location.assign(loginRedirect(token));
  }

  public static String loginRedirect(String token) {
    if (token == null) {
      token = "";
    } else if (token.startsWith("/")) {
      token = token.substring(1);
    }
    return selfRedirect("/login/" + token);
  }

  public static String selfRedirect(String suffix) {
    // Clean up the path. Users seem to like putting extra slashes into the URL
    // which can break redirections by misinterpreting at either client or server.
    String path = Location.getPath();
    if (path == null || path.isEmpty()) {
      path = "/";
    } else {
      while (path.startsWith("//")) {
        path = path.substring(1);
      }
      while (path.endsWith("//")) {
        path = path.substring(0, path.length() - 1);
      }
      if (!path.endsWith("/")) {
        path = path + "/";
      }
    }

    if (suffix != null) {
      while (suffix.startsWith("/")) {
        suffix = suffix.substring(1);
      }
      path += suffix;
    }

    UrlBuilder builder = new UrlBuilder();
    builder.setProtocol(Location.getProtocol());
    builder.setHost(Location.getHost());
    String port = Location.getPort();
    if (port != null && !port.isEmpty()) {
      builder.setPort(Integer.parseInt(port));
    }
    builder.setPath(path);
    return builder.buildString();
  }

  static void deleteSessionCookie() {
    myAccount = null;
    myAccountDiffPref = null;
    xGerritAuth = null;
    refreshMenuBar();

    // If the cookie was HttpOnly, this request to delete it will
    // most likely not be successful.  We can try anyway though.
    //
    Cookies.removeCookie("GerritAccount");
  }

  @Override
  public void onModuleLoad() {
    UserAgent.assertNotInIFrame();

    KeyUtil.setEncoderImpl(new KeyUtil.Encoder() {
      @Override
      public String encode(String e) {
        e = URL.encodeQueryString(e);
        e = fixPathImpl(e);
        e = fixColonImpl(e);
        e = fixDoubleQuote(e);
        return e;
      }

      @Override
      public String decode(final String e) {
        return URL.decodeQueryString(e);
      }

      private native String fixPathImpl(String path)
      /*-{ return path.replace(/%2F/g, "/"); }-*/;

      private native String fixColonImpl(String path)
      /*-{ return path.replace(/%3A/g, ":"); }-*/;

      private native String fixDoubleQuote(String path)
      /*-{ return path.replace(/%22/g, '"'); }-*/;
    });

    initHostname();
    Window.setTitle(M.windowTitle1(myHost));

    final HostPageDataService hpd = GWT.create(HostPageDataService.class);
    hpd.load(new GerritCallback<HostPageData>() {
      @Override
      public void onSuccess(final HostPageData result) {
        Document.get().getElementById("gerrit_hostpagedata").removeFromParent();
        myConfig = result.config;
        myTheme = result.theme;
        if (result.account != null) {
          myAccount = result.account;
          xGerritAuth = result.xGerritAuth;
        }
        if (result.accountDiffPref != null) {
          myAccountDiffPref = result.accountDiffPref;
          applyUserPreferences();
        }
        onModuleLoad2(result);
      }
    });
  }

  private static void initHostname() {
    myHost = Location.getHostName();
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
  private static Anchor signInAnchor;

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
    if (signInAnchor != null) {
      signInAnchor.setHref(loginRedirect(historyToken));
    }
    if (historyHooks != null) {
      final String url = Location.getPath() + "#" + historyToken;
      for (final JavaScriptObject hook : historyHooks) {
        callHistoryHook(hook, url);
      }
    }
  }

  private static void populateBottomMenu(final RootPanel btmmenu) {
    final Label keyHelp = new Label(C.keyHelp());
    keyHelp.setStyleName(RESOURCES.css().keyhelp());
    btmmenu.add(keyHelp);

    String vs;
    if (GWT.isScript()) {
      final ClientVersion v = GWT.create(ClientVersion.class);
      vs = v.version().getText();
      if (vs.startsWith("v")) {
        vs = vs.substring(1);
      }
    } else {
      vs = "dev";
    }

    FlowPanel poweredBy = new FlowPanel();
    poweredBy.setStyleName(RESOURCES.css().version());
    poweredBy.add(new InlineHTML(M.poweredBy(vs)));
    if (getConfig().getReportBugUrl() != null) {
      poweredBy.add(new InlineLabel(" | "));
      Anchor a = new Anchor(
          C.reportBug(),
          getConfig().getReportBugUrl());
      a.setTarget("_blank");
      a.setStyleName("");
      poweredBy.add(a);
    }
    btmmenu.add(poweredBy);
  }

  private void onModuleLoad2(HostPageData hpd) {
    RESOURCES.gwt_override().ensureInjected();
    RESOURCES.css().ensureInjected();

    final RootPanel gTopMenu = RootPanel.get("gerrit_topmenu");
    final RootPanel gStarting = RootPanel.get("gerrit_startinggerrit");
    final RootPanel gBody = RootPanel.get("gerrit_body");
    final RootPanel gBottomMenu = RootPanel.get("gerrit_btmmenu");

    gTopMenu.setStyleName(RESOURCES.css().gerritTopMenu());
    gBody.setStyleName(RESOURCES.css().gerritBody());

    final Grid menuLine = new Grid(1, 3);
    menuLeft = new MorphingTabPanel();
    menuRight = new LinkMenuBar();
    searchPanel = new SearchPanel();
    menuLeft.setStyleName(RESOURCES.css().topmenuMenuLeft());
    menuLine.setStyleName(RESOURCES.css().topmenu());
    gTopMenu.add(menuLine);
    final FlowPanel menuRightPanel = new FlowPanel();
    menuRightPanel.setStyleName(RESOURCES.css().topmenuMenuRight());
    menuRightPanel.add(searchPanel);
    menuRightPanel.add(menuRight);
    menuLine.setWidget(0, 0, menuLeft);
    menuLine.setWidget(0, 1, new FlowPanel());
    menuLine.setWidget(0, 2, menuRightPanel);
    final CellFormatter fmt = menuLine.getCellFormatter();
    fmt.setStyleName(0, 0, RESOURCES.css().topmenuTDmenu());
    fmt.setStyleName(0, 1, RESOURCES.css().topmenuTDglue());
    fmt.setStyleName(0, 2, RESOURCES.css().topmenuTDmenu());

    siteHeader = RootPanel.get("gerrit_header");
    siteFooter = RootPanel.get("gerrit_footer");

    body = new ViewSite<Screen>() {
      @Override
      protected void onShowView(Screen view) {
        final String token = view.getToken();
        if (!token.equals(History.getToken())) {
          History.newItem(token, false);
          dispatchHistoryHooks(token);
        }

        if (view instanceof ChangeListScreen) {
          lastChangeListToken = token;
        }

        super.onShowView(view);
        view.onShowView();
      }
    };
    gBody.add(body);

    RpcStatus.INSTANCE = new RpcStatus(gTopMenu);
    JsonUtil.addRpcStartHandler(RpcStatus.INSTANCE);
    JsonUtil.addRpcCompleteHandler(RpcStatus.INSTANCE);
    JsonUtil.setDefaultXsrfManager(new XsrfManager() {
      @Override
      public String getToken(JsonDefTarget proxy) {
        return xGerritAuth;
      }

      @Override
      public void setToken(JsonDefTarget proxy, String token) {
        // Ignore the request, we always rely upon the cookie.
      }
    });

    gStarting.getElement().getParentElement().removeChild(
        gStarting.getElement());
    RootPanel.detachNow(gStarting);

    applyUserPreferences();
    initHistoryHooks();
    populateBottomMenu(gBottomMenu);
    refreshMenuBar();

    History.addValueChangeHandler(new ValueChangeHandler<String>() {
      @Override
      public void onValueChange(final ValueChangeEvent<String> event) {
        display(event.getValue());
      }
    });
    JumpKeys.register(body);

    String token = History.getToken();
    if (token.isEmpty()) {
      token = isSignedIn()
          ? PageLinks.MINE
          : PageLinks.toChangeQuery("status:open");
    }
    if (signInAnchor != null) {
      signInAnchor.setHref(loginRedirect(token));
    }
    loadPlugins(hpd, token);
  }

  private void loadPlugins(HostPageData hpd, final String token) {
    if (hpd.plugins != null) {
      for (final String url : hpd.plugins) {
        ScriptInjector.fromUrl(url)
            .setWindow(ScriptInjector.TOP_WINDOW)
            .setCallback(new Callback<Void, Exception>() {
              @Override
              public void onSuccess(Void result) {
              }

              @Override
              public void onFailure(Exception reason) {
                ErrorDialog d = new ErrorDialog(reason);
                d.setTitle(M.pluginFailed(url));
                d.center();
              }
            }).inject();
      }
    }

    CallbackHandle<Void> cb = new CallbackHandle<Void>(
        new ResultDeserializer<Void>() {
          @Override
          public Void fromResult(JavaScriptObject responseObject) {
            return null;
          }
        },
        new AsyncCallback<Void>() {
          @Override
          public void onFailure(Throwable caught) {
          }

          @Override
          public void onSuccess(Void result) {
            display(token);
          }
        });
    cb.install();
    ScriptInjector.fromString(cb.getFunctionName() + "();")
        .setWindow(ScriptInjector.TOP_WINDOW)
        .inject();
  }

  public static void refreshMenuBar() {
    menuLeft.clear();
    menuRight.clear();

    final boolean signedIn = isSignedIn();
    final GerritConfig cfg = getConfig();
    LinkMenuBar m;

    m = new LinkMenuBar();
    addLink(m, C.menuAllOpen(), PageLinks.toChangeQuery("status:open"));
    addLink(m, C.menuAllMerged(), PageLinks.toChangeQuery("status:merged"));
    addLink(m, C.menuAllAbandoned(), PageLinks.toChangeQuery("status:abandoned"));
    menuLeft.add(m, C.menuAll());

    if (signedIn) {
      m = new LinkMenuBar();
      addLink(m, C.menuMyChanges(), PageLinks.MINE);
      addLink(m, C.menuMyDrafts(), PageLinks.toChangeQuery("is:draft"));
      addLink(m, C.menuMyDraftComments(), PageLinks.toChangeQuery("has:draft"));
      addLink(m, C.menuMyWatchedChanges(), PageLinks.toChangeQuery("is:watched status:open"));
      addLink(m, C.menuMyStarredChanges(), PageLinks.toChangeQuery("is:starred"));
      menuLeft.add(m, C.menuMine());
      menuLeft.selectTab(1);
    } else {
      menuLeft.selectTab(0);
    }

    patchScreen = null;
    diffBar = new LinkMenuBar();
    menuLeft.addInvisible(diffBar, C.menuDiff());
    addDiffLink(diffBar, CC.patchTableDiffSideBySide(), PatchScreen.Type.SIDE_BY_SIDE);
    addDiffLink(diffBar, CC.patchTableDiffUnified(), PatchScreen.Type.UNIFIED);
    addDiffLink(diffBar, C.menuDiffCommit(), PatchScreen.TopView.COMMIT);
    addDiffLink(diffBar, C.menuDiffPreferences(), PatchScreen.TopView.PREFERENCES);
    addDiffLink(diffBar, C.menuDiffPatchSets(), PatchScreen.TopView.PATCH_SETS);
    addDiffLink(diffBar, C.menuDiffFiles(), PatchScreen.TopView.FILES);

    projectsBar = new LinkMenuBar() {
      @Override
      public void onScreenLoad(ScreenLoadEvent event) {
        if (event.getScreen() instanceof ProjectScreen) {
          menuLeft.selectTab(menuLeft.getWidgetIndex(this));
        }
      }
    };
    addLink(projectsBar, C.menuProjectsList(), PageLinks.ADMIN_PROJECTS);
    addProjectLink(projectsBar, C.menuProjectsInfo(), ProjectScreen.INFO);
    addProjectLink(projectsBar, C.menuProjectsBranches(), ProjectScreen.BRANCH);
    addProjectLink(projectsBar, C.menuProjectsAccess(), ProjectScreen.ACCESS);
    addProjectLink(projectsBar, C.menuProjectsDashboards(), ProjectScreen.DASHBOARDS);
    menuLeft.add(projectsBar, C.menuProjects());

    if (signedIn) {
      final LinkMenuBar peopleBar = new LinkMenuBar();
      addLink(peopleBar, C.menuPeopleGroupsList(), PageLinks.ADMIN_GROUPS);
      menuLeft.add(peopleBar, C.menuPeople());

      final LinkMenuBar pluginsBar = new LinkMenuBar();
      AccountCapabilities.all(new GerritCallback<AccountCapabilities>() {
        @Override
        public void onSuccess(AccountCapabilities result) {
          if (result.canPerform(CREATE_PROJECT)) {
            addLink(projectsBar, C.menuProjectsCreate(), PageLinks.ADMIN_CREATE_PROJECT);
          }
          if (result.canPerform(CREATE_GROUP)) {
            addLink(peopleBar, C.menuPeopleGroupsCreate(), PageLinks.ADMIN_CREATE_GROUP);
          }
          if (result.canPerform(ADMINISTRATE_SERVER)) {
            addLink(pluginsBar, C.menuPluginsInstalled(), PageLinks.ADMIN_PLUGINS);
            menuLeft.insert(pluginsBar, C.menuPlugins(),
                menuLeft.getWidgetIndex(peopleBar) + 1);
          }
        }
      }, CREATE_PROJECT, CREATE_GROUP, ADMINISTRATE_SERVER);
    }

    if (getConfig().isDocumentationAvailable()) {
      m = new LinkMenuBar();
      addDocLink(m, C.menuDocumentationIndex(), "index.html");
      addDocLink(m, C.menuDocumentationSearch(), "user-search.html");
      addDocLink(m, C.menuDocumentationUpload(), "user-upload.html");
      addDocLink(m, C.menuDocumentationAccess(), "access-control.html");
      menuLeft.add(m, C.menuDocumentation());
    }

    if (signedIn) {
      whoAmI(cfg.getAuthType() != AuthType.CLIENT_SSL_CERT_LDAP);
    } else {
      switch (cfg.getAuthType()) {
        case HTTP:
        case HTTP_LDAP:
        case CLIENT_SSL_CERT_LDAP:
          break;

        case OPENID:
          menuRight.addItem(C.menuRegister(), new Command() {
            public void execute() {
              String t = History.getToken();
              if (t == null) {
                t = "";
              }
              doSignIn(PageLinks.REGISTER + t);
            }
          });
          menuRight.addItem(C.menuSignIn(), new Command() {
            public void execute() {
              doSignIn(History.getToken());
            }
          });
          break;

        case OPENID_SSO:
          menuRight.addItem(C.menuSignIn(), new Command() {
            public void execute() {
              doSignIn(History.getToken());
            }
          });
          break;

        case LDAP:
        case LDAP_BIND:
        case CUSTOM_EXTENSION:
          if (cfg.getRegisterUrl() != null) {
            final String registerText = cfg.getRegisterText() == null ? C.menuRegister() : cfg.getRegisterText();
            menuRight.add(anchor(registerText, cfg.getRegisterUrl()));
          }
          menuRight.addItem(C.menuSignIn(), new Command() {
            public void execute() {
              doSignIn(History.getToken());
            }
          });
          break;

        case DEVELOPMENT_BECOME_ANY_ACCOUNT:
          menuRight.add(anchor("Become", loginRedirect("")));
          break;
      }
    }
  }

  public static void applyUserPreferences() {
    if (myAccount != null) {
      final AccountGeneralPreferences p = myAccount.getGeneralPreferences();
      CopyableLabel.setFlashEnabled(p.isUseFlashClipboard());
      if (siteHeader != null) {
        siteHeader.setVisible(p.isShowSiteHeader());
      }
      if (siteFooter != null) {
        siteFooter.setVisible(p.isShowSiteHeader());
      }
      FormatUtil.setPreferences(myAccount.getGeneralPreferences());
    }
  }

  private static void whoAmI(boolean canLogOut) {
    Account account = getUserAccount();
    final CurrentUserPopupPanel userPopup =
        new CurrentUserPopupPanel(account, canLogOut);
    final FlowPanel userSummaryPanel = new FlowPanel();
    class PopupHandler implements KeyDownHandler, ClickHandler {
      private void showHidePopup() {
        if (userPopup.isShowing() && userPopup.isVisible()) {
          userPopup.hide();
        } else {
          userPopup.showRelativeTo(userSummaryPanel);
        }
      }

      @Override
      public void onClick(ClickEvent event) {
        showHidePopup();
      }

      @Override
      public void onKeyDown(KeyDownEvent event) {
        if(event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
          showHidePopup();
          event.preventDefault();
        }
      }
    }
    final PopupHandler popupHandler = new PopupHandler();
    final InlineLabel l = new InlineLabel(FormatUtil.name(account));
    l.setStyleName(RESOURCES.css().menuBarUserName());
    final AvatarImage avatar = new AvatarImage(account.getId(), 26);
    avatar.setStyleName(RESOURCES.css().menuBarUserNameAvatar());
    userSummaryPanel.setStyleName(RESOURCES.css().menuBarUserNamePanel());
    userSummaryPanel.add(l);
    userSummaryPanel.add(avatar);
    userSummaryPanel.add(new InlineLabel(" ▾"));
    userPopup.addAutoHidePartner(userSummaryPanel.getElement());
    FocusPanel fp = new FocusPanel(userSummaryPanel);
    fp.setStyleName(RESOURCES.css().menuBarUserNameFocusPanel());
    fp.addKeyDownHandler(popupHandler);
    fp.addClickHandler(popupHandler);
    menuRight.add(fp);
  }

  private static Anchor anchor(final String text, final String to) {
    final Anchor a = new Anchor(text, to);
    a.setStyleName(RESOURCES.css().menuItem());
    Roles.getMenuitemRole().set(a.getElement());
    return a;
  }

  private static void addLink(final LinkMenuBar m, final String text,
      final String historyToken) {
    m.addItem(new LinkMenuItem(text, historyToken));
  }

  private static void addDiffLink(final LinkMenuBar m, final String text,
      final PatchScreen.TopView tv) {
    m.addItem(new LinkMenuItem(text, "") {
        @Override
        public void go() {
          if (patchScreen != null) {
            patchScreen.setTopView(tv);
          }
          AnchorElement.as(getElement()).blur();
        }
      });
  }

  private static void addProjectLink(final LinkMenuBar m, final String text,
      final String panel) {
    m.addItem(new LinkMenuItem(text, "") {
        @Override
        public void onScreenLoad(ScreenLoadEvent event) {
          Screen screen = event.getScreen();
          Project.NameKey projectKey;
          if (screen instanceof ProjectScreen) {
            projectKey = ((ProjectScreen)screen).getProjectKey();
          } else {
            projectKey = ProjectScreen.getSavedKey();
          }

          if (projectKey != null) {
            setVisible(true);
            setTargetHistoryToken(Dispatcher.toProjectAdmin(projectKey, panel));
          } else {
            setVisible(false);
          }
          super.onScreenLoad(event);
        }
      });
  }

  private static void addDiffLink(final LinkMenuBar m, final String text,
      final PatchScreen.Type type) {
    m.addItem(new LinkMenuItem(text, "") {
        @Override
        public void go() {
          if (patchScreen != null) {
            patchScreen.setTopView(PatchScreen.TopView.MAIN);
            if (type == patchScreen.getPatchScreenType()) {
              AnchorElement.as(getElement()).blur();
            } else {
              new PatchLink("", type, patchScreen).go();
            }
          }
        }
      });
  }

  private static void addDocLink(final LinkMenuBar m, final String text,
      final String href) {
    final Anchor atag = anchor(text, selfRedirect("/Documentation/" + href));
    atag.setTarget("_blank");
    m.add(atag);
  }
}
