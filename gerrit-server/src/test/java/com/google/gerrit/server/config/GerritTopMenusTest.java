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

import com.google.gerrit.extensions.client.GerritTopMenu;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.webui.TopMenu.MenuEntry;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResource;
import com.google.gerrit.server.account.CapabilityControl;
import com.google.gerrit.server.project.ProjectCache;
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
  private com.google.gerrit.server.account.GetPreferences.PreferenceInfo preferenceInfoMock;

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
  }

  private GerritTopMenus gerritTopMenusWithModule(AbstractModule module) {
    return baseInjector.createChildInjector(module).getInstance(
        GerritTopMenus.class);
  }

  @Test
  public void testTopMenuForAnonymousUserShouldNotBeEmpty() {
    Collection<MenuEntry> menuEntries =
        gerritTopMenusWithModule(anonymousUserModule).getTopMenuBar(null);

    assertThat(menuEntries).isNotEmpty();
  }

  @Test
  public void testTopMenuForIdentifiedUserShouldNotBeEmpty() {
    Collection<MenuEntry> menuEntries =
        gerritTopMenusWithModule(identifiedUserModule).getTopMenuBar(null);

    assertThat(menuEntries).isNotEmpty();
  }

  @Test
  public void testTopMenuForAnonymousUserShouldHaveAllThreeMenuEntries() {
    Collection<MenuEntry> entries =
        gerritTopMenusWithModule(anonymousUserModule).getTopMenuBar(null);
    Iterator<MenuEntry> entriesIterator = entries.iterator();

    assertThat(entries).hasSize(3);
    assertThat(entriesIterator.next().name).isEqualTo(
        GerritTopMenu.ALL.menuName);
    assertThat(entriesIterator.next().name).isEqualTo(
        GerritTopMenu.PROJECTS.menuName);
    assertThat(entriesIterator.next().name).isEqualTo(
        GerritTopMenu.DOCUMENTATION.menuName);
  }

  @Test
  public void testTopMenuForIdentifiedUserShouldHaveAllFiveMenuEntries() {
    Collection<MenuEntry> entries =
        gerritTopMenusWithModule(identifiedUserModule).getTopMenuBar(null);
    Iterator<MenuEntry> entriesIterator = entries.iterator();

    assertThat(entries).hasSize(5);
    assertThat(entriesIterator.next().name).isEqualTo(
        GerritTopMenu.ALL.menuName);
    assertThat(entriesIterator.next().name)
        .isEqualTo(GerritTopMenu.MY.menuName);
    assertThat(entriesIterator.next().name).isEqualTo(
        GerritTopMenu.PROJECTS.menuName);
    assertThat(entriesIterator.next().name).isEqualTo(
        GerritTopMenu.PEOPLE.menuName);
    assertThat(entriesIterator.next().name).isEqualTo(
        GerritTopMenu.DOCUMENTATION.menuName);
  }
}
