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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.extensions.client.GerritTopMenu;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.webui.TopMenu.MenuEntry;
import com.google.gerrit.extensions.webui.TopMenu.MenuItem;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.easymock.EasyMockRunner;
import org.easymock.Mock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.List;

@RunWith(EasyMockRunner.class)
public class GerritTopMenusTest {

  private Injector baseInjector;

  @Mock
  private ProjectCache projectCacheMock;

  @Mock
  private com.google.gerrit.server.account.GetPreferences getPreferencesMock;

  private AbstractModule anonymousUserModule = new AbstractModule() {
    @Override
    protected void configure() {
      bind(CurrentUser.class).to(AnonymousUser.class);
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
  }

  private GerritTopMenus gerritTopMenusWithModule(AbstractModule module) {
    Injector injector = baseInjector.createChildInjector(module);
    return injector.getInstance(GerritTopMenus.class);
  }

  @Test
  public void testTopMenuForAnonymousUserShouldNotBeEmpty() {
    Collection<MenuEntry> menuEntries =
        gerritTopMenusWithModule(anonymousUserModule).getTopMenuBar(null);

    assertTrue(!menuEntries.isEmpty());
  }

  @Test
  public void testTopMenuForAnonymousUserShouldHaveAllMenuEntry() {
    Collection<MenuEntry> menuEntries =
        gerritTopMenusWithModule(anonymousUserModule).getTopMenuBar(null);


    MenuEntry allMenuItem = menuEntries.iterator().next();
    assertEquals(GerritTopMenu.ALL.menuName, allMenuItem.name);

    List<MenuItem> allItems = allMenuItem.items;
    assertEquals(3, allItems.size());
    assertEquals("menuAllOpen", allItems.get(0).name);

  }
}
