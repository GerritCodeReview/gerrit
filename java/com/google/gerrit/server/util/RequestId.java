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

package com.google.gerrit.server.util;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import java.net.InetAddress;
import java.net.UnknownHostException;

/** Unique identifier for an end-user request, used in logs and similar. */
public class RequestId {
  private static final String MACHINE_ID;

  static {
    String id;
    try {
      id = InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      id = "unknown";
    }
    MACHINE_ID = id;
  }

  public static RequestId forChange(Change c) {
    return new RequestId(c.getId().toString());
  }

  public static RequestId forProject(Project.NameKey p) {
    return new RequestId(p.toString());
  }

  private final String str;

  private RequestId(String resourceId) {
    Hasher h = Hashing.murmur3_128().newHasher();
    h.putLong(Thread.currentThread().getId()).putUnencodedChars(MACHINE_ID);
    str =
        "["
            + resourceId
            + "-"
            + TimeUtil.nowTs().getTime()
            + "-"
            + h.hash().toString().substring(0, 8)
            + "]";
  }

  @Override
  public String toString() {
    return str;
  }

  public String toStringForStorage() {
    return str.substring(1, str.length() - 1);
  }
}
