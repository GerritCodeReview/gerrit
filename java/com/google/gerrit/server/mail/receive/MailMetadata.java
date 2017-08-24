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

package com.google.gerrit.server.mail.receive;

import com.google.common.base.MoreObjects;
import java.sql.Timestamp;

/** MailMetadata represents metadata parsed from inbound email. */
public class MailMetadata {
  public Integer changeNumber;
  public Integer patchSet;
  public String author; // Author of the email
  public Timestamp timestamp;
  public String messageType; // we expect comment here

  public boolean hasRequiredFields() {
    return changeNumber != null
        && patchSet != null
        && author != null
        && timestamp != null
        && messageType != null;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("Change-Number", changeNumber)
        .add("Patch-Set", patchSet)
        .add("Author", author)
        .add("Timestamp", timestamp)
        .add("Message-Type", messageType)
        .toString();
  }
}
