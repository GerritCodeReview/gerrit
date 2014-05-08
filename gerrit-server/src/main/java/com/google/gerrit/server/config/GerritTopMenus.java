// Copyright (C) 2014 The Android Open Source Project
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
package com.google.gerrit.server.config;

import static com.google.gerrit.common.PageLinks.ADMIN_PROJECTS;

import com.google.common.collect.Lists;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.webui.GerritTopMenu;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.extensions.webui.TopMenu.MenuEntry;
import com.google.gerrit.extensions.webui.TopMenu.MenuItem;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.account.GetPreferences;
import com.google.gerrit.server.account.GetPreferences.PreferenceInfo;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.servlet.ServletContext;

@Singleton
class GerritTopMenus {
  public static final String PROJECT_INFO = "info";
  public static final String PROJECT_BRANCH = "branches";
  public static final String PROJECT_ACCESS = "access";
  public static final String PROJECT_DASHBOARDS = "dashboards";

  private final Provider<CurrentUser> currentUserProvider;
  private final GerritConstantsProperties constProps;
  private final GetPreferences getPreferences;
  private final Provider<GerritConfig> configProvider;

  @Inject
  public GerritTopMenus(Provider<CurrentUser> cup,
      GerritConstantsProperties gcp, GetPreferences gp,
      Provider<GerritConfig> cp) throws IOException {
    currentUserProvider = cup;
    constProps = gcp;
    getPreferences = gp;
    configProvider = cp;
  }

  Collection<TopMenu.MenuEntry> getTopMenuBar(boolean populateMyMenu,
      Project.NameKey projectNameKey) throws AuthException,
      ResourceNotFoundException, OrmException, IOException,
      ConfigInvalidException {
    CurrentUser currentUser = currentUserProvider.get();
    CapabilityControl capabilities = currentUser.getCapabilities();
    boolean signedIn = currentUser.isIdentifiedUser();

    Collection<TopMenu.MenuEntry> topMenu = Lists.newArrayList();
    topMenu
        .add(menu(
            GerritTopMenu.ALL,
            subMenu("menuAllOpen", PageLinks.toChangeQuery("status:open")),
            subMenu("menuAllMerged", PageLinks.toChangeQuery("status:merged")),
            subMenu("menuAllAbandoned",
                PageLinks.toChangeQuery("status:abandoned"))));

    if (signedIn) {
      topMenu.add(menu(GerritTopMenu.MY,
          getMySubMenu((IdentifiedUser) currentUser)));
    }

    List<MenuItem> projectsMenuItems = Lists.newArrayList();
    topMenu.add(menu(GerritTopMenu.PROJECTS, projectsMenuItems));

    projectsMenuItems
        .add(subMenu("menuProjectsList", PageLinks.ADMIN_PROJECTS));

    if (projectNameKey != null) {
      projectsMenuItems.addAll(Arrays
          .asList(
              projectSubMenu(projectNameKey, "menuProjectsInfo", PROJECT_INFO),
              projectSubMenu(projectNameKey, "menuProjectsBranches",
                  PROJECT_BRANCH),
              projectSubMenu(projectNameKey, "menuProjectsAccess",
                  PROJECT_ACCESS),
              projectSubMenu(projectNameKey, "menuProjectsDashboards",
                  PROJECT_DASHBOARDS)));
    }

    if (signedIn) {
      if (capabilities.canCreateProject()) {
        projectsMenuItems.add(subMenu("menuProjectsCreate",
            PageLinks.ADMIN_CREATE_PROJECT));
      }

      List<MenuItem> groupsMenuItems = Lists.newArrayList();
      topMenu.add(menu(GerritTopMenu.PEOPLE, groupsMenuItems));
      groupsMenuItems.add(subMenu("menuPeopleGroupsList",
          PageLinks.ADMIN_GROUPS));

      if (capabilities.canCreateGroup()) {
        groupsMenuItems.add(subMenu("menuPeopleGroupsCreate",
            PageLinks.ADMIN_CREATE_GROUP));
      }

      if (capabilities.canAdministrateServer()) {
        topMenu.add(menu(GerritTopMenu.PLUGINS,
            subMenu("menuPluginsInstalled", PageLinks.ADMIN_PLUGINS)));
      }
    }

    if (isDocumentationAvailable()) {
      topMenu.add(menu(
          GerritTopMenu.DOCUMENTATION,
          subMenuBlank("menuDocumentationTOC", "index.html"),
          subMenuBlank("menuDocumentationSearch", "user-search.html"),
          subMenuBlank("menuDocumentationUpload", "user-upload.html"),
          subMenuBlank("menuDocumentationAccess", "access-control.html"),
          subMenuBlank("menuDocumentationAPI", "rest-api.html"),
          subMenuBlank("menuDocumentationProjectOwnerGuide",
              "intro-project-owner.html")));
    }

    return topMenu;
  }

  private boolean isDocumentationAvailable() {
    return configProvider.get().isDocumentationAvailable();
  }

  private List<MenuItem> getMySubMenu(IdentifiedUser user)
      throws AuthException, ResourceNotFoundException, OrmException,
      IOException, ConfigInvalidException {
    AccountResource account = new AccountResource(user);
    PreferenceInfo preferences = getPreferences.apply(account);
    return preferences.getMyMenu();
  }

  private MenuItem projectSubMenu(Project.NameKey currentProjectName,
      String nameKey, String screen) {
    String projectLink;
    if (screen == null || PROJECT_INFO.equals(screen)) {
      projectLink = ADMIN_PROJECTS + currentProjectName.toString();
    } else {
      projectLink =
          ADMIN_PROJECTS + currentProjectName.toString() + "," + screen;
    }

    return subMenu(nameKey, projectLink);
  }

  private MenuItem subMenu(String nameKey, String link) {
    return new MenuItem(constProps.getProperty(nameKey), link, "_self");
  }

  private MenuItem subMenuBlank(String nameKey, String link) {
    return new MenuItem(constProps.getProperty(nameKey), "/Documentation/" + link, "_blank");
  }

  private MenuEntry menu(GerritTopMenu gerritMenu, MenuItem... items) {
    return new MenuEntry(gerritMenu, Arrays.asList(items));
  }

  private MenuEntry menu(GerritTopMenu gerritMenu, List<MenuItem> menuItems) {
    return new MenuEntry(gerritMenu, menuItems);
  }

}
