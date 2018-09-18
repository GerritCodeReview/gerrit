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

package com.google.gerrit.server.logging;

import com.google.common.base.Enums;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.server.util.time.TimeUtil;
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

  public enum Type {
    RECEIVE_ID,
    SUBMISSION_ID,
    TRACE_ID;

    static boolean isId(String id) {
      return id != null && Enums.getIfPresent(Type.class, id).isPresent();
    }
  }

  public static boolean isSet() {
    return LoggingContext.getInstance().getTagsAsMap().keySet().stream().anyMatch(Type::isId);
  }

  private final String str;

  public RequestId() {
    this(null);
  }

  public RequestId(@Nullable String resourceId) {
    Hasher h = Hashing.murmur3_128().newHasher();
    h.putLong(Thread.currentThread().getId()).putUnencodedChars(MACHINE_ID);
    str =
        (resourceId != null ? resourceId + "-" : "")
            + TimeUtil.nowTs().getTime()
            + "-"
            + h.hash().toString().substring(0, 8);
  }

  @Override
  public String toString() {
    return str;
  }

  public String toStringForStorage() {
    return str.substring(1, str.length() - 1);
  }
}
