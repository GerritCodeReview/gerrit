// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.acceptance.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.LightweightPluginDaemonTest;
import com.google.gerrit.acceptance.TestPlugin;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventDispatcher;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Scopes;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

@TestPlugin(
    name = "instance-id-from-plugin",
    sysModule = "com.google.gerrit.acceptance.config.InstanceIdFromPluginIT$TestModule")
public class InstanceIdFromPluginIT extends LightweightPluginDaemonTest {

  public static class TestModule extends AbstractModule {

    @Override
    protected void configure() {
      bind(InstanceIdLoader.class).in(Scopes.SINGLETON);
      bind(TestEventListener.class).in(Scopes.SINGLETON);
      DynamicSet.bind(binder(), EventListener.class).to(TestEventListener.class);
    }
  }

  public static class InstanceIdLoader {
    public final String gerritInstanceId;

    @Inject
    InstanceIdLoader(@Nullable @GerritInstanceId String gerritInstanceId) {
      this.gerritInstanceId = gerritInstanceId;
    }
  }

  public static class TestEventListener implements EventListener {
    private final List<Event> events = new ArrayList<>();

    @Override
    public void onEvent(Event event) {
      events.add(event);
    }

    public List<Event> getEvents() {
      return events;
    }
  }

  public static class TestEvent extends Event {

    protected TestEvent(String instanceId) {
      super("test");
      this.instanceId = instanceId;
    }
  }

  @Test
  @GerritConfig(name = "gerrit.instanceId", value = "testInstanceId")
  public void shouldReturnInstanceIdWhenDefined() {
    assertThat(getInstanceIdLoader().gerritInstanceId).isEqualTo("testInstanceId");
  }

  @Test
  public void shouldReturnNullWhenNotDefined() {
    assertThat(getInstanceIdLoader().gerritInstanceId).isNull();
  }

  @Test
  @GerritConfig(name = "gerrit.instanceId", value = "testInstanceId")
  public void shouldPreserveEventInstanceIdWhenDefined() throws PermissionBackendException {
    EventDispatcher dispatcher =
        plugin.getSysInjector().getInstance(new Key<DynamicItem<EventDispatcher>>() {}).get();
    String eventInstanceId = "eventInstanceId";
    TestEventListener eventListener = plugin.getSysInjector().getInstance(TestEventListener.class);
    TestEvent testEvent = new TestEvent(eventInstanceId);

    dispatcher.postEvent(testEvent);
    List<Event> receivedEvents = eventListener.getEvents();
    assertThat(receivedEvents).hasSize(1);
    assertThat(receivedEvents.get(0).instanceId).isEqualTo(eventInstanceId);
  }

  private InstanceIdLoader getInstanceIdLoader() {
    return plugin.getSysInjector().getInstance(InstanceIdLoader.class);
  }
}
