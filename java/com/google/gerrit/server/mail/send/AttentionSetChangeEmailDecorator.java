// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.mail.send;

import com.google.gerrit.entities.Account;
import com.google.gerrit.server.mail.send.ChangeEmail.ChangeEmailDecorator;

/** Base class for Attention Set email senders */
public interface AttentionSetChangeEmailDecorator extends ChangeEmailDecorator {
  enum AttentionSetChange {
    USER_ADDED,
    USER_REMOVED
  }

  /** User who is being added/removed from attention set. */
  public void setAttentionSetUser(Account.Id attentionSetUser);

  /** Cause of the change in attention set. */
  public void setReason(String reason);

  /** Whether the user is being added or removed. */
  public void setAttentionSetChange(AttentionSetChange attentionSetChange);
}
