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
  static final String TEST_PLUGIN = "test-plugin";
  static final String TEST_PLUGIN_FILENAME = TEST_PLUGIN + ".jar";

  @Singleton
  public static class TestStartPluginListener implements StartPluginListener {
    public volatile Plugin plugin;
    public volatile Injector pluginInjector;

    @Override
    public void onStartPlugin(Plugin plugin) {
      if (plugin.getName().equals(TEST_PLUGIN)) {
        this.plugin = plugin;
        this.pluginInjector = plugin.getSysInjector();
      }
    }
  }

  @Singleton
  public static class TestStopPluginListener implements StopPluginListener {
    public volatile Plugin plugin;
    public volatile Injector pluginInjector;

    @Override
    public void beforeStopPlugin(Plugin plugin) {
      if (plugin.getName().equals(TEST_PLUGIN)) {
        this.pluginInjector = plugin.getSysInjector();
      }
    }

    @Override
    public void onStopPlugin(Plugin plugin) {
      if (plugin.getName().equals(TEST_PLUGIN)) {
        this.plugin = plugin;
      }
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
    assertThat(testStartPluginListener.pluginInjector).isNotNull();

    gApi.plugins().name(TEST_PLUGIN).disable();
    PluginInfo pluginInfo = gApi.plugins().name(TEST_PLUGIN).get();
    assertThat(pluginInfo.id).isEqualTo(TEST_PLUGIN);
    assertThat(pluginInfo.disabled).isTrue();

<<<<<<< PATCH SET (074956467fa18c56718cfa008c40aa856c626a6a Invoke StopPluginListener also before the plugin stop)
    assertThat(testStopPluginListener.plugin).isNotNull();
||||||| BASE      (98f6156a71edb47631f4885e995517ac2c78cd5b Add integration test for Start/StopPluginListener)
=======
    assertThat(testStopPluginListener.plugin).isNotNull());
>>>>>>> BASE      (694c36488e8815baa12ad752a1fe93c21e2950ca Add integration test for Start/StopPluginListener)
    assertThat(testStopPluginListener.plugin.getName()).isEqualTo(TEST_PLUGIN);
    assertThat(testStopPluginListener.pluginInjector).isNotNull();
  }
}
