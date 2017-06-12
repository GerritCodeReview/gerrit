// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import java.util.List;

public class GpgKeyInfo {
  /**
   * Status of checking an object like a key or signature.
   *
   * <p>Order of values in this enum is significant: OK is "better" than BAD, etc.
   */
  public enum Status {
    /** Something is wrong with this key. */
    BAD,

    /**
     * Inspecting only this key found no problems, but the system does not fully trust the key's
     * origin.
     */
    OK,

    /** This key is valid, and the system knows enough about the key and its origin to trust it. */
    TRUSTED;
  }

  public String id;
  public String fingerprint;
  public List<String> userIds;
  public String key;

  public Status status;
  public List<String> problems;
}
