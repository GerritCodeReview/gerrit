// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.plugins;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.systemstatus.ServerInformation;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.internal.UniqueAnnotations;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.verification.VerificationMode;

@RunWith(MockitoJUnitRunner.class)
public class PluginGuiceEnvironmentTest {
  private static final MetricMaker DISABLED_METRIC_MAKER = new DisabledMetricMaker();
  private static final Injector EMPTY_INJECTOR = Guice.createInjector();
  private static final String TEST_PLUGIN_NAME = "testPlugin";
  private static final String TEST_PLUGIN_LISTENER_NAME = "testPluginListener";

  @Mock private ThreadLocalRequestContext requestContextMock;

  @Mock private ServerInformation srvInfoMock;

  @Mock private CopyConfigModule copyConfigModuleMock;

  @Mock private StartPluginListener startPluginListenerMock;

  @Mock private StopPluginListener stopPluginListenerMock;

  @Mock private ReloadPluginListener reloadPluginListenerMock;

  @Mock private StartPluginListener startPluginListenerReloadedMock;

  @Mock private StopPluginListener stopPluginListenerReloadedMock;

  @Mock private ReloadPluginListener reloadPluginListenerReloadedMock;

  @Mock private Plugin pluginMock;

  @Mock private Plugin reloadedPluginMock;

  @Mock private Plugin pluginWithListenersMock;

  @Mock private Plugin pluginWithListenersReloadedMock;

  @Test
  public void shouldAddStartStopReloadListener_GuiceEnvironmentIsCreated() {
    mockPlugin(pluginMock, TEST_PLUGIN_NAME, EMPTY_INJECTOR);
    mockPlugin(reloadedPluginMock, TEST_PLUGIN_NAME, EMPTY_INJECTOR);
    PluginGuiceEnvironment env =
        newPluginGuiceEnvironment(newInjectorWithStartStopReloadListeners());
    verifyStartReloadStopListenersCalled(env);
  }

  @Test
  public void shouldAddStartStopReloadListener_pluginIsStarted() {
    Injector injectorWithListeners = newInjectorWithStartStopReloadListeners();
    mockPlugin(pluginWithListenersMock, TEST_PLUGIN_LISTENER_NAME, injectorWithListeners);
    mockPlugin(pluginMock, TEST_PLUGIN_NAME, EMPTY_INJECTOR);
    mockPlugin(reloadedPluginMock, TEST_PLUGIN_NAME, EMPTY_INJECTOR);

    PluginGuiceEnvironment env = newPluginGuiceEnvironment(EMPTY_INJECTOR);
    env.onStartPlugin(pluginWithListenersMock);
    verifyStartReloadStopListenersCalled(env);
  }

  @Test
  public void shouldRemoveStartStopReloadListener_pluginStartedAndThenStopped() {
    Injector injectorWithListeners = newInjectorWithStartStopReloadListeners();
    mockPlugin(pluginWithListenersMock, TEST_PLUGIN_LISTENER_NAME, injectorWithListeners);
    mockPlugin(pluginMock, TEST_PLUGIN_NAME, EMPTY_INJECTOR);
    mockPlugin(reloadedPluginMock, TEST_PLUGIN_NAME, EMPTY_INJECTOR);

    PluginGuiceEnvironment env = newPluginGuiceEnvironment(EMPTY_INJECTOR);
    env.onStartPlugin(pluginWithListenersMock);
    stopPlugin(env, pluginWithListenersMock);

    verifyStartReloadStopListeners(env, never());
  }

  @Test
  public void shouldRemoveStartStopReloadListener_pluginStartedAndThenReloaded() {
    mockPlugin(
        pluginWithListenersMock,
        TEST_PLUGIN_LISTENER_NAME,
        newInjectorWithStartStopReloadListeners());
    mockPlugin(
        pluginWithListenersReloadedMock,
        TEST_PLUGIN_LISTENER_NAME,
        newInjectorWithStartStopReloadListeners(
            startPluginListenerReloadedMock,
            stopPluginListenerReloadedMock,
            reloadPluginListenerReloadedMock));
    mockPlugin(pluginMock, TEST_PLUGIN_NAME, EMPTY_INJECTOR);
    mockPlugin(reloadedPluginMock, TEST_PLUGIN_NAME, EMPTY_INJECTOR);

    PluginGuiceEnvironment env = newPluginGuiceEnvironment(EMPTY_INJECTOR);
    startPlugin(env, pluginWithListenersMock);
    reloadPlugin(env, pluginWithListenersMock, pluginWithListenersReloadedMock);

    startPlugin(env, pluginMock);
    verify(startPluginListenerMock, never()).onStartPlugin(pluginMock);
    verify(startPluginListenerReloadedMock).onStartPlugin(pluginMock);

    reloadPlugin(env, pluginMock, reloadedPluginMock);
    verify(reloadPluginListenerMock, never()).onReloadPlugin(pluginMock, reloadedPluginMock);
    verify(reloadPluginListenerReloadedMock).onReloadPlugin(pluginMock, reloadedPluginMock);

    stopPlugin(env, pluginMock);
    verify(stopPluginListenerMock, never()).onStopPlugin(pluginMock);
    verify(stopPluginListenerReloadedMock).onStopPlugin(pluginMock);
  }

  private void startPlugin(PluginGuiceEnvironment env, Plugin plugin) {
    env.onStartPlugin(plugin);
  }

  private void reloadPlugin(PluginGuiceEnvironment env, Plugin oldPlugin, Plugin newPlugin) {
    when(oldPlugin.getSysInjector()).thenReturn(null);
    env.onReloadPlugin(oldPlugin, newPlugin);
  }

  private void stopPlugin(PluginGuiceEnvironment env, Plugin plugin) {
    when(plugin.getSysInjector()).thenReturn(null);
    env.onStopPlugin(plugin);
  }

  private void verifyStartReloadStopListenersCalled(PluginGuiceEnvironment env) {
    verifyStartReloadStopListeners(env, times(1));
  }

  private void verifyStartReloadStopListeners(
      PluginGuiceEnvironment env, VerificationMode verificationMode) {
    startPlugin(env, pluginMock);
    verify(startPluginListenerMock, verificationMode).onStartPlugin(pluginMock);
    reloadPlugin(env, pluginMock, reloadedPluginMock);
    verify(reloadPluginListenerMock, verificationMode)
        .onReloadPlugin(pluginMock, reloadedPluginMock);
    stopPlugin(env, pluginMock);
    verify(stopPluginListenerMock, verificationMode).onStopPlugin(pluginMock);
  }

  private void mockPlugin(Plugin pluginMock, String pluginName, Injector sysInjector) {
    when(pluginMock.getName()).thenReturn(pluginName);
    when(pluginMock.getSysInjector()).thenReturn(sysInjector);
  }

  private PluginGuiceEnvironment newPluginGuiceEnvironment(Injector injector) {
    return new PluginGuiceEnvironment(
        injector, requestContextMock, srvInfoMock, copyConfigModuleMock, DISABLED_METRIC_MAKER);
  }

  private Injector newInjectorWithStartStopReloadListeners() {
    return newInjectorWithStartStopReloadListeners(
        startPluginListenerMock, stopPluginListenerMock, reloadPluginListenerMock);
  }

  private Injector newInjectorWithStartStopReloadListeners(
      StartPluginListener startListener,
      StopPluginListener stopListener,
      ReloadPluginListener reloadListener) {
    return Guice.createInjector(
        newModuleWithListener(StartPluginListener.class, startListener),
        newModuleWithListener(StopPluginListener.class, stopListener),
        newModuleWithListener(ReloadPluginListener.class, reloadListener));
  }

  private <T> AbstractModule newModuleWithListener(Class<T> listenerClass, T listener) {
    return new AbstractModule() {
      @Override
      protected void configure() {
        bind(listenerClass).annotatedWith(UniqueAnnotations.create()).toInstance(listener);
      }
    };
  }
}
