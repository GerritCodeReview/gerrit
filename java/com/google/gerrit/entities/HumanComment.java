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

package com.google.gerrit.entities;

import java.sql.Timestamp;

public class HumanComment extends Comment {
  public String parentUuid;

  public HumanComment(
      Key key,
      Account.Id author,
      Timestamp writtenOn,
      short side,
      String message,
      String serverId,
      boolean unresolved) {
    super(key, author, writtenOn, side, message, serverId, unresolved);
  }

  public HumanComment(HumanComment comment) {
    super(comment);
  }

  @Override
  public String toString() {
    return toStringHelper().add("parentUuid", parentUuid).toString();
  }
}
