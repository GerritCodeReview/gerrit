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

package com.google.gerrit.extensions.events;

import com.google.gerrit.extensions.annotations.ExtensionPoint;

/** Notified whenever a change is indexed or deleted from the index. */
@ExtensionPoint
public interface ChangeIndexedListener {
  public interface Event {
    int getChangeId();
  }

  /** Invoked when a change is indexed. */
  void onChangeIndexed(Event event);

  /** Invoked when a change is deleted from the index. */
  void onChangeDeleted(Event event);
}
