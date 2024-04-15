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

package com.google.gerrit.acceptance.server.plugins;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.server.config.GerritIsReplica;
import com.google.inject.AbstractModule;
import com.google.inject.CreationException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.Provides;
import org.junit.Test;

public class PluginBindingsIT extends AbstractDaemonTest {
  public static class TestPluginApiModule extends AbstractModule {
    public interface DummyProvidedByOtherModule {}
  }

  public static class TestPluginSysModule extends AbstractModule {}

  public static class PluginInjectsInjectorModule extends AbstractModule {
    private final Injector injector;

    @Inject
    PluginInjectsInjectorModule(Injector injector) {
      this.injector = injector;
    }

    @Override
    protected void configure() {
      Key<String> pluginNameKey = Key.get(String.class, PluginName.class);
      assertThat(injector.getInstance(pluginNameKey)).isNotNull();
      Key<Boolean> isReplicaKey = Key.get(Boolean.class, GerritIsReplica.class);
      assertThat(injector.getInstance(isReplicaKey)).isNotNull();
    }
  }

  public static class PluginInjectsGerritReplicaModule extends AbstractModule {
    private final boolean isReplica;

    @Inject
    PluginInjectsGerritReplicaModule(@GerritIsReplica boolean isReplica) {
      this.isReplica = isReplica;
    }

    @Override
    protected void configure() {
      assertThat(isReplica).isFalse();
    }
  }

  public static class PluginProvidesClassForOtherPluginModule {

    public static class AtProvidesSysModule extends AbstractModule {
      @Provides
      TestPluginApiModule.DummyProvidedByOtherModule getDummyProvided() {
        return new TestPluginApiModule.DummyProvidedByOtherModule() {};
      }
    }

    public static class BindingProvidesSysModule extends AbstractModule {
      private static class DummyProvider
          implements Provider<TestPluginApiModule.DummyProvidedByOtherModule> {

        @Override
        public TestPluginApiModule.DummyProvidedByOtherModule get() {
          return new TestPluginApiModule.DummyProvidedByOtherModule() {};
        }
      }

      @Override
      protected void configure() {
        bind(DummyProvider.class);
        bind(TestPluginApiModule.DummyProvidedByOtherModule.class).toProvider(DummyProvider.class);
      }
    }

    public static class HttpModule extends AbstractModule {
      @Inject
      HttpModule(TestPluginApiModule.DummyProvidedByOtherModule providedBySysModule) {
        assertThat(providedBySysModule).isNotNull();
      }
    }

    public static class HttpModuleUsingInjector extends AbstractModule {
      private final Injector injector;

      @Inject
      HttpModuleUsingInjector(Injector injector) {
        this.injector = injector;
      }

      @Override
      protected void configure() {
        assertThat(injector.getInstance(TestPluginApiModule.DummyProvidedByOtherModule.class))
            .isNotNull();
      }
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingInjector() throws Exception {
    try (AutoCloseable ignored =
        installPlugin("my-plugin-injecting-injector", PluginInjectsInjectorModule.class)) {
      // test passes so long as no exception is thrown
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingInjectorInHttpModule() throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-plugin-injecting-injector", null, PluginInjectsInjectorModule.class, null, null)) {
      // test passes so long as no exception is thrown
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingInjectorInSshModule() throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-plugin-injecting-injector", null, null, PluginInjectsInjectorModule.class, null)) {
      // test passes so long as no exception is thrown
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingInjectorAfterInstallingApiModule() throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin("my-plugin-injecting-injector", PluginInjectsInjectorModule.class)) {
        // test passes so long as no exception is thrown
      }
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingInjectorAndApiModule() throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-plugin-injecting-injector-and-apiModule",
            PluginInjectsInjectorModule.class,
            null,
            null,
            TestPluginApiModule.class)) {
      // test passes so long as no exception is thrown
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingInjectorAndApiModuleAfterInstallingApiModule()
      throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin(
              "my-plugin-injecting-injector-and-apiModule",
              PluginInjectsInjectorModule.class,
              null,
              null,
              TestPluginApiModule.class)) {
        // test passes so long as no exception is thrown
      }
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingInjectorInHttpModuleAfterInstallingApiModule()
      throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin(
              "my-plugin-injecting-injector",
              null,
              PluginInjectsInjectorModule.class,
              null,
              null)) {
        // test passes so long as no exception is thrown
      }
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingInjectorInSshModuleAfterInstallingApiModule()
      throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin(
              "my-plugin-injecting-injector",
              null,
              null,
              PluginInjectsInjectorModule.class,
              null)) {
        // test passes so long as no exception is thrown
      }
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplica() throws Exception {
    try (AutoCloseable ignored =
        installPlugin("my-plugin-injecting-replica", PluginInjectsGerritReplicaModule.class)) {
      // test passes so long as no exception is thrown
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplicaInApiModule() throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin",
            TestPluginSysModule.class,
            null,
            null,
            PluginInjectsGerritReplicaModule.class)) {
      // test passes so long as no exception is thrown
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplicaInHttpModule() throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-plugin-injecting-replica-http",
            null,
            PluginInjectsGerritReplicaModule.class,
            null,
            null)) {
      // test passes so long as no exception is thrown
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplicaInSshModule() throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-plugin-injecting-replica-ssh",
            null,
            null,
            PluginInjectsGerritReplicaModule.class,
            null)) {
      // test passes so long as no exception is thrown
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplicaAfterInstallingApiModule() throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin("my-plugin-injecting-replica", PluginInjectsGerritReplicaModule.class)) {
        // test passes so long as no exception is thrown
      }
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplicaAndApiModule() throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-plugin-injecting-replica-and-apiModule",
            PluginInjectsGerritReplicaModule.class,
            null,
            null,
            TestPluginApiModule.class)) {
      // test passes so long as no exception is thrown
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplicaAndApiModuleAfterInstallingApiModule()
      throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin(
              "my-plugin-injecting-replica-and-apiModule",
              PluginInjectsGerritReplicaModule.class,
              null,
              null,
              TestPluginApiModule.class)) {
        // test passes so long as no exception is thrown
      }
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplicaInHttpModuleAfterInstallingApiModule()
      throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin(
              "my-plugin-injecting-replica",
              null,
              PluginInjectsGerritReplicaModule.class,
              null,
              null)) {
        // test passes so long as no exception is thrown
      }
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplicaInSshModuleAfterInstallingApiModule()
      throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin(
              "my-plugin-injecting-replica",
              null,
              null,
              PluginInjectsGerritReplicaModule.class,
              null)) {
        // test passes so long as no exception is thrown
      }
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingAtProvidesInternally() throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-plugin-using-at-provides-internally",
            PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
            PluginProvidesClassForOtherPluginModule.HttpModule.class,
            null,
            null)) {
      // test passes so long as no exception is thrown
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingBindingProvidesInternally() throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-plugin-using-binding-provides-internally",
            PluginProvidesClassForOtherPluginModule.BindingProvidesSysModule.class,
            PluginProvidesClassForOtherPluginModule.HttpModule.class,
            null,
            null)) {
      // test passes so long as no exception is thrown
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingAtProvidesInternallyAndApiModule() throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-plugin-using-at-provides-internally-and-apiModule",
            PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
            PluginProvidesClassForOtherPluginModule.HttpModule.class,
            null,
            TestPluginApiModule.class)) {
      // test passes so long as no exception is thrown
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingBindingProvidesInternallyAndApiModule() throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-plugin-using-binding-provides-internally-and-apiModule",
            PluginProvidesClassForOtherPluginModule.BindingProvidesSysModule.class,
            PluginProvidesClassForOtherPluginModule.HttpModule.class,
            null,
            TestPluginApiModule.class)) {
      // test passes so long as no exception is thrown
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingAtProvidesInternallyAfterInstallingApiModule()
      throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin(
              "my-plugin-using-at-provides-internally",
              PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
              PluginProvidesClassForOtherPluginModule.HttpModule.class,
              null,
              null)) {
        // test passes so long as no exception is thrown
      }
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingBindingProvidesInternallyAfterInstallingApiModule()
      throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin(
              "my-plugin-using-binding-provides-internally",
              PluginProvidesClassForOtherPluginModule.BindingProvidesSysModule.class,
              PluginProvidesClassForOtherPluginModule.HttpModule.class,
              null,
              null)) {
        // test passes so long as no exception is thrown
      }
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingAtProvidesInternallyViaInjectorAfterInstallingApiModule()
      throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin(
              "my-plugin-using-at-provides-internally",
              PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
              PluginProvidesClassForOtherPluginModule.HttpModuleUsingInjector.class,
              null,
              null)) {
        // test passes so long as no exception is thrown
      }
    }
  }

  @Test
  @Sandboxed
  public void
      testCanInstallPluginUsingBindingProvidesInternallyViaInjectorAfterInstallingApiModule()
          throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin(
              "my-plugin-using-binding-provides-internally",
              PluginProvidesClassForOtherPluginModule.BindingProvidesSysModule.class,
              PluginProvidesClassForOtherPluginModule.HttpModuleUsingInjector.class,
              null,
              null)) {
        // test passes so long as no exception is thrown
      }
    }
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingAtProvidesInternallyAndApiModuleAfterInstallingApiModule()
      throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin(
              "my-plugin-using-at-provides-internally-and-apiModule",
              PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
              PluginProvidesClassForOtherPluginModule.HttpModule.class,
              null,
              TestPluginApiModule.class)) {
        // test passes so long as no exception is thrown
      }
    }
  }

  @Test
  @Sandboxed
  public void
      testCanInstallPluginUsingAtProvidesInternallyAfterInstallingPluginInjectingReplicaInApiModule()
          throws Exception {
    try (AutoCloseable ignored =
        installPlugin(
            "my-api-plugin",
            TestPluginSysModule.class,
            null,
            null,
            PluginInjectsGerritReplicaModule.class)) {
      try (AutoCloseable ignored2 =
          installPlugin(
              "my-plugin-using-at-provides-internally",
              PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
              PluginProvidesClassForOtherPluginModule.HttpModuleUsingInjector.class,
              null,
              null)) {
        // test passes so long as no exception is thrown
      }
    }
  }

  @Test
  @Sandboxed
  public void testPluginApiModuleCannotInjectPluginResources() throws Exception {
    // Items bound in ServerPluginInfoModule are not expected to be available when the apiModule
    // injector is created
    assertThrows(
        CreationException.class,
        () ->
            installPlugin(
                "my-plugin-injecting-injector-as-apiModule",
                TestPluginSysModule.class,
                null,
                null,
                PluginInjectsInjectorModule.class));
  }
}
