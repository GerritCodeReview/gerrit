package com.google.gerrit.acceptance.api.plugin;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.api.plugin.PluginIT.pluginContent;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.extensions.api.plugins.InstallPluginInput;
import com.google.gerrit.extensions.common.PluginInfo;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.gerrit.server.plugins.StopPluginListener;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.internal.UniqueAnnotations;
import org.junit.Test;

@TestPlugin(
    name = "plugin-start-stop-listener",
    sysModule = "com.google.gerrit.acceptance.api.plugin.PluginOnStartStopIT$TestModule")
public class PluginOnStartStopIT extends LightweightPluginDaemonTest {
  private static final String TEST_PLUGIN = "test-plugin";
  private static final String TEST_PLUGIN_FILENAME = TEST_PLUGIN + ".jar";

  @Singleton
  public static class TestStartPluginListener implements StartPluginListener {
    public volatile Plugin plugin;

    @Override
    public void onStartPlugin(Plugin plugin) {
      this.plugin = plugin;
    }
  }

  @Singleton
  public static class TestStopPluginListener implements StopPluginListener {
    public volatile Plugin plugin;

    @Override
    public void onStopPlugin(Plugin plugin) {
      this.plugin = plugin;
    }
  }

  public static class TestModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(StartPluginListener.class)
          .annotatedWith(UniqueAnnotations.create())
          .to(TestStartPluginListener.class);
      bind(StopPluginListener.class)
          .annotatedWith(UniqueAnnotations.create())
          .to(TestStopPluginListener.class);
    }
  }

  @Test
  @GerritConfig(name = "plugins.allowRemoteAdmin", value = "true")
  public void pluginStartStopListener_calledOnPluginLoadedUnloaded() throws Exception {
    Injector pluginSysInjector = plugin.getSysInjector();
    TestStartPluginListener testStartPluginListener =
        pluginSysInjector.getInstance(TestStartPluginListener.class);
    TestStopPluginListener testStopPluginListener =
        pluginSysInjector.getInstance(TestStopPluginListener.class);

    InstallPluginInput input = new InstallPluginInput();
    input.raw = pluginContent(TEST_PLUGIN_FILENAME);
    String pluginId = gApi.plugins().install(TEST_PLUGIN_FILENAME, input).get().id;
    assertThat(pluginId).isEqualTo(TEST_PLUGIN);

    assertThat(testStartPluginListener.plugin).isNotNull();
    assertThat(testStartPluginListener.plugin.getName()).isEqualTo(TEST_PLUGIN);

    gApi.plugins().name(TEST_PLUGIN).disable();
    PluginInfo pluginInfo = gApi.plugins().name(TEST_PLUGIN).get();
    assertThat(pluginInfo.id).isEqualTo(TEST_PLUGIN);
    assertThat(pluginInfo.disabled).isTrue();

    assertThat(testStopPluginListener.plugin).isNotNull();
    assertThat(testStopPluginListener.plugin.getName()).isEqualTo(TEST_PLUGIN);
  }
}
