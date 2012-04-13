// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.audit;

import com.google.common.collect.Lists;
import com.google.gerrit.server.plugins.Plugin;
import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.plugins.RemovePluginListener;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Singleton
public class AuditService implements StartPluginListener, ReloadPluginListener, RemovePluginListener {

  private List<AuditListener> auditListeners =
      new CopyOnWriteArrayList<AuditListener>();


  @Inject
  public AuditService(Injector sysInjector) {
    loadAuditListeners(sysInjector);
  }

  private static <T> List<T> listeners(Injector src, Class<T> type) {
    List<Binding<T>> bindings = src.findBindingsByType(TypeLiteral.get(type));
    List<T> found = Lists.newArrayListWithCapacity(bindings.size());
    for (Binding<T> b : bindings) {
      found.add(b.getProvider().get());
    }
    return found;
  }

  public void track(AuditEvent action) {
    for (AuditListener auditListener : auditListeners) {
      auditListener.track(action);
    }
  }

  @Override
  public void onReloadPlugin(Plugin oldPlugin, Plugin newPlugin) {
    unloadAuditListeners(oldPlugin.getSysInjector());
    loadAuditListeners(newPlugin.getSysInjector());
  }

  @Override
  public void onStartPlugin(Plugin plugin) {
    loadAuditListeners(plugin.getSysInjector());
  }

  @Override
  public void onRemovePlugin(Plugin plugin) {
    unloadAuditListeners(plugin.getSysInjector());
  }

  private void unloadAuditListeners(Injector injector) {
    List<AuditListener> listeners = listeners(injector, AuditListener.class);
    System.out.println("Removing listeners: " + listeners);
    auditListeners.removeAll(listeners);
  }

  private void loadAuditListeners(Injector injector) {
    List<AuditListener> listeners = listeners(injector, AuditListener.class);
    System.out.println("Adding listeners: " + listeners);
    auditListeners.addAll(listeners);
  }


}
