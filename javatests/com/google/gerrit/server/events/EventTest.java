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

package com.google.gerrit.server.events;

import static com.google.common.truth.Truth.assertThat;

import java.util.Objects;
import org.junit.Test;

public class EventTest {
  private static final String INSTANCE_ID = "instance-id";
  private static final String NO_INSTANCE_ID = null;
  private static final String TYPE = "test-event";
  private static final long CREATED_ON = 1000L;

  private static class TestEvent extends Event {
    private TestEvent() {
      super(TYPE);
    }
  }

  private static class OtherTestEvent extends Event {
    private OtherTestEvent() {
      super("other-test-event");
    }
  }

  private static class DerivedEvent extends Event {
    public int derivedEventField;

    private DerivedEvent(int derivedEventField) {
      super(TYPE);
      this.derivedEventField = derivedEventField;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof DerivedEvent that)) {
        return false;
      }
      return super.equals(o) && derivedEventField == that.derivedEventField;
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), derivedEventField);
    }
  }

  @Test
  public void eventsWithSameClassAndBaseFieldsAreEqual() {
    Event event = newTestEvent(CREATED_ON, INSTANCE_ID);
    Event sameEvent = newTestEvent(CREATED_ON, INSTANCE_ID);

    assertThat(event).isEqualTo(sameEvent);
    assertThat(event.hashCode()).isEqualTo(sameEvent.hashCode());
  }

  @Test
  public void eventsWithSameClassAndNoInstanceIdAreEqual() {
    Event event = newTestEvent(CREATED_ON, NO_INSTANCE_ID);
    Event sameEvent = newTestEvent(CREATED_ON, NO_INSTANCE_ID);

    assertThat(event).isEqualTo(sameEvent);
    assertThat(event.hashCode()).isEqualTo(sameEvent.hashCode());
  }

  @Test
  public void eventsWithDifferentEventCreatedOnAreNotEqual() {
    Event event = newTestEvent(CREATED_ON, INSTANCE_ID);
    Event eventWithDifferentTimestamp = newTestEvent(CREATED_ON + 1, INSTANCE_ID);

    assertThat(event).isNotEqualTo(eventWithDifferentTimestamp);
  }

  @Test
  public void eventsWithDifferentInstanceIdAreNotEqual() {
    Event event = newTestEvent(CREATED_ON, INSTANCE_ID);
    Event eventWithDifferentInstanceId = newTestEvent(CREATED_ON, "other-instance-id");

    assertThat(event).isNotEqualTo(eventWithDifferentInstanceId);
  }

  @Test
  public void derivedEventOverridesEquals() {
    DerivedEvent event = new DerivedEvent(1);
    DerivedEvent sameEvent = new DerivedEvent(1);
    DerivedEvent eventWithDifferentDerivedField = new DerivedEvent(2);

    assertThat(event).isEqualTo(sameEvent);
    assertThat(event.hashCode()).isEqualTo(sameEvent.hashCode());

    assertThat(event).isNotEqualTo(eventWithDifferentDerivedField);
    assertThat(event.hashCode()).isNotEqualTo(eventWithDifferentDerivedField.hashCode());
  }

  @Test
  public void eventsWithDifferentTypesAreNotEqual() {
    Event event = newTestEvent(CREATED_ON, INSTANCE_ID);
    Event eventWithDifferentType = new OtherTestEvent();
    eventWithDifferentType.eventCreatedOn = event.eventCreatedOn;
    eventWithDifferentType.instanceId = event.instanceId;

    assertThat(event).isNotEqualTo(eventWithDifferentType);
  }

  private static Event newTestEvent(long eventCreatedOn, String instanceId) {
    Event event = new TestEvent();
    event.eventCreatedOn = eventCreatedOn;
    event.instanceId = instanceId;
    return event;
  }
}
