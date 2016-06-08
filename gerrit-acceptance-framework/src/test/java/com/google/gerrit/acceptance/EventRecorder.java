// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import static com.google.common.truth.Truth.assertThat;
import static org.eclipse.jgit.lib.Constants.R_HEADS;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import com.google.gerrit.common.UserScopedEventListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.ChangeMergedEvent;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.events.RefEvent;
import com.google.gerrit.server.events.RefUpdatedEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Collection;
import java.util.List;

public class EventRecorder {
  private final RegistrationHandle eventListenerRegistration;
  private final Multimap<String, RefEvent> recordedEvents;

  @Singleton
  public static class Factory {
    private final DynamicSet<UserScopedEventListener> eventListeners;
    private final IdentifiedUser.GenericFactory userFactory;

    @Inject
    Factory(DynamicSet<UserScopedEventListener> eventListeners,
        IdentifiedUser.GenericFactory userFactory) {
      this.eventListeners = eventListeners;
      this.userFactory = userFactory;
    }

    public EventRecorder create(TestAccount user) {
      return new EventRecorder(eventListeners, userFactory.create(user.id));
    }
  }

  public EventRecorder(DynamicSet<UserScopedEventListener> eventListeners,
      final IdentifiedUser user) {
    recordedEvents = LinkedListMultimap.create();

    eventListenerRegistration = eventListeners.add(
        new UserScopedEventListener() {
          @Override
          public void onEvent(Event e) {
            if (e instanceof RefEvent) {
              RefEvent event = (RefEvent) e;
              String key = key(event.getType(), event.getProjectNameKey().get(),
                  event.getRefName());
              recordedEvents.put(key, event);
            }
          }

          @Override
          public CurrentUser getUser() {
            return user;
          }
        });
  }

  private static String key(String type, String project, String ref) {
    return String.format("%s-%s-%s", type, project, ref);
  }

  public RefUpdatedEvent getOneRefUpdate(String project, String refName) {
    String key = key(RefUpdatedEvent.TYPE, project, refName);
    assertThat(recordedEvents).containsKey(key);
    Collection<RefEvent> events = recordedEvents.get(key);
    assertThat(events).hasSize(1);
    Event e = events.iterator().next();
    assertThat(e).isInstanceOf(RefUpdatedEvent.class);
    return (RefUpdatedEvent) e;
  }

  public ImmutableList<RefEvent> getRefUpdates(String project, String refName,
      int expectedSize) {
    String key = key(RefUpdatedEvent.TYPE, project, refName);
    assertThat(recordedEvents).containsKey(key);
    Collection<RefEvent> events = recordedEvents.get(key);
    assertThat(events).hasSize(expectedSize);
    return ImmutableList.copyOf(events);
  }

  public ChangeMergedEvent getOneChangeMerged(String project, String branch,
      final String changeNumber) throws Exception {
    String key = key(ChangeMergedEvent.TYPE, project,
        branch.startsWith(R_HEADS) ? branch : R_HEADS + branch);
    assertThat(recordedEvents).containsKey(key);
    List<RefEvent> events = FluentIterable
        .from(recordedEvents.get(key))
        .filter(new Predicate<RefEvent>() {
          @Override
          public boolean apply(RefEvent input) {
            assertThat(input).isInstanceOf(ChangeMergedEvent.class);
            ChangeMergedEvent e = (ChangeMergedEvent) input;
            return e.change.get().number.equals(changeNumber);
          }})
        .toList();
    assertThat(events).hasSize(1);
    return (ChangeMergedEvent) events.get(0);
  }

  public void close() {
    eventListenerRegistration.remove();
  }
}