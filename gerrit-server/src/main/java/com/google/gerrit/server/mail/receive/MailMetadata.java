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

import java.sql.Timestamp;

/** MailMetadata represents metadata parsed from inbound email. */
public class MailMetadata {
  public String changeId;
  public Integer patchSet;
  public String author; // Author of the email
  public Timestamp timestamp;
  public String messageType; // we expect comment here


  public boolean hasRequiredFields() {
    return changeId != null && patchSet != null &&
        author != null && timestamp != null;
  }

  @Override
  public String toString() {
    return changeId + patchSet + author + timestamp + messageType;
  }
}
