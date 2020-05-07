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

package com.google.gerrit.acceptance.server.event;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.NoHttpd;

import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventDispatcher;
import com.google.gerrit.server.events.EventListener;
import com.google.gerrit.server.events.EventTypes;

import com.google.inject.Inject;
import com.google.inject.Module;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class InstanceIdInEventIT extends AbstractDaemonTest {

  @Override
  public Module createModule() {
    return new FactoryModule() {
      @Override
      public void configure() {
        DynamicSet.bind(binder(), EventListener.class).to(TestEventHandler.class);
      }
    };
  }

  public static class TestDispatcher  {
    private final DynamicItem<EventDispatcher> eventDispatcher;

    @Inject
    TestDispatcher(DynamicItem <EventDispatcher> eventDispatcher) {
      this.eventDispatcher = eventDispatcher;
    }

    public void postEvent(TestEvent event) {
      try {
        eventDispatcher.get().postEvent(event);
      } catch (Exception e) {
        fail("Exception raised when posting Event " + e.getCause());
      }
    }
  }

  public static class TestEventHandler implements EventListener {
    @Override
    public void onEvent(Event event) {
      if (event.type.equals("test-event")) {
        //XXX Add logic

      }
    }
  }

  public static class TestEvent extends Event {
    private static final String TYPE = "test-event-n o-instance-id";

    public TestEvent() {
      super(TYPE);
    }
  }

  @Inject private  DynamicItem<EventDispatcher> eventDispatcher;
  TestDispatcher testDispatcher;

  @Before
  public void setUp() throws Exception {

    testDispatcher = new TestDispatcher(eventDispatcher);
    EventTypes.register(TestEvent.TYPE, TestEvent.class);

  }

  @Test
  @GerritConfig(name = "gerrit.instanceId", value = "testInstanceId")
  public void defaultOptions() throws Exception {
      testDispatcher.postEvent(new TestEvent());
  }
}
