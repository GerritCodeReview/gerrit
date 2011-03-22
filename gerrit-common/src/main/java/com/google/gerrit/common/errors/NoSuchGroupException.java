// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.common.errors;

import com.google.gerrit.reviewdb.AccountGroup;

/** Indicates the account group does not exist. */
public class NoSuchGroupException extends Exception {
  private static final long serialVersionUID = 1L;

  public static final String MESSAGE = "Group Not Found: ";

  public NoSuchGroupException(final AccountGroup.Id key) {
    this(key, null);
  }

  public NoSuchGroupException(final AccountGroup.Id key, final Throwable why) {
    super(MESSAGE + key.toString(), why);
  }

  public NoSuchGroupException(final AccountGroup.NameKey k) {
    this(k, null);
  }

  public NoSuchGroupException(final AccountGroup.NameKey k, final Throwable why) {
    super(MESSAGE + k.toString(), why);
  }

  public NoSuchGroupException(String who) {
    this(who, null);
  }

  public NoSuchGroupException(String who, final Throwable why) {
    super(MESSAGE + who, why);
  }
}
