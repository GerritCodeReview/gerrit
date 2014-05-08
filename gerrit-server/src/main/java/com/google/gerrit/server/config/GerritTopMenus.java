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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Singleton
class GerritTopMenus {
  private static final Logger log = LoggerFactory.getLogger(GerritTopMenus.class);
  private static final String PROJECT_INFO = "info";
  private static final String PROJECT_BRANCH = "branches";
  private static final String PROJECT_ACCESS = "access";
  private static final String PROJECT_DASHBOARDS = "dashboards";

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
      Project.NameKey projectNameKey)  {
    CurrentUser currentUser = currentUserProvider.get();
    CapabilityControl capabilities = currentUser.getCapabilities();
    boolean signedIn = currentUser.isIdentifiedUser();

    Collection<TopMenu.MenuEntry> topMenu = Lists.newArrayList();
    topMenu.add(menu(GerritTopMenu.ALL,
        subMenu("menuAllOpen", changeQuery("status:open")),
        subMenu("menuAllMerged", changeQuery("status:merged")),
        subMenu("menuAllAbandoned", changeQuery("status:abandoned"))));

    if (signedIn) {
      topMenu.add(menu(GerritTopMenu.MY,
          getMySubMenu((IdentifiedUser) currentUser)));
    }

    List<MenuItem> projectsMenuItems = Lists.newArrayList();
    topMenu.add(menu(GerritTopMenu.PROJECTS, projectsMenuItems));

    projectsMenuItems
        .add(subMenu("menuProjectsList", hashLink(PageLinks.ADMIN_PROJECTS)));

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
            hashLink(PageLinks.ADMIN_CREATE_PROJECT)));
      }

      List<MenuItem> groupsMenuItems = Lists.newArrayList();
      topMenu.add(menu(GerritTopMenu.PEOPLE, groupsMenuItems));
      groupsMenuItems.add(subMenu("menuPeopleGroupsList",
          hashLink(PageLinks.ADMIN_GROUPS)));

      if (capabilities.canCreateGroup()) {
        groupsMenuItems.add(subMenu("menuPeopleGroupsCreate",
            hashLink(PageLinks.ADMIN_CREATE_GROUP)));
      }

      if (capabilities.canAdministrateServer()) {
        topMenu.add(menu(GerritTopMenu.PLUGINS,
            subMenu("menuPluginsInstalled", hashLink(PageLinks.ADMIN_PLUGINS))));
      }
    }

    if (isDocumentationAvailable()) {
      topMenu.add(menu(
          GerritTopMenu.DOCUMENTATION,
          subMenuDocumentation("menuDocumentationTOC", "index.html"),
          subMenuDocumentation("menuDocumentationSearch", "user-search.html"),
          subMenuDocumentation("menuDocumentationUpload", "user-upload.html"),
          subMenuDocumentation("menuDocumentationAccess", "access-control.html"),
          subMenuDocumentation("menuDocumentationAPI", "rest-api.html"),
          subMenuDocumentation("menuDocumentationProjectOwnerGuide",
              "intro-project-owner.html")));
    }

    return topMenu;
  }

  private String changeQuery(String query) {
    return "#" + PageLinks.QUERY + query;
  }

  private boolean isDocumentationAvailable() {
    return configProvider.get().isDocumentationAvailable();
  }

  private List<MenuItem> getMySubMenu(IdentifiedUser user) {
    AccountResource account = new AccountResource(user);
    PreferenceInfo preferences;
    try {
      preferences = getPreferences.apply(account);
      return preferences.getMyMenu();
    } catch (AuthException | ResourceNotFoundException | OrmException
        | IOException | ConfigInvalidException e) {
      log.error("Unable to access My sub-menu preferences", e);
      return Collections.emptyList();
    }
  }

  private MenuItem projectSubMenu(Project.NameKey nameKey, String menuKey,
      String screen) {
    String projectLink = projectLink(ADMIN_PROJECTS, nameKey);
    if (screen != null && !PROJECT_INFO.equals(screen)) {
      projectLink += "," + screen;
    }
    return subMenu(menuKey, projectLink);
  }

  private String projectLink(String screenName, Project.NameKey nameKey) {
    return hashLink(screenName) + nameKey.toString();
  }

  private String hashLink(String screenName) {
    return "#" + screenName;
  }

  private MenuItem subMenu(String nameKey, String link) {
    return new MenuItem(constProps.getProperty(nameKey), link, "_self");
  }

  private MenuItem subMenuDocumentation(String nameKey, String link) {
    return new MenuItem(constProps.getProperty(nameKey), "/Documentation/" + link, "_blank");
  }

  private MenuEntry menu(GerritTopMenu gerritMenu, MenuItem... items) {
    return new MenuEntry(gerritMenu, Arrays.asList(items));
  }

  private MenuEntry menu(GerritTopMenu gerritMenu, List<MenuItem> menuItems) {
    return new MenuEntry(gerritMenu, menuItems);
  }

}
