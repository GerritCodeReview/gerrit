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
import java.util.Collection;

/** Notified whenever a Change's Hashtags are edited. */
@ExtensionPoint
public interface HashtagsEditedListener {
  interface Event extends ChangeEvent {
    Collection<String> getHashtags();

    Collection<String> getAddedHashtags();

    Collection<String> getRemovedHashtags();
  }

  void onHashtagsEdited(Event event);
}
