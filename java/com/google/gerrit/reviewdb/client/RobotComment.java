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

package com.google.gerrit.reviewdb.client;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RobotComment extends Comment {
  public String robotId;
  public String robotRunId;
  public String url;
  public Map<String, String> properties;
  public List<FixSuggestion> fixSuggestions;

  public RobotComment(
      Key key,
      Account.Id author,
      Timestamp writtenOn,
      short side,
      String message,
      String serverId,
      String robotId,
      String robotRunId) {
    super(key, author, writtenOn, side, message, serverId, false);
    this.robotId = robotId;
    this.robotRunId = robotRunId;
  }

  @Override
  public String toString() {
    return new StringBuilder()
        .append("RobotComment{")
        .append("key=")
        .append(key)
        .append(',')
        .append("robotId=")
        .append(robotId)
        .append(',')
        .append("robotRunId=")
        .append(robotRunId)
        .append(',')
        .append("lineNbr=")
        .append(lineNbr)
        .append(',')
        .append("author=")
        .append(author.getId().get())
        .append(',')
        .append("realAuthor=")
        .append(realAuthor != null ? realAuthor.getId().get() : "")
        .append(',')
        .append("writtenOn=")
        .append(writtenOn.toString())
        .append(',')
        .append("side=")
        .append(side)
        .append(',')
        .append("message=")
        .append(Objects.toString(message, ""))
        .append(',')
        .append("parentUuid=")
        .append(Objects.toString(parentUuid, ""))
        .append(',')
        .append("range=")
        .append(Objects.toString(range, ""))
        .append(',')
        .append("revId=")
        .append(revId != null ? revId : "")
        .append(',')
        .append("tag=")
        .append(Objects.toString(tag, ""))
        .append(',')
        .append("unresolved=")
        .append(unresolved)
        .append(',')
        .append("url=")
        .append(url)
        .append(',')
        .append("properties=")
        .append(properties != null ? properties : "")
        .append("fixSuggestions=")
        .append(fixSuggestions != null ? fixSuggestions : "")
        .append('}')
        .toString();
  }
}
