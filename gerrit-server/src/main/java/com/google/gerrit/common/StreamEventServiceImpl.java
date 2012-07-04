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

package com.google.gerrit.common;

import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.events.StreamEvent;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class StreamEventServiceImpl implements StreamEventService {

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(StreamEventServiceImpl.class);
      bind(StreamEventService.class).to(StreamEventServiceImpl.class);
    }
  }

  private static class StreamEventListenerHolder {
    final StreamEventListener listener;
    final IdentifiedUser user;

    StreamEventListenerHolder(StreamEventListener l, IdentifiedUser u) {
      listener = l;
      user = u;
    }
  }

  /** Listeners to receive changes as they happen. */
  private final Map<StreamEventListener, StreamEventListenerHolder> listeners =
      new ConcurrentHashMap<StreamEventListener, StreamEventListenerHolder>();

  @Override
  public void addListener(StreamEventListener listener, IdentifiedUser user) {
    listeners.put(listener, new StreamEventListenerHolder(listener, user));
  }

  @Override
  public void removeListener(StreamEventListener listener) {
    listeners.remove(listener);
  }

  @Override
  public void fireEvent(StreamEvent event, StreamEventAuth auth) {
    for (StreamEventListenerHolder holder : listeners.values()) {
      if (auth.isVisibleTo(holder.user)) {
        holder.listener.onStreamEvent(event);
      }
    }
  }
}
