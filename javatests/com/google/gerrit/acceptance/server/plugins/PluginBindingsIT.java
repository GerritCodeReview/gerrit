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
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.annotations.PluginName;
import com.google.gerrit.extensions.common.VersionInfo;
import com.google.gerrit.server.config.GerritIsReplica;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Provides;
import com.google.inject.internal.UniqueAnnotations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Test;

public class PluginBindingsIT extends AbstractDaemonTest {
  public static class TestPluginModule extends AbstractModule {
    protected final ModuleLoadCounter moduleLoadCounter;

    @Inject
    TestPluginModule(ModuleLoadCounter moduleLoadCounter) {
      this.moduleLoadCounter = moduleLoadCounter;
    }

    @Override
    protected void configure() {
      moduleLoadCounter.completed(getClass());
    }
  }

  public static class TestPluginApiModule extends TestPluginModule {
    @Inject
    TestPluginApiModule(ModuleLoadCounter moduleLoadCounter) {
      super(moduleLoadCounter);
    }

    public interface DummyProvidedByOtherModule {}
  }

  public static class TestPluginSysModule extends TestPluginModule {
    @Inject
    TestPluginSysModule(ModuleLoadCounter moduleLoadCounter) {
      super(moduleLoadCounter);
    }
  }

  public static class PluginInjectsInjectorModule extends AbstractModule {
    private final Injector injector;
    private final ModuleInjectorTester moduleInjectorTester;

    @Inject
    PluginInjectsInjectorModule(Injector injector, ModuleInjectorTester moduleInjectorTester) {
      this.injector = injector;
      this.moduleInjectorTester = moduleInjectorTester;
    }

    @Override
    protected void configure() {
      moduleInjectorTester.called(getClass());
      moduleInjectorTester.test(getClass(), injector);
      moduleInjectorTester.completed(getClass());
    }
  }

  public static class PluginInjectsGerritReplicaModule extends TestPluginModule {
    private final boolean isReplica;

    @Inject
    PluginInjectsGerritReplicaModule(
        @GerritIsReplica boolean isReplica, ModuleLoadCounter moduleLoadCounter) {
      super(moduleLoadCounter);
      this.isReplica = isReplica;
    }

    @Override
    protected void configure() {
      moduleLoadCounter.called(getClass());
      assertThat(isReplica).isFalse();
      moduleLoadCounter.completed(getClass());
    }
  }

  public static class PluginProvidesClassForOtherPluginModule {
    public static class AtProvidesSysModule extends TestPluginModule {
      @Inject
      AtProvidesSysModule(ModuleLoadCounter moduleLoadCounter) {
        super(moduleLoadCounter);
      }

      @Provides
      TestPluginApiModule.DummyProvidedByOtherModule getDummyProvided() {
        return new TestPluginApiModule.DummyProvidedByOtherModule() {};
      }
    }

    public static class BindingProvidesSysModule extends TestPluginModule {
      @Inject
      BindingProvidesSysModule(ModuleLoadCounter moduleLoadCounter) {
        super(moduleLoadCounter);
      }

      private static class DummyProvider
          implements Provider<TestPluginApiModule.DummyProvidedByOtherModule> {
        @Override
        public TestPluginApiModule.DummyProvidedByOtherModule get() {
          return new TestPluginApiModule.DummyProvidedByOtherModule() {};
        }
      }

      @Override
      protected void configure() {
        moduleLoadCounter.called(getClass());
        bind(DummyProvider.class);
        bind(TestPluginApiModule.DummyProvidedByOtherModule.class).toProvider(DummyProvider.class);
        moduleLoadCounter.completed(getClass());
      }
    }

    public static class HttpModule extends TestPluginModule {
      @Inject
      HttpModule(
          TestPluginApiModule.DummyProvidedByOtherModule providedBySysModule,
          ModuleLoadCounter moduleLoadCounter) {
        super(moduleLoadCounter);
        moduleLoadCounter.called(getClass());
        assertThat(providedBySysModule).isNotNull();
        moduleLoadCounter.completed(getClass());
      }
    }
  }

  protected static class MyStartedPluginListener implements StartPluginListener {
    public final Set<String> started = new HashSet<>();

    @Override
    public void onStartPlugin(Plugin plugin) {
      // This isn't perfect confirmation that the plugin finishing starting successfully because
      // other implementors of StartPluginListener (like HttpPluginServlet and
      // SshPluginStarterCallback) have additional logic, but it should be sufficient in the context
      // of testing plugin module bindings. It is not sufficient to confirm that each of the
      // plugin's modules has been loaded.
      started.add(plugin.getName());
    }

    public void reset() {
      started.clear();
    }
  }

  protected static class ModuleLoadCounter {
    protected final Map<Class<? extends AbstractModule>, AtomicInteger> successCountByModule =
        new HashMap<>();

    public void assertThatModuleSuccessfullyLoaded(Class<? extends AbstractModule> module) {
      assertThat(getSuccessfulModules()).contains(module);
    }

    public void assertThatModuleSuccessfullyLoaded(
        Class<? extends AbstractModule> module1, Class<? extends AbstractModule> module2) {
      assertThat(getSuccessfulModules()).containsAtLeast(module1, module2);
    }

    public void assertThatModuleSuccessfullyLoaded(
        Class<? extends AbstractModule> module1,
        Class<? extends AbstractModule> module2,
        Class<? extends AbstractModule> module3) {
      assertThat(getSuccessfulModules()).containsAtLeast(module1, module2, module3);
    }

    private Set<Class<? extends AbstractModule>> getSuccessfulModules() {
      return successCountByModule.entrySet().stream()
          .filter(entry -> entry.getValue().get() != 0)
          .map(Map.Entry::getKey)
          .collect(Collectors.toSet());
    }

    public void reset() {
      successCountByModule.clear();
    }

    public void called(Class<? extends AbstractModule> moduleClass) {
      successCountByModule.putIfAbsent(moduleClass, new AtomicInteger());
    }

    public void completed(Class<? extends AbstractModule> moduleClass) {
      called(moduleClass);
      successCountByModule.get(moduleClass).incrementAndGet();
    }
  }

  protected static class ModuleInjectorTester extends ModuleLoadCounter {
    private final Map<Class<? extends AbstractModule>, Consumer<Injector>> testByModule =
        new HashMap<>();

    public void addTest(Class<? extends AbstractModule> module, Consumer<Injector> test) {
      testByModule.put(module, test);
    }

    @Override
    public void reset() {
      super.reset();
      testByModule.clear();
    }

    public void test(Class<? extends AbstractModule> moduleClass, Injector injectorToTest) {
      Consumer<Injector> test = testByModule.get(moduleClass);
      if (test != null) {
        test.accept(injectorToTest);
      }
    }
  }

  private final ModuleInjectorTester moduleInjectorTester = new ModuleInjectorTester();
  private final ModuleLoadCounter moduleLoadTester = new ModuleLoadCounter();
  private final MyStartedPluginListener startedPluginListener = new MyStartedPluginListener();
  private final List<AutoCloseable> installedPlugins = new ArrayList<>();

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      public void configure() {
        bind(StartPluginListener.class)
            .annotatedWith(UniqueAnnotations.create())
            .toInstance(startedPluginListener);
        bind(ModuleInjectorTester.class).toInstance(moduleInjectorTester);
        bind(ModuleLoadCounter.class).toInstance(moduleLoadTester);
      }
    };
  }

  @After
  public void tearDown() throws Exception {
    for (AutoCloseable p : installedPlugins) {
      if (p != null) {
        p.close();
      }
    }
    installedPlugins.clear();
    startedPluginListener.reset();
    moduleInjectorTester.reset();
    moduleLoadTester.reset();
  }

  private void installPluginForRemovalAfterAndAssertStarted(
      String pluginName, Class<? extends Module> sysModuleClass) throws Exception {
    installedPlugins.add(installPlugin(pluginName, sysModuleClass));
    assertThat(startedPluginListener.started).contains(pluginName);
  }

  private void installPluginForRemovalAfterAndAssertStarted(
      String pluginName,
      @Nullable Class<? extends Module> sysModuleClass,
      @Nullable Class<? extends Module> httpModuleClass,
      @Nullable Class<? extends Module> sshModuleClass,
      @Nullable Class<? extends Module> apiModuleClass)
      throws Exception {
    installedPlugins.add(
        installPlugin(pluginName, sysModuleClass, httpModuleClass, sshModuleClass, apiModuleClass));
    assertThat(startedPluginListener.started).contains(pluginName);
  }

  protected static class InjectedPluginNameAsserter implements Consumer<Injector> {
    private final String expectedPluginName;

    public InjectedPluginNameAsserter(String expectedPluginName) {
      this.expectedPluginName = expectedPluginName;
    }

    @Override
    public void accept(Injector injector) {
      assertThat(injector.getInstance(Key.get(String.class, PluginName.class)))
          .isEqualTo(expectedPluginName);
    }
  }

  @SuppressWarnings("UnnecessaryLambda")
  private final Consumer<Injector> assertCanGetWebAppInstance =
      injector ->
          assertThat(injector.getInstance(Key.get(Boolean.class, GerritIsReplica.class)))
              .isNotNull();

  @SuppressWarnings("UnnecessaryLambda")
  private final Consumer<Injector> assertCanGetGerritGlobalInstance =
      injector -> assertThat(injector.getInstance(VersionInfo.class)).isNotNull();

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingInjector() throws Exception {
    moduleInjectorTester.addTest(
        PluginInjectsInjectorModule.class,
        new InjectedPluginNameAsserter("my-plugin-injecting-injector")
            .andThen(assertCanGetWebAppInstance)
            .andThen(assertCanGetGerritGlobalInstance));
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-injector", PluginInjectsInjectorModule.class);
    moduleInjectorTester.assertThatModuleSuccessfullyLoaded(PluginInjectsInjectorModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingInjectorInHttpModule() throws Exception {
    moduleInjectorTester.addTest(
        PluginInjectsInjectorModule.class,
        new InjectedPluginNameAsserter("my-plugin-injecting-injector-in-httpModule")
            .andThen(assertCanGetWebAppInstance)
            .andThen(assertCanGetGerritGlobalInstance));
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-injector-in-httpModule",
        null,
        PluginInjectsInjectorModule.class,
        null,
        null);
    moduleInjectorTester.assertThatModuleSuccessfullyLoaded(PluginInjectsInjectorModule.class);
  }

  @Test
  @Sandboxed
  @UseSsh
  public void testCanInstallPluginInjectingInjectorInSshModule() throws Exception {
    moduleInjectorTester.addTest(
        PluginInjectsInjectorModule.class,
        new InjectedPluginNameAsserter("my-plugin-injecting-injector-in-sshModule")
            .andThen(assertCanGetWebAppInstance)
            .andThen(assertCanGetGerritGlobalInstance));
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-injector-in-sshModule",
        null,
        null,
        PluginInjectsInjectorModule.class,
        null);
    moduleInjectorTester.assertThatModuleSuccessfullyLoaded(PluginInjectsInjectorModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingInjectorAfterInstallingApiModule() throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        TestPluginSysModule.class, TestPluginApiModule.class);
    moduleInjectorTester.addTest(
        PluginInjectsInjectorModule.class,
        new InjectedPluginNameAsserter("my-plugin-injecting-injector")
            .andThen(assertCanGetWebAppInstance)
            .andThen(assertCanGetGerritGlobalInstance));
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-injector", PluginInjectsInjectorModule.class);
    moduleInjectorTester.assertThatModuleSuccessfullyLoaded(PluginInjectsInjectorModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingInjectorAndApiModule() throws Exception {
    moduleInjectorTester.addTest(
        PluginInjectsInjectorModule.class,
        new InjectedPluginNameAsserter("my-plugin-injecting-injector-and-apiModule")
            .andThen(assertCanGetWebAppInstance)
            .andThen(assertCanGetGerritGlobalInstance));
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-injector-and-apiModule",
        PluginInjectsInjectorModule.class,
        null,
        null,
        TestPluginApiModule.class);
    moduleInjectorTester.assertThatModuleSuccessfullyLoaded(PluginInjectsInjectorModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(TestPluginApiModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingInjectorAndApiModuleAfterInstallingApiModule()
      throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        TestPluginSysModule.class, TestPluginApiModule.class);
    moduleInjectorTester.addTest(
        PluginInjectsInjectorModule.class,
        new InjectedPluginNameAsserter("my-plugin-injecting-injector-and-apiModule")
            .andThen(assertCanGetWebAppInstance)
            .andThen(assertCanGetGerritGlobalInstance));
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-injector-and-apiModule",
        PluginInjectsInjectorModule.class,
        null,
        null,
        TestPluginApiModule.class);
    moduleInjectorTester.assertThatModuleSuccessfullyLoaded(PluginInjectsInjectorModule.class);
    // TODO Check if TestPluginApiModule was loaded twice?
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingInjectorInHttpModuleAfterInstallingApiModule()
      throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        TestPluginSysModule.class, TestPluginApiModule.class);
    moduleInjectorTester.addTest(
        PluginInjectsInjectorModule.class,
        new InjectedPluginNameAsserter("my-plugin-injecting-injector")
            .andThen(assertCanGetWebAppInstance)
            .andThen(assertCanGetGerritGlobalInstance));
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-injector", null, PluginInjectsInjectorModule.class, null, null);
    moduleInjectorTester.assertThatModuleSuccessfullyLoaded(PluginInjectsInjectorModule.class);
  }

  @Test
  @Sandboxed
  @UseSsh
  public void testCanInstallPluginInjectingInjectorInSshModuleAfterInstallingApiModule()
      throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        TestPluginSysModule.class, TestPluginApiModule.class);
    moduleInjectorTester.addTest(
        PluginInjectsInjectorModule.class,
        new InjectedPluginNameAsserter("my-plugin-injecting-injector")
            .andThen(assertCanGetWebAppInstance)
            .andThen(assertCanGetGerritGlobalInstance));
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-injector", null, null, PluginInjectsInjectorModule.class, null);
    moduleInjectorTester.assertThatModuleSuccessfullyLoaded(PluginInjectsInjectorModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplica() throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-replica", PluginInjectsGerritReplicaModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(PluginInjectsGerritReplicaModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplicaInApiModule() throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-replica-api-plugin",
        TestPluginSysModule.class,
        null,
        null,
        PluginInjectsGerritReplicaModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        TestPluginSysModule.class, PluginInjectsGerritReplicaModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplicaInHttpModule() throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-replica-http",
        null,
        PluginInjectsGerritReplicaModule.class,
        null,
        null);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(PluginInjectsGerritReplicaModule.class);
  }

  @Test
  @Sandboxed
  @UseSsh
  public void testCanInstallPluginInjectingReplicaInSshModule() throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-replica-ssh",
        null,
        null,
        PluginInjectsGerritReplicaModule.class,
        null);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(PluginInjectsGerritReplicaModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplicaAfterInstallingApiModule() throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        TestPluginSysModule.class, TestPluginApiModule.class);
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-replica", PluginInjectsGerritReplicaModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(PluginInjectsGerritReplicaModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplicaAndApiModule() throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-replica-and-apiModule",
        PluginInjectsGerritReplicaModule.class,
        null,
        null,
        TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        PluginInjectsGerritReplicaModule.class, TestPluginApiModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplicaAndApiModuleAfterInstallingApiModule()
      throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        TestPluginSysModule.class, TestPluginApiModule.class);
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-replica-and-apiModule",
        PluginInjectsGerritReplicaModule.class,
        null,
        null,
        TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        PluginInjectsGerritReplicaModule.class, TestPluginApiModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginInjectingReplicaInHttpModuleAfterInstallingApiModule()
      throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        TestPluginSysModule.class, TestPluginApiModule.class);
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-replica", null, PluginInjectsGerritReplicaModule.class, null, null);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(PluginInjectsGerritReplicaModule.class);
  }

  @Test
  @Sandboxed
  @UseSsh
  public void testCanInstallPluginInjectingReplicaInSshModuleAfterInstallingApiModule()
      throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        TestPluginSysModule.class, TestPluginApiModule.class);
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-replica", null, null, PluginInjectsGerritReplicaModule.class, null);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(PluginInjectsGerritReplicaModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingAtProvidesInternally() throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-using-at-provides-internally",
        PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
        PluginProvidesClassForOtherPluginModule.HttpModule.class,
        null,
        null);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
        PluginProvidesClassForOtherPluginModule.HttpModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingBindingProvidesInternally() throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-using-binding-provides-internally",
        PluginProvidesClassForOtherPluginModule.BindingProvidesSysModule.class,
        PluginProvidesClassForOtherPluginModule.HttpModule.class,
        null,
        null);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        PluginProvidesClassForOtherPluginModule.BindingProvidesSysModule.class,
        PluginProvidesClassForOtherPluginModule.HttpModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingAtProvidesInternallyAndApiModule() throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-using-at-provides-internally-and-apiModule",
        PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
        PluginProvidesClassForOtherPluginModule.HttpModule.class,
        null,
        TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
        PluginProvidesClassForOtherPluginModule.HttpModule.class,
        TestPluginApiModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingBindingProvidesInternallyAndApiModule() throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-using-binding-provides-internally-and-apiModule",
        PluginProvidesClassForOtherPluginModule.BindingProvidesSysModule.class,
        PluginProvidesClassForOtherPluginModule.HttpModule.class,
        null,
        TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        PluginProvidesClassForOtherPluginModule.BindingProvidesSysModule.class,
        PluginProvidesClassForOtherPluginModule.HttpModule.class,
        TestPluginApiModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingAtProvidesInternallyAfterInstallingApiModule()
      throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        TestPluginSysModule.class, TestPluginApiModule.class);
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-using-at-provides-internally",
        PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
        PluginProvidesClassForOtherPluginModule.HttpModule.class,
        null,
        null);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
        PluginProvidesClassForOtherPluginModule.HttpModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingBindingProvidesInternallyAfterInstallingApiModule()
      throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        TestPluginSysModule.class, TestPluginApiModule.class);
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-using-binding-provides-internally",
        PluginProvidesClassForOtherPluginModule.BindingProvidesSysModule.class,
        PluginProvidesClassForOtherPluginModule.HttpModule.class,
        null,
        null);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        PluginProvidesClassForOtherPluginModule.BindingProvidesSysModule.class,
        PluginProvidesClassForOtherPluginModule.HttpModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingAtProvidesInternallyViaInjectorAfterInstallingApiModule()
      throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        TestPluginSysModule.class, TestPluginApiModule.class);
    moduleInjectorTester.addTest(
        PluginInjectsInjectorModule.class,
        injector ->
            assertThat(injector.getInstance(TestPluginApiModule.DummyProvidedByOtherModule.class))
                .isNotNull());
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-using-at-provides-internally",
        PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
        PluginInjectsInjectorModule.class,
        null,
        null);
    moduleInjectorTester.assertThatModuleSuccessfullyLoaded(PluginInjectsInjectorModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class);
  }

  @Test
  @Sandboxed
  public void
      testCanInstallPluginUsingBindingProvidesInternallyViaInjectorAfterInstallingApiModule()
          throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        TestPluginSysModule.class, TestPluginApiModule.class);
    moduleInjectorTester.addTest(
        PluginInjectsInjectorModule.class,
        injector ->
            assertThat(injector.getInstance(TestPluginApiModule.DummyProvidedByOtherModule.class))
                .isNotNull());
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-using-binding-provides-internally",
        PluginProvidesClassForOtherPluginModule.BindingProvidesSysModule.class,
        PluginInjectsInjectorModule.class,
        null,
        null);
    moduleInjectorTester.assertThatModuleSuccessfullyLoaded(PluginInjectsInjectorModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        PluginProvidesClassForOtherPluginModule.BindingProvidesSysModule.class);
  }

  @Test
  @Sandboxed
  public void testCanInstallPluginUsingAtProvidesInternallyAndApiModuleAfterInstallingApiModule()
      throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-api-plugin", TestPluginSysModule.class, null, null, TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        TestPluginSysModule.class, TestPluginApiModule.class);
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-using-at-provides-internally-and-apiModule",
        PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
        PluginProvidesClassForOtherPluginModule.HttpModule.class,
        null,
        TestPluginApiModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
        PluginProvidesClassForOtherPluginModule.HttpModule.class,
        TestPluginApiModule.class);
  }

  @Test
  @Sandboxed
  public void
      testCanInstallPluginUsingAtProvidesInternallyAfterInstallingPluginInjectingReplicaInApiModule()
          throws Exception {
    installPluginForRemovalAfterAndAssertStarted(
        "my-replica-api-plugin",
        TestPluginSysModule.class,
        null,
        null,
        PluginInjectsGerritReplicaModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        TestPluginSysModule.class, PluginInjectsGerritReplicaModule.class);
    moduleInjectorTester.addTest(
        PluginInjectsInjectorModule.class,
        injector ->
            assertThat(injector.getInstance(TestPluginApiModule.DummyProvidedByOtherModule.class))
                .isNotNull());
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-using-at-provides-internally",
        PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class,
        PluginInjectsInjectorModule.class,
        null,
        null);
    moduleInjectorTester.assertThatModuleSuccessfullyLoaded(PluginInjectsInjectorModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(
        PluginProvidesClassForOtherPluginModule.AtProvidesSysModule.class);
  }

  @Test
  @Sandboxed
  public void testPluginApiModuleCannotInjectPluginResources() throws Exception {
    // Items bound in ServerPluginInfoModule are not expected to be available when the apiModule
    // injector is created
    moduleInjectorTester.addTest(
        PluginInjectsInjectorModule.class,
        ((Consumer<Injector>)
                injector -> {
                  assertThrows(
                      ConfigurationException.class,
                      () -> injector.getInstance(Key.get(String.class, PluginName.class)));
                })
            .andThen(assertCanGetWebAppInstance)
            .andThen(assertCanGetGerritGlobalInstance));
    installPluginForRemovalAfterAndAssertStarted(
        "my-plugin-injecting-injector-as-apiModule",
        TestPluginSysModule.class,
        null,
        null,
        PluginInjectsInjectorModule.class);
    moduleInjectorTester.assertThatModuleSuccessfullyLoaded(PluginInjectsInjectorModule.class);
    moduleLoadTester.assertThatModuleSuccessfullyLoaded(TestPluginSysModule.class);
  }
}
