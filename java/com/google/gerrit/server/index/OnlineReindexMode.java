// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.index;

import java.util.Optional;

public class OnlineReindexMode {
  private static ThreadLocal<Boolean> isOnlineReindex = new ThreadLocal<>();

  public static boolean get() {
    return Optional.ofNullable(isOnlineReindex.get()).orElse(Boolean.FALSE);
  }

  public static void begin() {
    isOnlineReindex.set(Boolean.TRUE);
  }

  public static void end() {
    isOnlineReindex.set(Boolean.FALSE);
  }
}
