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

/** Provides access to system stream events service. */
public interface StreamEventService {
  /** Add a listener for receiving stream events. */
  void addListener(StreamEventListener listener, IdentifiedUser user);

  /** Remove the registered listener. */
  void removeListener(StreamEventListener listener);

  /** Send a stream event to the registered listeners. */
  void fireEvent(StreamEvent event, StreamEventAuth auth);
}
