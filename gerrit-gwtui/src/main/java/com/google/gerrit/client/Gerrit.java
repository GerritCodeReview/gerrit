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

import static com.google.gerrit.common.data.GlobalCapability.CREATE_GROUP;
import static com.google.gerrit.common.data.GlobalCapability.CREATE_PROJECT;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_PLUGINS;
import static com.google.gerrit.common.data.HostPageData.XSRF_COOKIE_NAME;

import com.google.gerrit.client.account.AccountApi;
import com.google.gerrit.client.account.AccountCapabilities;
import com.google.gerrit.client.account.EditPreferences;
import com.google.gerrit.client.admin.ProjectScreen;
import com.google.gerrit.client.api.ApiGlue;
import com.google.gerrit.client.api.PluginLoader;
import com.google.gerrit.client.change.LocalComments;
import com.google.gerrit.client.changes.ChangeListScreen;
import com.google.gerrit.client.config.ConfigServerApi;
import com.google.gerrit.client.documentation.DocInfo;
import com.google.gerrit.client.info.AccountInfo;
import com.google.gerrit.client.info.AuthInfo;
import com.google.gerrit.client.info.GeneralPreferences;
import com.google.gerrit.client.info.ServerInfo;
import com.google.gerrit.client.info.TopMenu;
import com.google.gerrit.client.info.TopMenuItem;
import com.google.gerrit.client.info.TopMenuList;
import com.google.gerrit.client.rpc.CallbackGroup;
import com.google.gerrit.client.rpc.GerritCallback;
import com.google.gerrit.client.rpc.Natives;
import com.google.gerrit.client.ui.LinkMenuBar;
import com.google.gerrit.client.ui.LinkMenuItem;
import com.google.gerrit.client.ui.MorphingTabPanel;
import com.google.gerrit.client.ui.ProjectLinkMenuItem;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.HostPageData;
import com.google.gerrit.common.data.SystemInfoService;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.EditPreferencesInfo;
import com.google.gerrit.extensions.client.GerritTopMenu;
import com.google.gerrit.extensions.client.UiType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gwt.aria.client.Roles;
import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
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
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;
import com.google.gwt.http.client.URL;
import com.google.gwt.http.client.UrlBuilder;
import com.google.gwt.user.client.Command;
import com.google.gwt.user.client.Cookies;
import com.google.gwt.user.client.History;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.Window.Location;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Anchor;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.FocusPanel;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.HTMLTable.CellFormatter;
import com.google.gwt.user.client.ui.InlineHTML;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwtexpui.clippy.client.CopyableLabel;
import com.google.gwtexpui.user.client.UserAgent;
import com.google.gwtexpui.user.client.ViewSite;
import com.google.gwtjsonrpc.client.JsonDefTarget;
import com.google.gwtjsonrpc.client.JsonUtil;
import com.google.gwtjsonrpc.client.XsrfManager;
import com.google.gwtorm.client.KeyUtil;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Gerrit implements EntryPoint {
  public static final GerritConstants C = GWT.create(GerritConstants.class);
  public static final GerritMessages M = GWT.create(GerritMessages.class);
  public static final GerritResources RESOURCES = GWT.create(GerritResources.class);
  public static final SystemInfoService SYSTEM_SVC;
  public static final EventBus EVENT_BUS = GWT.create(SimpleEventBus.class);
  public static final Themer THEMER = GWT.create(Themer.class);
  public static final String PROJECT_NAME_MENU_VAR = "${projectName}";
  public static final String INDEX = "Documentation/index.html";

  private static String myHost;
  private static ServerInfo myServerInfo;
  private static AccountInfo myAccount;
  private static GeneralPreferences myPrefs;
  private static UrlAliasMatcher urlAliasMatcher;
  private static boolean hasDocumentation;
  private static boolean docSearch;
  private static String docUrl;
  private static HostPageData.Theme myTheme;
  private static String defaultScreenToken;
  private static DiffPreferencesInfo myAccountDiffPref;
  private static EditPreferencesInfo editPrefs;
  private static String xGerritAuth;
  private static boolean isNoteDbEnabled;

  private static Map<String, LinkMenuBar> menuBars;

  private static MorphingTabPanel menuLeft;
  private static LinkMenuBar menuRight;
  private static RootPanel topMenu;
  private static RootPanel siteHeader;
  private static RootPanel siteFooter;
  private static RootPanel bottomMenu;
  private static SearchPanel searchPanel;
  private static final Dispatcher dispatcher = new Dispatcher();
  private static ViewSite<Screen> body;
  private static String lastChangeListToken;
  private static String lastViewToken;
  private static Anchor uiSwitcherLink;

  static {
    SYSTEM_SVC = GWT.create(SystemInfoService.class);
    JsonUtil.bind(SYSTEM_SVC, "rpc/SystemInfoService");
  }

  static void upgradeUI(String token) {
    History.newItem(Dispatcher.RELOAD_UI + token, false);
    Window.Location.reload();
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

  public static String getPriorView() {
    return lastViewToken;
  }

  /**
   * Load the screen at the given location, displaying when ready.
   *
   * <p>If the URL is not already pointing at this location, a new item will be added to the
   * browser's history when the screen is fully loaded and displayed on the UI.
   *
   * @param token location to parse, load, and render.
   */
  public static void display(String token) {
    if (body.getView() == null || !body.getView().displayToken(token)) {
      dispatcher.display(token);
      updateUiLink(token);
    }
  }

  /**
   * Load the screen passed, assuming token can be used to locate it.
   *
   * <p>The screen is loaded in the background. When it is ready to be visible a new item will be
   * added to the browser's history, the screen will be made visible, and the window title may be
   * updated.
   *
   * <p>If {@link Screen#isRequiresSignIn()} is true and the user is not signed in yet the screen
   * instance will be discarded, sign-in will take place, and will redirect to this location upon
   * success.
   *
   * @param token location that refers to {@code view}.
   * @param view the view to load.
   */
  public static void display(String token, Screen view) {
    if (view.isRequiresSignIn() && !isSignedIn()) {
      doSignIn(token);
    } else {
      view.setToken(token);
      if (isSignedIn()) {
        LocalComments.saveInlineComments();
      }
      body.setView(view);
      updateUiLink(token);
    }
  }

  public static void selectMenu(LinkMenuBar bar) {
    menuLeft.selectTab(menuLeft.getWidgetIndex(bar));
  }

  /**
   * Update the current history token after a screen change.
   *
   * <p>The caller has already updated the UI, but wants to publish a different history token for
   * the current browser state. This really only makes sense if the caller is a {@code TabPanel} and
   * is firing an event when the tab changed to a different part.
   *
   * @param token new location that is already visible.
   */
  public static void updateImpl(String token) {
    History.newItem(token, false);
    dispatchHistoryHooks(token);
  }

  public static void setQueryString(String query) {
    searchPanel.setText(query);
  }

  public static void setWindowTitle(Screen screen, String text) {
    if (screen == body.getView()) {
      if (text == null || text.length() == 0) {
        Window.setTitle(M.windowTitle1(myHost));
      } else {
        Window.setTitle(M.windowTitle2(text, myHost));
      }
    }
  }

  public static int getHeaderFooterHeight() {
    int h = bottomMenu.getOffsetHeight();
    if (topMenu.isVisible()) {
      h += topMenu.getOffsetHeight();
    }
    if (siteHeader.isVisible()) {
      h += siteHeader.getOffsetHeight();
    }
    if (siteFooter.isVisible()) {
      h += siteFooter.getOffsetHeight();
    }
    return h;
  }

  public static void setHeaderVisible(boolean visible) {
    topMenu.setVisible(visible);
    siteHeader.setVisible(visible && getUserPreferences().showSiteHeader());
  }

  public static boolean isHeaderVisible() {
    return topMenu.isVisible();
  }

  public static String getDefaultScreenToken() {
    return defaultScreenToken;
  }

  public static RootPanel getBottomMenu() {
    return bottomMenu;
  }

  /** Get the public configuration data used by this Gerrit instance. */
  public static ServerInfo info() {
    return myServerInfo;
  }

  public static UrlAliasMatcher getUrlAliasMatcher() {
    return urlAliasMatcher;
  }

  /** Site theme information (site specific colors)/ */
  public static HostPageData.Theme getTheme() {
    return myTheme;
  }

  /** @return the currently signed in user's account data; empty account data if no account */
  public static AccountInfo getUserAccount() {
    return myAccount;
  }

  /** @return access token to prove user identity during REST API calls. */
  @Nullable
  public static String getXGerritAuth() {
    return xGerritAuth;
  }

  /**
   * @return the preferences of the currently signed in user, the default preferences if not signed
   *     in
   */
  public static GeneralPreferences getUserPreferences() {
    return myPrefs;
  }

  /** @return the currently signed in users's diff preferences, or default values */
  public static DiffPreferencesInfo getDiffPreferences() {
    return myAccountDiffPref;
  }

  public static void setDiffPreferences(DiffPreferencesInfo accountDiffPref) {
    myAccountDiffPref = accountDiffPref;
  }

  /** @return the edit preferences of the current user, null if not signed-in */
  public static EditPreferencesInfo getEditPreferences() {
    return editPrefs;
  }

  public static void setEditPreferences(EditPreferencesInfo p) {
    editPrefs = p;
  }

  /** @return true if the user is currently authenticated */
  public static boolean isSignedIn() {
    return xGerritAuth != null;
  }

  /** Sign the user into the application. */
  public static void doSignIn(String token) {
    Location.assign(loginRedirect(token));
  }

  public static boolean isNoteDbEnabled() {
    return isNoteDbEnabled;
  }

  public static String loginRedirect(String token) {
    if (token == null) {
      token = "";
    } else if (token.startsWith("/")) {
      token = token.substring(1);
    }

    return selfRedirect("login/") + URL.encodePathSegment("#/" + token);
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
    myAccount = AccountInfo.create(0, null, null, null);
    myAccountDiffPref = null;
    editPrefs = null;
    myPrefs = GeneralPreferences.createDefault();
    urlAliasMatcher.clearUserAliases();
    xGerritAuth = null;
    refreshMenuBar();

    // If the cookie was HttpOnly, this request to delete it will
    // most likely not be successful.  We can try anyway though.
    //
    Cookies.removeCookie("GerritAccount");
  }

  private void setXsrfToken() {
    xGerritAuth = Cookies.getCookie(XSRF_COOKIE_NAME);
    JsonUtil.setDefaultXsrfManager(
        new XsrfManager() {
          @Override
          public String getToken(JsonDefTarget proxy) {
            return xGerritAuth;
          }

          @Override
          public void setToken(JsonDefTarget proxy, String token) {
            // Ignore the request, we always rely upon the cookie.
          }
        });
  }

  @Override
  public void onModuleLoad() {
    if (!canLoadInIFrame()) {
      UserAgent.assertNotInIFrame();
    }
    setXsrfToken();

    KeyUtil.setEncoderImpl(
        new KeyUtil.Encoder() {
          @Override
          public String encode(String e) {
            e = URL.encodeQueryString(e);
            e = fixPathImpl(e);
            e = fixColonImpl(e);
            e = fixDoubleQuote(e);
            return e;
          }

          @Override
          public String decode(String e) {
            return URL.decodeQueryString(e);
          }

          private native String fixPathImpl(String path)
              /*-{ return path.replace(/%2F/g, "/"); }-*/ ;

          private native String fixColonImpl(String path)
              /*-{ return path.replace(/%3A/g, ":"); }-*/ ;

          private native String fixDoubleQuote(String path)
              /*-{ return path.replace(/%22/g, '"'); }-*/ ;
        });

    initHostname();
    Window.setTitle(M.windowTitle1(myHost));

    RpcStatus.INSTANCE = new RpcStatus();
    CallbackGroup cbg = new CallbackGroup();
    getDocIndex(
        cbg.add(
            new GerritCallback<DocInfo>() {
              @Override
              public void onSuccess(DocInfo indexInfo) {
                hasDocumentation = indexInfo != null;
                docUrl = selfRedirect("/Documentation/");
              }
            }));
    ConfigServerApi.serverInfo(
        cbg.add(
            new GerritCallback<ServerInfo>() {
              @Override
              public void onSuccess(ServerInfo info) {
                myServerInfo = info;
                urlAliasMatcher = new UrlAliasMatcher(info.urlAliases());
                String du = info.gerrit().docUrl();
                if (du != null && !du.isEmpty()) {
                  hasDocumentation = true;
                  docUrl = du;
                }
                docSearch = info.gerrit().docSearch();
              }
            }));
    HostPageDataService hpd = GWT.create(HostPageDataService.class);
    hpd.load(
        cbg.addFinal(
            new GerritCallback<HostPageData>() {
              @Override
              public void onSuccess(HostPageData result) {
                Document.get().getElementById("gerrit_hostpagedata").removeFromParent();
                myTheme = result.theme;
                isNoteDbEnabled = result.isNoteDbEnabled;
                if (result.accountDiffPref != null) {
                  myAccountDiffPref = result.accountDiffPref;
                }
                if (result.accountDiffPref != null) {
                  // TODO: Support options on the GetDetail REST endpoint so that it can
                  // also return the preferences. Then we can fetch everything with a
                  // single request and we don't need the callback group anymore.
                  CallbackGroup cbg = new CallbackGroup();
                  AccountApi.self()
                      .view("detail")
                      .get(
                          cbg.add(
                              new GerritCallback<AccountInfo>() {
                                @Override
                                public void onSuccess(AccountInfo result) {
                                  myAccount = result;
                                }
                              }));
                  AccountApi.self()
                      .view("preferences")
                      .get(
                          cbg.add(
                              new GerritCallback<GeneralPreferences>() {
                                @Override
                                public void onSuccess(GeneralPreferences prefs) {
                                  myPrefs = prefs;
                                  onModuleLoad2(result);
                                }
                              }));
                  AccountApi.getEditPreferences(
                      cbg.addFinal(
                          new GerritCallback<EditPreferences>() {
                            @Override
                            public void onSuccess(EditPreferences prefs) {
                              EditPreferencesInfo prefsInfo = new EditPreferencesInfo();
                              prefs.copyTo(prefsInfo);
                              editPrefs = prefsInfo;
                            }
                          }));
                } else {
                  myAccount = AccountInfo.create(0, null, null, null);
                  myPrefs = GeneralPreferences.createDefault();
                  editPrefs = null;
                  onModuleLoad2(result);
                }
              }
            }));
  }

  private native boolean canLoadInIFrame() /*-{
    return $wnd.gerrit_hostpagedata.canLoadInIFrame || false;
  }-*/;

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

  private static void dispatchHistoryHooks(String token) {
    ApiGlue.fireEvent("history", token);
  }

  private static String getUiSwitcherUrl(String token) {
    UrlBuilder builder = new UrlBuilder();
    builder.setProtocol(Location.getProtocol());
    builder.setHost(Location.getHost());
    String port = Location.getPort();
    if (port != null && !port.isEmpty()) {
      builder.setPort(Integer.parseInt(port));
    }
    String[] tokens = token.split("@", 2);
    if (Location.getPath().endsWith("/") && tokens[0].startsWith("/")) {
      tokens[0] = tokens[0].substring(1);
    }
    builder.setPath(Location.getPath() + tokens[0]);
    if (tokens.length == 2) {
      builder.setHash(tokens[1]);
    }
    builder.setParameter("polygerrit", "1");
    return builder.buildString();
  }

  private static void populateBottomMenu(RootPanel btmmenu, HostPageData hpd) {
    String vs = hpd.version;
    if (vs == null || vs.isEmpty()) {
      vs = "dev";
    }

    btmmenu.add(new InlineHTML(M.poweredBy(vs)));

    if (info().gerrit().webUis().contains(UiType.POLYGERRIT)) {
      btmmenu.add(new InlineLabel(" | "));
      uiSwitcherLink = new Anchor(C.newUi(), getUiSwitcherUrl(History.getToken()));
      uiSwitcherLink.setStyleName("");
      btmmenu.add(uiSwitcherLink);
    }

    String reportBugUrl = info().gerrit().reportBugUrl();
    if (reportBugUrl != null) {
      String reportBugText = info().gerrit().reportBugText();
      Anchor a = new Anchor(reportBugText == null ? C.reportBug() : reportBugText, reportBugUrl);
      a.setTarget("_blank");
      a.setStyleName("");
      btmmenu.add(new InlineLabel(" | "));
      btmmenu.add(a);
    }
    btmmenu.add(new InlineLabel(" | "));
    btmmenu.add(new InlineLabel(C.keyHelp()));
  }

  private static void updateUiLink(String token) {
    if (uiSwitcherLink != null) {
      uiSwitcherLink.setHref(getUiSwitcherUrl(token));
    }
  }

  private void onModuleLoad2(HostPageData hpd) {
    RESOURCES.gwt_override().ensureInjected();
    RESOURCES.css().ensureInjected();

    topMenu = RootPanel.get("gerrit_topmenu");
    final RootPanel gStarting = RootPanel.get("gerrit_startinggerrit");
    final RootPanel gBody = RootPanel.get("gerrit_body");
    bottomMenu = RootPanel.get("gerrit_btmmenu");

    topMenu.setStyleName(RESOURCES.css().gerritTopMenu());
    gBody.setStyleName(RESOURCES.css().gerritBody());

    final Grid menuLine = new Grid(1, 3);
    menuLeft = new MorphingTabPanel();
    menuRight = new LinkMenuBar();
    searchPanel = new SearchPanel();
    menuLeft.setStyleName(RESOURCES.css().topmenuMenuLeft());
    menuLine.setStyleName(RESOURCES.css().topmenu());
    topMenu.add(menuLine);
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

    body =
        new ViewSite<Screen>() {
          @Override
          protected void onShowView(Screen view) {
            String token = view.getToken();
            History.newItem(token, false);
            dispatchHistoryHooks(token);

            if (view instanceof ChangeListScreen) {
              lastChangeListToken = token;
            }

            super.onShowView(view);
            view.onShowView();
            lastViewToken = token;
          }
        };
    gBody.add(body);

    JsonUtil.addRpcStartHandler(RpcStatus.INSTANCE);
    JsonUtil.addRpcCompleteHandler(RpcStatus.INSTANCE);

    gStarting.getElement().getParentElement().removeChild(gStarting.getElement());
    RootPanel.detachNow(gStarting);
    ApiGlue.init();

    applyUserPreferences();
    populateBottomMenu(bottomMenu, hpd);
    refreshMenuBar();

    History.addValueChangeHandler(
        new ValueChangeHandler<String>() {
          @Override
          public void onValueChange(ValueChangeEvent<String> event) {
            display(event.getValue());
          }
        });
    JumpKeys.register(body);

    saveDefaultTheme();
    if (hpd.messages != null) {
      new MessageOfTheDayBar(hpd.messages).show();
    }
    PluginLoader.load(
        hpd.plugins,
        hpd.pluginsLoadTimeout,
        new GerritCallback<VoidResult>() {
          @Override
          public void onSuccess(VoidResult result) {
            String token = History.getToken();
            if (token.isEmpty()) {
              token = isSignedIn() ? PageLinks.MINE : PageLinks.toChangeQuery("status:open");
            }
            display(token);
          }
        });
  }

  private void saveDefaultTheme() {
    THEMER.init(
        Document.get().getElementById("gerrit_sitecss"),
        Document.get().getElementById("gerrit_header"),
        Document.get().getElementById("gerrit_footer"));
  }

  public static void refreshMenuBar() {
    menuLeft.clear();
    menuRight.clear();

    menuBars = new HashMap<>();

    boolean signedIn = isSignedIn();
    AuthInfo authInfo = info().auth();
    LinkMenuBar m;

    m = new LinkMenuBar();
    menuBars.put(GerritTopMenu.ALL.menuName, m);
    addLink(m, C.menuAllOpen(), PageLinks.toChangeQuery("status:open"));
    addLink(m, C.menuAllMerged(), PageLinks.toChangeQuery("status:merged"));
    addLink(m, C.menuAllAbandoned(), PageLinks.toChangeQuery("status:abandoned"));
    menuLeft.add(m, C.menuAll());

    if (signedIn) {
      LinkMenuBar myBar = new LinkMenuBar();
      menuBars.put(GerritTopMenu.MY.menuName, myBar);

      if (myPrefs.my() != null) {
        myBar.clear();
        String url = null;
        List<TopMenuItem> myMenuItems = Natives.asList(myPrefs.my());
        if (!myMenuItems.isEmpty()) {
          if (myMenuItems.get(0).getUrl().startsWith("#")) {
            url = myMenuItems.get(0).getUrl().substring(1);
          }
          for (TopMenuItem item : myMenuItems) {
            addExtensionLink(myBar, item);
          }
        }
        defaultScreenToken = url;
      }

      menuLeft.add(myBar, C.menuMine());
      menuLeft.selectTab(1);
    } else {
      menuLeft.selectTab(0);
    }

    final LinkMenuBar projectsBar = new LinkMenuBar();
    menuBars.put(GerritTopMenu.PROJECTS.menuName, projectsBar);
    addLink(projectsBar, C.menuProjectsList(), PageLinks.ADMIN_PROJECTS);
    projectsBar.addItem(new ProjectLinkMenuItem(C.menuProjectsInfo(), ProjectScreen.INFO));
    projectsBar.addItem(new ProjectLinkMenuItem(C.menuProjectsBranches(), ProjectScreen.BRANCHES));
    projectsBar.addItem(new ProjectLinkMenuItem(C.menuProjectsTags(), ProjectScreen.TAGS));
    projectsBar.addItem(new ProjectLinkMenuItem(C.menuProjectsAccess(), ProjectScreen.ACCESS));
    final LinkMenuItem dashboardsMenuItem =
        new ProjectLinkMenuItem(C.menuProjectsDashboards(), ProjectScreen.DASHBOARDS) {
          @Override
          protected boolean match(String token) {
            return super.match(token)
                || (!getTargetHistoryToken().isEmpty()
                    && ("/admin" + token).startsWith(getTargetHistoryToken()));
          }
        };
    projectsBar.addItem(dashboardsMenuItem);
    menuLeft.add(projectsBar, C.menuProjects());

    if (signedIn) {
      final LinkMenuBar peopleBar = new LinkMenuBar();
      menuBars.put(GerritTopMenu.PEOPLE.menuName, peopleBar);
      final LinkMenuItem groupsListMenuItem =
          addLink(peopleBar, C.menuPeopleGroupsList(), PageLinks.ADMIN_GROUPS);
      menuLeft.add(peopleBar, C.menuPeople());

      final LinkMenuBar pluginsBar = new LinkMenuBar();
      menuBars.put(GerritTopMenu.PLUGINS.menuName, pluginsBar);
      AccountCapabilities.all(
          new GerritCallback<AccountCapabilities>() {
            @Override
            public void onSuccess(AccountCapabilities result) {
              if (result.canPerform(CREATE_PROJECT)) {
                insertLink(
                    projectsBar,
                    C.menuProjectsCreate(),
                    PageLinks.ADMIN_CREATE_PROJECT,
                    projectsBar.getWidgetIndex(dashboardsMenuItem) + 1);
              }
              if (result.canPerform(CREATE_GROUP)) {
                insertLink(
                    peopleBar,
                    C.menuPeopleGroupsCreate(),
                    PageLinks.ADMIN_CREATE_GROUP,
                    peopleBar.getWidgetIndex(groupsListMenuItem) + 1);
              }
              if (result.canPerform(VIEW_PLUGINS)) {
                insertLink(pluginsBar, C.menuPluginsInstalled(), PageLinks.ADMIN_PLUGINS, 0);
                menuLeft.insert(
                    pluginsBar, C.menuPlugins(), menuLeft.getWidgetIndex(peopleBar) + 1);
              }
            }
          },
          CREATE_PROJECT,
          CREATE_GROUP,
          VIEW_PLUGINS);
    }

    if (hasDocumentation) {
      m = new LinkMenuBar();
      menuBars.put(GerritTopMenu.DOCUMENTATION.menuName, m);
      addDocLink(m, C.menuDocumentationTOC(), "index.html");
      addDocLink(m, C.menuDocumentationSearch(), "user-search.html");
      addDocLink(m, C.menuDocumentationUpload(), "user-upload.html");
      addDocLink(m, C.menuDocumentationAccess(), "access-control.html");
      addDocLink(m, C.menuDocumentationAPI(), "rest-api.html");
      addDocLink(m, C.menuDocumentationProjectOwnerGuide(), "intro-project-owner.html");
      menuLeft.add(m, C.menuDocumentation());
    }

    if (signedIn) {
      whoAmI(!authInfo.isClientSslCertLdap());
    } else {
      switch (authInfo.authType()) {
        case CLIENT_SSL_CERT_LDAP:
          break;

        case OPENID:
          menuRight.addItem(
              C.menuRegister(),
              new Command() {
                @Override
                public void execute() {
                  String t = History.getToken();
                  if (t == null) {
                    t = "";
                  }
                  doSignIn(PageLinks.REGISTER + t);
                }
              });
          menuRight.addItem(
              C.menuSignIn(),
              new Command() {
                @Override
                public void execute() {
                  doSignIn(History.getToken());
                }
              });
          break;

        case OAUTH:
          menuRight.addItem(
              C.menuSignIn(),
              new Command() {
                @Override
                public void execute() {
                  doSignIn(History.getToken());
                }
              });
          break;

        case OPENID_SSO:
          menuRight.addItem(
              C.menuSignIn(),
              new Command() {
                @Override
                public void execute() {
                  doSignIn(History.getToken());
                }
              });
          break;

        case HTTP:
        case HTTP_LDAP:
          if (authInfo.loginUrl() != null) {
            String signinText =
                authInfo.loginText() == null ? C.menuSignIn() : authInfo.loginText();
            menuRight.add(anchor(signinText, authInfo.loginUrl()));
          }
          break;

        case LDAP:
        case LDAP_BIND:
        case CUSTOM_EXTENSION:
          if (authInfo.registerUrl() != null) {
            String registerText =
                authInfo.registerText() == null ? C.menuRegister() : authInfo.registerText();
            menuRight.add(anchor(registerText, authInfo.registerUrl()));
          }
          menuRight.addItem(
              C.menuSignIn(),
              new Command() {
                @Override
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
    ConfigServerApi.topMenus(
        new GerritCallback<TopMenuList>() {
          @Override
          public void onSuccess(TopMenuList result) {
            List<TopMenu> topMenuExtensions = Natives.asList(result);
            for (TopMenu menu : topMenuExtensions) {
              String name = menu.getName();
              LinkMenuBar existingBar = menuBars.get(name);
              LinkMenuBar bar = existingBar != null ? existingBar : new LinkMenuBar();
              for (TopMenuItem item : Natives.asList(menu.getItems())) {
                addMenuLink(bar, item);
              }
              if (existingBar == null) {
                menuBars.put(name, bar);
                menuLeft.add(bar, name);
              }
            }
          }
        });
  }

  public static void refreshUserPreferences() {
    if (isSignedIn()) {
      AccountApi.self()
          .view("preferences")
          .get(
              new GerritCallback<GeneralPreferences>() {
                @Override
                public void onSuccess(GeneralPreferences prefs) {
                  setUserPreferences(prefs);
                }
              });
    } else {
      setUserPreferences(GeneralPreferences.createDefault());
    }
  }

  public static void setUserPreferences(GeneralPreferences prefs) {
    myPrefs = prefs;
    applyUserPreferences();
    refreshMenuBar();
  }

  private static void applyUserPreferences() {
    CopyableLabel.setFlashEnabled(myPrefs.useFlashClipboard());
    if (siteHeader != null) {
      siteHeader.setVisible(myPrefs.showSiteHeader());
    }
    if (siteFooter != null) {
      siteFooter.setVisible(myPrefs.showSiteHeader());
    }
    FormatUtil.setPreferences(myPrefs);
    urlAliasMatcher.updateUserAliases(myPrefs.urlAliases());
  }

  public static boolean hasDocSearch() {
    return docSearch;
  }

  private static void getDocIndex(AsyncCallback<DocInfo> cb) {
    RequestBuilder req = new RequestBuilder(RequestBuilder.HEAD, GWT.getHostPageBaseURL() + INDEX);
    req.setCallback(
        new RequestCallback() {
          @Override
          public void onResponseReceived(Request req, Response resp) {
            switch (resp.getStatusCode()) {
              case Response.SC_OK:
              case Response.SC_MOVED_PERMANENTLY:
              case Response.SC_MOVED_TEMPORARILY:
                cb.onSuccess(DocInfo.create());
                break;
              default:
                cb.onSuccess(null);
                break;
            }
          }

          @Override
          public void onError(Request request, Throwable e) {
            cb.onFailure(e);
          }
        });
    try {
      req.send();
    } catch (RequestException e) {
      cb.onFailure(e);
    }
  }

  private static void whoAmI(boolean canLogOut) {
    AccountInfo account = getUserAccount();
    final UserPopupPanel userPopup = new UserPopupPanel(account, canLogOut, true);
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
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
          showHidePopup();
          event.preventDefault();
        }
      }
    }
    final PopupHandler popupHandler = new PopupHandler();
    final InlineLabel l = new InlineLabel(FormatUtil.name(account));
    l.setStyleName(RESOURCES.css().menuBarUserName());
    final AvatarImage avatar = new AvatarImage(account, 26, false);
    avatar.setStyleName(RESOURCES.css().menuBarUserNameAvatar());
    userSummaryPanel.setStyleName(RESOURCES.css().menuBarUserNamePanel());
    userSummaryPanel.add(l);
    userSummaryPanel.add(avatar);
    // "BLACK DOWN-POINTING SMALL TRIANGLE"
    userSummaryPanel.add(new InlineLabel(" \u25be"));
    userPopup.addAutoHidePartner(userSummaryPanel.getElement());
    FocusPanel fp = new FocusPanel(userSummaryPanel);
    fp.setStyleName(RESOURCES.css().menuBarUserNameFocusPanel());
    fp.addKeyDownHandler(popupHandler);
    fp.addClickHandler(popupHandler);
    menuRight.add(fp);
  }

  private static Anchor anchor(String text, String to) {
    final Anchor a = new Anchor(text, to);
    a.setStyleName(RESOURCES.css().menuItem());
    Roles.getMenuitemRole().set(a.getElement());
    return a;
  }

  private static LinkMenuItem addLink(final LinkMenuBar m, String text, String historyToken) {
    LinkMenuItem i = new LinkMenuItem(text, historyToken);
    m.addItem(i);
    return i;
  }

  private static void insertLink(
      final LinkMenuBar m, String text, String historyToken, int beforeIndex) {
    m.insertItem(new LinkMenuItem(text, historyToken), beforeIndex);
  }

  private static LinkMenuItem addProjectLink(LinkMenuBar m, TopMenuItem item) {
    LinkMenuItem i =
        new ProjectLinkMenuItem(item.getName(), item.getUrl()) {
          @Override
          protected void onScreenLoad(Project.NameKey project) {
            String p = panel.replace(PROJECT_NAME_MENU_VAR, URL.encodeQueryString(project.get()));
            if (!panel.startsWith("/x/") && !isAbsolute(panel)) {
              UrlBuilder builder = new UrlBuilder();
              builder.setProtocol(Location.getProtocol());
              builder.setHost(Location.getHost());
              String port = Location.getPort();
              if (port != null && !port.isEmpty()) {
                builder.setPort(Integer.parseInt(port));
              }
              builder.setPath(Location.getPath());
              p = builder.buildString() + p;
            }
            getElement().setPropertyString("href", p);
          }

          @Override
          public void go() {
            String href = getElement().getPropertyString("href");
            if (href.startsWith("#")) {
              super.go();
            } else {
              Window.open(href, getElement().getPropertyString("target"), "");
            }
          }
        };
    if (item.getTarget() != null && !item.getTarget().isEmpty()) {
      i.getElement().setAttribute("target", item.getTarget());
    }
    if (item.getId() != null) {
      i.getElement().setAttribute("id", item.getId());
    }
    m.addItem(i);
    return i;
  }

  private static void addDocLink(LinkMenuBar m, String text, String href) {
    final Anchor atag = anchor(text, docUrl + href);
    atag.setTarget("_blank");
    m.add(atag);
  }

  private static void addMenuLink(LinkMenuBar m, TopMenuItem item) {
    if (item.getUrl().contains(PROJECT_NAME_MENU_VAR)) {
      addProjectLink(m, item);
    } else {
      addExtensionLink(m, item);
    }
  }

  private static void addExtensionLink(LinkMenuBar m, TopMenuItem item) {
    if (item.getUrl().startsWith("#") && (item.getTarget() == null || item.getTarget().isEmpty())) {
      LinkMenuItem a = new LinkMenuItem(item.getName(), item.getUrl().substring(1));
      if (item.getId() != null) {
        a.getElement().setAttribute("id", item.getId());
      }
      m.addItem(a);
    } else {
      Anchor atag =
          anchor(
              item.getName(),
              isAbsolute(item.getUrl()) ? item.getUrl() : selfRedirect(item.getUrl()));
      if (item.getTarget() != null && !item.getTarget().isEmpty()) {
        atag.setTarget(item.getTarget());
      }
      if (item.getId() != null) {
        atag.getElement().setAttribute("id", item.getId());
      }
      m.add(atag);
    }
  }

  private static boolean isAbsolute(String url) {
    return url.matches("^https?://.*");
  }
}
