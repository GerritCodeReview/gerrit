// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.gerrit.server.UsedAt;

public class JdbcUtil {

  public static String hostname(String hostname) {
    if (hostname == null || hostname.isEmpty()) {
      hostname = "localhost";

    } else if (hostname.contains(":") && !hostname.startsWith("[")) {
      hostname = "[" + hostname + "]";
    }
    return hostname;
  }

  @UsedAt(UsedAt.Project.PLUGINS_ALL)
  public static String port(String port) {
    if (port != null && !port.isEmpty()) {
      return ":" + port;
    }
    return "";
  }
}
