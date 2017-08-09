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

package com.google.gerrit.server.schema;

import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.StatementExecutor;
import java.util.List;
import java.util.Set;

public interface UpdateUI {
  void message(String msg);

  boolean yesno(boolean def, String msg);

  void waitForUser();

  String readString(String def, Set<String> allowedValues, String fmt);

  boolean isBatch();

  void pruneSchema(StatementExecutor e, List<String> pruneList) throws OrmException;
}
