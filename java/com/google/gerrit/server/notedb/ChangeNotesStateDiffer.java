// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import java.util.Map;

/** Gets the diff between two {@link ChangeNotesState} instances. */
public final class ChangeNotesStateDiffer {

  /**
   * Returns the diff between two instances of {@link ChangeNotesState}.
   *
   * @param oldState the previous state to diff against {@code newState}
   * @param newState the state to diff against {@code oldState}
   * @return the diff between the given states
   */
  public ChangeNotesStateDiff getDiff(ChangeNotesState oldState, ChangeNotesState newState) {
    checkNotNull(oldState);
    checkNotNull(newState);
    return ChangeNotesStateDiff.builder().setOldTopic(oldState.columns().topic())
        .setNewTopic(newState.columns().topic())
        .setAddedMessages(getAddedMessages(oldState, newState))
        .setAddedPatchSetApprovals(getAddedApprovals(oldState, newState))
        .setRemovedPatchSetApprovals(getAddedApprovals(newState, oldState)).build();
  }

  private static ImmutableList<ChangeMessage> getAddedMessages(ChangeNotesState oldState,
      ChangeNotesState newState) {
    ImmutableSet<ChangeMessage.Key> oldMessageKeys =
        oldState.changeMessages().stream().map(ChangeMessage::getKey).collect(toImmutableSet());
    return newState.changeMessages().stream().filter(m -> !oldMessageKeys.contains(m.getKey()))
        .collect(toImmutableList());
  }

  private static ImmutableList<Map.Entry<PatchSet.Id, PatchSetApproval>> getAddedApprovals(
      ChangeNotesState state1, ChangeNotesState state2) {
    ImmutableSet<Map.Entry<PatchSet.Id, PatchSetApproval>> newApprovals =
        ImmutableSet.copyOf(state1.approvals());
    return state2.approvals().stream().filter(a -> !newApprovals.contains(a))
        .collect(toImmutableList());
  }
}
