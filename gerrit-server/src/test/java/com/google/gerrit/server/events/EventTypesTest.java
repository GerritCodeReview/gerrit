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
import static org.junit.Assert.fail;

import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.EventTypes;

import org.junit.Test;

public class EventTypesTest {
  public static class TestEvent extends Event {
    public TestEvent() {
      super("test-event");
    }
  }

  public static class TestEvent2 extends Event {
    public TestEvent2() {
      super("test-event"); // Intentionally same as in TestEvent
    }
  }

  public static class AnotherTestEvent extends Event {
    public AnotherTestEvent() {
      super("another-test-event");
    }
  }

  @Test
  public void testEventTypeRegistration() {
    EventTypes.registerClass(new TestEvent());
    EventTypes.registerClass(new AnotherTestEvent());
    assertThat(EventTypes.getClass("test-event")).isEqualTo(TestEvent.class);
    assertThat(EventTypes.getClass("another-test-event"))
      .isEqualTo(AnotherTestEvent.class);

    try {
      EventTypes.registerClass(new TestEvent());
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertThat(EventTypes.getClass("test-event")).isEqualTo(TestEvent.class);
    }

    try {
      EventTypes.registerClass(new TestEvent2());
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      assertThat(EventTypes.getClass("test-event")).isEqualTo(TestEvent.class);
    }
  }

  @Test
  public void testGetClassForNonExistingType() {
    Class<?> clazz = EventTypes.getClass("does-not-exist-event");
    assertThat(clazz).isNull();
  }
}
