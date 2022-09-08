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

package com.google.gerrit.server.events;

import static com.google.common.truth.Truth.assertThat;

import java.util.Collection;
import org.junit.Test;

public class EventTypesTest {
  public static class TestEvent extends Event {
    private static final String TYPE = "test-event";

    public TestEvent() {
      super(TYPE);
    }
  }

  public static class AnotherTestEvent extends Event {
    private static final String TYPE = "another-test-event";

    public AnotherTestEvent() {
      super("another-test-event");
    }
  }

  @Test
  public void eventTypeRegistration() {
    EventTypes.register(TestEvent.TYPE, TestEvent.class);
    EventTypes.register(AnotherTestEvent.TYPE, AnotherTestEvent.class);
    assertThat(EventTypes.getClass(TestEvent.TYPE)).isEqualTo(TestEvent.class);
    assertThat(EventTypes.getClass(AnotherTestEvent.TYPE)).isEqualTo(AnotherTestEvent.class);
  }

  @Test
  public void eventTypeRegistrationWithAlternateType() {
    EventTypes.register("alternate-type", TestEvent.class);
    assertThat(EventTypes.getClass("alternate-type")).isEqualTo(TestEvent.class);
    assertThat(EventTypes.getClass(TestEvent.TYPE)).isEqualTo(TestEvent.class);
  }

  @Test
  public void getClassForNonExistingType() {
    Class<?> clazz = EventTypes.getClass("does-not-exist-event");
    assertThat(clazz).isNull();
  }

  @Test
  public void getRegisteredClassesGetsANewlyRegisteredEvent() {
    EventTypes.register(TestEvent.TYPE, TestEvent.class);
    Collection<Class<?>> newlyRegistered = EventTypes.getRegisteredClasses();
    assertThat(TestEvent.class).isIn(newlyRegistered);
  }

  @Test
  public void getRegisteredTypesGetsANewlyRegisteredEventType() {
    EventTypes.register(TestEvent.TYPE, TestEvent.class);
    Collection<String> newlyRegisteredTypes = EventTypes.getRegisteredTypes();
    assertThat(TestEvent.TYPE).isIn(newlyRegisteredTypes);
  }

  @Test
  public void getRegisteredTypesGetsTypeGivenAtRegistration() {
    EventTypes.register("alternate-type", TestEvent.class);
    Collection<String> newlyRegisteredTypes = EventTypes.getRegisteredTypes();
    assertThat("alternate-type").isIn(newlyRegisteredTypes);
    assertThat(TestEvent.TYPE).isIn(newlyRegisteredTypes);
  }
}
