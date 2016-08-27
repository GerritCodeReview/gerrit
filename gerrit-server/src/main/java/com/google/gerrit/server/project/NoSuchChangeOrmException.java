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

package com.google.gerrit.server.project;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gwtorm.server.OrmException;

/**
 * Indicates the change does not exist.
 * <p>
 * Similar to {@code NoSuchChangeException}, except this one inherited from
 * {@code OrmException}.
 */
public class NoSuchChangeOrmException extends OrmException {
  private static final long serialVersionUID = 1L;

  public NoSuchChangeOrmException(Change.Id key) {
    this(key, null);
  }

  public NoSuchChangeOrmException(Throwable why) {
    super(why);
  }

  public NoSuchChangeOrmException(Change.Id key, Throwable why) {
    super(key.toString(), why);
  }
}
