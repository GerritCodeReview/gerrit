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

package com.google.gerrit.server.project;

import com.google.gerrit.entities.Change;
import com.google.gerrit.exceptions.StorageException;

/** Indicates the change does not exist. */
public class NoSuchChangeException extends StorageException {
  private static final long serialVersionUID = 1L;

  public NoSuchChangeException(Change.Id key) {
    this(key, null);
  }

  public NoSuchChangeException(Change.Id key, Throwable why) {
    super(key.toString(), why);
  }
}
