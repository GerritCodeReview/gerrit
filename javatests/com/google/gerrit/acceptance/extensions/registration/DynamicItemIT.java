// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.acceptance.extensions.registration;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.ProvisionException;
import org.junit.Test;

@TestPlugin(
    name = "dynamic-item-plugin",
    sysModule = "com.google.gerrit.acceptance.extensions.registration.DynamicItemIT$TestModule")
public class DynamicItemIT extends LightweightPluginDaemonTest {
  private static final String PLUGIN_NAME = "my-plugin";

  public static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      DynamicItem.itemOf(binder(), TestInterface.class);
      DynamicItem.bind(binder(), TestInterface.class).to(TestInterfaceImpl.class);
    }
  }

  static class MyPluginSysModule extends AbstractModule {
    @Override
    public void configure() {
      install(
          new AbstractModule() {
            @Override
            public void configure() {
              DynamicItem.bind(binder(), TestInterface.class).to(TestInterfaceImpl2.class);
            }
          });
    }
  }

  @Test
  public void testDynamicItemFinal() {
    TestInterface testInterface =
        plugin.getSysInjector().getInstance(new Key<DynamicItem<TestInterface>>() {}).get();

    assertThat(testInterface).isInstanceOf(TestInterfaceImpl.class);
    testInterface.doSomething();

    assertThrows(
        ProvisionException.class,
        () -> installPlugin(PLUGIN_NAME, MyPluginSysModule.class, null, null));
  }

  @DynamicItem.Final
  private interface TestInterface {
    void doSomething();
  }

  private static class TestInterfaceImpl implements TestInterface {
    @Override
    public void doSomething() {
      // do nothing
    }
  }

  private static class TestInterfaceImpl2 implements TestInterface {
    @Override
    public void doSomething() {
      // do nothing
    }
  }
}
