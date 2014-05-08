// Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.GerritTopMenu;
import com.google.gerrit.extensions.client.MenuItem;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.webui.TopMenu.MenuEntry;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.client.KeyUtil;
import com.google.gwtorm.client.KeyUtil.Encoder;
import com.google.gwtorm.server.OrmException;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.easymock.EasyMockRunner;
import org.easymock.Mock;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

@RunWith(EasyMockRunner.class)
public class GerritTopMenusTest {

  private Injector baseInjector;

  @Mock
  private ProjectCache projectCacheMock;

  @Mock
  private IdentifiedUser mockIdentifiedUser;

  @Mock
  private com.google.gerrit.server.account.GetPreferences getPreferencesMock;

  @Mock
  private GeneralPreferencesInfo preferenceInfoMock;

  @Mock
  private CapabilityControl capabilitiesControlMock;


  private AbstractModule anonymousUserModule = new AbstractModule() {
    @Override
    protected void configure() {
      bind(CurrentUser.class).to(AnonymousUser.class);
    }
  };

  private AbstractModule identifiedUserModule = new AbstractModule() {
    @Override
    protected void configure() {
      expect(mockIdentifiedUser.isIdentifiedUser()).andReturn(true);
      expect(mockIdentifiedUser.getCapabilities()).andReturn(
          capabilitiesControlMock);
      replay(mockIdentifiedUser);

      try {
        expect(getPreferencesMock.apply(anyObject(AccountResource.class)))
            .andReturn(preferenceInfoMock);
        replay(getPreferencesMock);
      } catch (AuthException | ResourceNotFoundException | OrmException
          | IOException | ConfigInvalidException e) {
        throw new RuntimeException(e);
      }

      expect(capabilitiesControlMock.canCreateProject()).andReturn(false);
      expect(capabilitiesControlMock.canCreateGroup()).andReturn(false);
      expect(capabilitiesControlMock.canViewPlugins()).andReturn(false);
      replay(capabilitiesControlMock);

      bind(CurrentUser.class).toInstance(mockIdentifiedUser);
    }
  };

  @Before
  public void setUp() throws Exception {
    AbstractModule mod = new FactoryModule() {
      @Override
      protected void configure() {
        bind(ProjectCache.class).toInstance(projectCacheMock);
        factory(CapabilityControl.Factory.class);
        bind(com.google.gerrit.server.account.GetPreferences.class).toInstance(
            getPreferencesMock);
      }
    };
    baseInjector = Guice.createInjector(mod);
    KeyUtil.setEncoderImpl(new Encoder() {
      @Override
      public String encode(String e) {
        return e;
      }

      @Override
      public String decode(String e) {
        return e;
      }
    });
  }

  private GerritTopMenus gerritTopMenusWithModule(AbstractModule module) {
    return baseInjector.createChildInjector(module).getInstance(
        GerritTopMenus.class);
  }

  @Test
  public void testTopMenuForAnonymousUserShouldHaveAllThreeMenuEntries() {
    Collection<MenuEntry> entries =
        gerritTopMenusWithModule(anonymousUserModule).getTopMenuBar(null);

    assertMenuEntriesNames(entries, GerritTopMenu.ALL.menuName,
        GerritTopMenu.PROJECTS.menuName, GerritTopMenu.DOCUMENTATION.menuName);
  }

  @Test
  public void testAllMenuEntryForAnonymousUserShouldHaveAllThreeSubMenuItems() {
    MenuEntry allMenuEntry =
        findEntryByName(gerritTopMenusWithModule(anonymousUserModule)
            .getTopMenuBar(null), GerritTopMenu.ALL.menuName);

    assertMenuItemsUrls(allMenuEntry.items, "status:open", "status:merged",
        "status:abandoned");
  }

  @Test
  public void testProjectsMenuEntryForAnonymousUserShouldHaveOneSubMenuItem() {
    MenuEntry projectsMenuEntry =
        findEntryByName(gerritTopMenusWithModule(anonymousUserModule)
            .getTopMenuBar(null), GerritTopMenu.PROJECTS.menuName);

    assertMenuItemsUrls(projectsMenuEntry.items, "/admin/projects/");
  }

  @Test
  public void testDocumentationMenuEntryShouldHaveSixSubMenuItem() {
    MenuEntry docMenuEntry =
        findEntryByName(gerritTopMenusWithModule(anonymousUserModule)
            .getTopMenuBar(null), GerritTopMenu.DOCUMENTATION.menuName);

    assertMenuItemsUrls(docMenuEntry.items, "index.html", "user-search.html",
        "user-upload.html", "access-control.html", "rest-api.html",
        "intro-project-owner.html");
  }

  @Test
  public void testTopMenuForIdentifiedUserShouldHaveAllFiveMenuEntries() {
    Collection<MenuEntry> entries =
        gerritTopMenusWithModule(identifiedUserModule).getTopMenuBar(null);

    assertMenuEntriesNames(entries, GerritTopMenu.ALL.menuName,
        GerritTopMenu.MY.menuName, GerritTopMenu.PROJECTS.menuName,
        GerritTopMenu.PEOPLE.menuName, GerritTopMenu.DOCUMENTATION.menuName);
  }

  @Test
  public void testMyMenuItemForIdentifiedUserShouldHavePersonalisedEntries() {
    preferenceInfoMock.my =
        Arrays.asList(new MenuItem("CustomItem",
            "http://example.com/my-custom-item.html"));

    MenuEntry myMenuEntry =
        findEntryByName(gerritTopMenusWithModule(identifiedUserModule)
            .getTopMenuBar(null), GerritTopMenu.MY.menuName);

    assertMenuItemsUrls(myMenuEntry.items,
        "http://example.com/my-custom-item.html");
  }

  @Test
  public void testProjectsMenuItemForSelectedProjectShouldHaveFiveEntries() {
    MenuEntry projectsMenuEntry =
        findEntryByName(gerritTopMenusWithModule(identifiedUserModule)
            .getTopMenuBar(Project.NameKey.parse("myproject")),
            GerritTopMenu.PROJECTS.menuName);

    assertMenuItemsUrls(projectsMenuEntry.items, "/admin/projects/",
        "/admin/projects/myproject", "/admin/projects/myproject,branches",
        "/admin/projects/myproject,access",
        "/admin/projects/myproject,dashboards");
  }

  private void assertMenuEntriesNames(Collection<MenuEntry> menuEntries,
      String... names) {
    assertThat(menuEntries).hasSize(names.length);
    Iterator<MenuEntry> menuEntriesIter = menuEntries.iterator();
    for (String name : names) {
      assertThat(menuEntriesIter.next().name).isEqualTo(name);
    }
  }

  private void assertMenuItemsUrls(Collection<MenuItem> menuItems,
      String... urlSuffixes) {
    assertThat(menuItems).hasSize(urlSuffixes.length);
    Iterator<MenuItem> menuItemsIter = menuItems.iterator();
    for (String urlSuffix : urlSuffixes) {
      assertThat(menuItemsIter.next().url).endsWith(urlSuffix);
    }
  }

  private MenuEntry findEntryByName(Collection<MenuEntry> menuEntries,
      final String itemName) {
    return Iterables.find(menuEntries, new Predicate<MenuEntry>() {
      @Override
      public boolean apply(MenuEntry menuEntry) {
        return menuEntry.name == itemName;
      }
    });
  }
}
