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

package com.google.gerrit.pgm.backup;

import com.google.gwtorm.client.Schema;
import com.google.gwtorm.nosql.NoSqlSchema;

public abstract class BackupSchema<T extends Schema> extends NoSqlSchema {
  protected BackupSchema(BackupDatabase<T> d) {
    super(d);
  }

  @Override
  protected long nextLong(String poolName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void close() {
  }
}
