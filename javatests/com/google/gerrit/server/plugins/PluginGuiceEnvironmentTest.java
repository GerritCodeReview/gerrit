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

@RunWith(MockitoJUnitRunner.class)
public class PluginGuiceEnvironmentTest {
  private static final MetricMaker DISABLED_METRIC_MAKER = new DisabledMetricMaker();
  private static final Injector EMPTY_INJECTOR = Guice.createInjector();

  @Mock private ThreadLocalRequestContext requestContextMock;

  @Mock private ServerInformation srvInfoMock;

  @Mock private CopyConfigModule copyConfigModuleMock;

  @Mock private StartPluginListener startPluginListenerMock;

  @Mock private StopPluginListener stopPluginListenerMock;

  @Mock private ReloadPluginListener reloadPluginListenerMock;

  @Mock private Plugin pluginMock;

  @Mock private Plugin reloadedPluginMock;

  @Mock private Plugin pluginWithListenersMock;

  @Test
  public void shouldAddStartStopReloadListener_GuiceEnvironmentIsCreated() {
    mockPluginSysInjector(pluginMock, EMPTY_INJECTOR);
    mockPluginSysInjector(reloadedPluginMock, EMPTY_INJECTOR);
    PluginGuiceEnvironment env =
        newPluginGuiceEnvironment(newInjectorWithStartStopReloadListeners());
    verifyStartReloadStopListenersCalled(env);
  }

  @Test
  public void shouldAddStartStopReloadListener_pluginIsStarted() {
    Injector injectorWithListeners = newInjectorWithStartStopReloadListeners();
    mockPluginSysInjector(pluginWithListenersMock, injectorWithListeners);
    mockPluginSysInjector(pluginMock, EMPTY_INJECTOR);
    mockPluginSysInjector(reloadedPluginMock, EMPTY_INJECTOR);

    PluginGuiceEnvironment env = newPluginGuiceEnvironment(EMPTY_INJECTOR);
    env.onStartPlugin(pluginWithListenersMock);
    verifyStartReloadStopListenersCalled(env);
  }

  private void verifyStartReloadStopListenersCalled(PluginGuiceEnvironment env) {
    env.onStartPlugin(pluginMock);
    verify(startPluginListenerMock).onStartPlugin(pluginMock);
    env.onReloadPlugin(pluginMock, reloadedPluginMock);
    verify(reloadPluginListenerMock).onReloadPlugin(pluginMock, reloadedPluginMock);
    env.onStopPlugin(pluginMock);
    verify(stopPluginListenerMock).onStopPlugin(pluginMock);
  }

  private void mockPluginSysInjector(Plugin pluginMock, Injector sysInjector) {
    when(pluginMock.getSysInjector()).thenReturn(sysInjector);
  }

  private PluginGuiceEnvironment newPluginGuiceEnvironment(Injector injector) {
    return new PluginGuiceEnvironment(
        injector, requestContextMock, srvInfoMock, copyConfigModuleMock, DISABLED_METRIC_MAKER);
  }

  private Injector newInjectorWithStartStopReloadListeners() {
    return Guice.createInjector(
        newModuleWithListener(StartPluginListener.class, startPluginListenerMock),
        newModuleWithListener(StopPluginListener.class, stopPluginListenerMock),
        newModuleWithListener(ReloadPluginListener.class, reloadPluginListenerMock));
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
