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

package com.google.gerrit.server;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.PatchSetAccess;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

public class PSU {
  public static final AtomicDouble FREQUENCY = new AtomicDouble(1.0);

  public static PatchSet get(PatchSetAccess access, PatchSet.Id id)
      throws OrmException {
    if (Math.random() < FREQUENCY.get()) {
      new Exception("Get " + id).printStackTrace();
    }
    return access.get(id);
  }

  public static ResultSet<PatchSet> get(PatchSetAccess access,
      Iterable<PatchSet.Id> ids) throws OrmException {
    if (Math.random() < FREQUENCY.get()) {
      new Exception("Get " + ids).printStackTrace();
    }
    return access.get(ids);
  }

  private PSU() {
  }
}
