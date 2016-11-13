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

package com.google.gerrit.extensions.events;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import com.google.gerrit.extensions.common.ApprovalInfo;
import java.util.Map;

/** Notified whenever a vote is removed from a change. */
@ExtensionPoint
public interface VoteDeletedListener {
  interface Event extends RevisionEvent {
    Map<String, ApprovalInfo> getOldApprovals();

    Map<String, ApprovalInfo> getApprovals();

    Map<String, ApprovalInfo> getRemoved();

    String getMessage();
  }

  void onVoteDeleted(Event event);
}
