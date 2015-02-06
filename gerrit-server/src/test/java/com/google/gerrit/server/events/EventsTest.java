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

import org.junit.Test;

public class EventsTest {

  public static class TestEvent extends Event {
    public TestEvent() {
      super("test-event");
    }
  }

  public static class AnotherTestEvent extends Event {
    public AnotherTestEvent() {
      super("test-event");
    }
  }

  @Test
  public void testEventRegistration() {
    EventTypes.registerClass(new TestEvent());
    assertThat(EventTypes.getClass("test-event")).isEqualTo(TestEvent.class);
  }

  @Test
  public void testMultipleTypeRegistrationSameClass() {
    EventTypes.registerClass(new TestEvent());
    EventTypes.registerClass(new TestEvent());
    assertThat(EventTypes.getClass("test-event")).isEqualTo(TestEvent.class);
  }

  @Test
  public void testMultipleTypeRegistrationDifferentClasses() {
    EventTypes.registerClass(new TestEvent());
    try {
      EventTypes.registerClass(new AnotherTestEvent());
    } catch (Exception e) {
      // Exception is expected
      assertThat(EventTypes.getClass("test-event")).isEqualTo(TestEvent.class);
    }
    fail("Exception not raised when registering multiple classes with same type");
  }
}
