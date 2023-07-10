// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.extensions.api.config;

import com.google.errorprone.annotations.FormatMethod;
import java.util.List;
import java.util.Objects;

public class ConsistencyCheckInfo {
  public CheckAccountsResultInfo checkAccountsResult;
  public CheckAccountExternalIdsResultInfo checkAccountExternalIdsResult;
  public CheckGroupsResultInfo checkGroupsResult;

  public static class CheckAccountsResultInfo {
    public List<ConsistencyProblemInfo> problems;

    public CheckAccountsResultInfo(List<ConsistencyProblemInfo> problems) {
      this.problems = problems;
    }

    public CheckAccountsResultInfo() {}
  }

  public static class CheckAccountExternalIdsResultInfo {
    public List<ConsistencyProblemInfo> problems;

    public CheckAccountExternalIdsResultInfo(List<ConsistencyProblemInfo> problems) {
      this.problems = problems;
    }
  }

  public static class CheckGroupsResultInfo {
    public List<ConsistencyProblemInfo> problems;

    public CheckGroupsResultInfo(List<ConsistencyProblemInfo> problems) {
      this.problems = problems;
    }

    public CheckGroupsResultInfo() {}
  }

  public static class ConsistencyProblemInfo {
    public enum Status {
      FATAL,
      ERROR,
      WARNING,
    }

    public Status status;
    public String message;

    public ConsistencyProblemInfo(Status status, String message) {
      this.status = status;
      this.message = message;
    }

    public ConsistencyProblemInfo() {}

    @Override
    public boolean equals(Object o) {
      if (o instanceof ConsistencyProblemInfo) {
        ConsistencyProblemInfo other = ((ConsistencyProblemInfo) o);
        return Objects.equals(status, other.status) && Objects.equals(message, other.message);
      }
      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(status, message);
    }

    @Override
    public String toString() {
      return status.name() + ": " + message;
    }

    @FormatMethod
    public static ConsistencyProblemInfo warning(String fmt, Object... args) {
      return new ConsistencyProblemInfo(Status.WARNING, String.format(fmt, args));
    }

    @FormatMethod
    public static ConsistencyProblemInfo error(String fmt, Object... args) {
      return new ConsistencyProblemInfo(Status.ERROR, String.format(fmt, args));
    }
  }
}
