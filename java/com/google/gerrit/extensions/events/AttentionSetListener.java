// Copyright (C) 2022 The Android Open Source Project
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
import java.util.Set;

/** Notified whenever the attention set is changed. */
@ExtensionPoint
public interface AttentionSetListener {
  interface Event extends ChangeEvent {

    /**
     * Returns the users added to the attention set because of this change
     *
     * @return Account IDs
     */
    Set<Integer> usersAdded();

    /**
     * Returns the users removed from the attention set because of this change
     *
     * @return Account IDs
     */
    Set<Integer> usersRemoved();
  }

  /**
   * This function will be called when the attention set changes
   *
   * @param event The event that changed the attention set
   */
  void onAttentionSetChanged(Event event);
}
