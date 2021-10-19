// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.TopMenu;
import com.google.gerrit.extensions.webui.TopMenu.MenuEntry;
import com.google.inject.AbstractModule;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

@TestPlugin(
    name = "test-topmenus",
    sysModule = "com.google.gerrit.acceptance.api.config.TopMenusIT$TestModule")
public class TopMenusIT extends LightweightPluginDaemonTest {

  static final TopMenu.MenuEntry TEST_MENU_ENTRY =
      new TopMenu.MenuEntry("MyMenu", Collections.emptyList());

  public static class TestModule extends AbstractModule {

    @Override
    protected void configure() {
      DynamicSet.bind(binder(), TopMenu.class).to(TopMenuTest.class);
    }
  }

  public static class TopMenuTest implements TopMenu {

    @Override
    public List<MenuEntry> getEntries() {
      return Arrays.asList(TEST_MENU_ENTRY);
    }
  }

  @Test
  public void topMenuShouldReturnOneEntry() throws RestApiException {
    List<MenuEntry> topMenuItems = gApi.config().server().topMenus();
    assertThat(topMenuItems).containsExactly(TEST_MENU_ENTRY);
  }
}
