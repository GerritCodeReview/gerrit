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

package com.google.gerrit.entities;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class RobotComment extends Comment {
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
  public int getApproximateSize() {
    int approximateSize = super.getApproximateSize() + nullableLength(robotId, robotRunId, url);
    approximateSize +=
        properties != null
            ? properties.entrySet().stream()
                .map(entry -> nullableLength(entry.getKey(), entry.getValue()))
                .reduce(0, Integer::sum)
            : 0;
    approximateSize +=
        fixSuggestions != null
            ? fixSuggestions.stream().map(FixSuggestion::getApproximateSize).reduce(0, Integer::sum)
            : 0;
    return approximateSize;
  }

  @Override
  public String toString() {
    return toStringHelper()
        .add("robotId", robotId)
        .add("robotRunId", robotRunId)
        .add("url", url)
        .add("properties", Objects.toString(properties, ""))
        .add("fixSuggestions", Objects.toString(fixSuggestions, ""))
        .toString();
  }
}
