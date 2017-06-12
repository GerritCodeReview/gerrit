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

package com.google.gerrit.server.extensions.events;

import com.google.gerrit.extensions.events.PluginEventListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.inject.Inject;

public class PluginEvent {
  private final DynamicSet<PluginEventListener> listeners;

  @Inject
  PluginEvent(DynamicSet<PluginEventListener> listeners) {
    this.listeners = listeners;
  }

  public void fire(String pluginName, String type, String data) {
    if (!listeners.iterator().hasNext()) {
      return;
    }
    Event e = new Event(pluginName, type, data);
    for (PluginEventListener l : listeners) {
      l.onPluginEvent(e);
    }
  }

  private static class Event extends AbstractNoNotifyEvent implements PluginEventListener.Event {
    private final String pluginName;
    private final String type;
    private final String data;

    Event(String pluginName, String type, String data) {
      this.pluginName = pluginName;
      this.type = type;
      this.data = data;
    }

    @Override
    public String pluginName() {
      return pluginName;
    }

    @Override
    public String getType() {
      return type;
    }

    @Override
    public String getData() {
      return data;
    }
  }
}
