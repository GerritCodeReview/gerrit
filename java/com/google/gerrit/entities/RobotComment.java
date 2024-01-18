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

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@Deprecated
public final class RobotComment extends Comment {
  public String robotId;
  public String robotRunId;
  public String url;
  public Map<String, String> properties;

  public RobotComment(
      Key key,
      Account.Id author,
      Instant writtenOn,
      short side,
      String message,
      String serverId,
      String robotId,
      String robotRunId) {
    super(key, author, writtenOn, side, message, serverId);
    this.robotId = robotId;
    this.robotRunId = robotRunId;
  }

  @Override
  public int getApproximateSize() {
    int approximateSize =
        super.getCommentFieldApproximateSize() + nullableLength(robotId, robotRunId, url);
    approximateSize +=
        properties != null
            ? properties.entrySet().stream()
                .mapToInt(entry -> nullableLength(entry.getKey(), entry.getValue()))
                .sum()
            : 0;
    approximateSize +=
        fixSuggestions != null
            ? fixSuggestions.stream().mapToInt(FixSuggestion::getApproximateSize).sum()
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
        .toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof RobotComment)) {
      return false;
    }
    RobotComment c = (RobotComment) o;
    return super.equals(o)
        && Objects.equals(robotId, c.robotId)
        && Objects.equals(robotRunId, c.robotRunId)
        && Objects.equals(url, c.url)
        && Objects.equals(properties, c.properties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), robotId, robotRunId, url, properties);
  }
}
