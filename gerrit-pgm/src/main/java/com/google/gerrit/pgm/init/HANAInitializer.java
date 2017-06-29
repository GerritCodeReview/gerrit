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

package com.google.gerrit.pgm.init;

import static com.google.gerrit.pgm.init.api.InitUtil.username;

import com.google.common.base.Strings;
import com.google.gerrit.pgm.init.api.Section;

public class HANAInitializer implements DatabaseConfigInitializer {

  @Override
  public void initConfig(Section databaseSection) {
    databaseSection.string("Server hostname", "hostname", "localhost");
    String instance = databaseSection.get("instance");
    if (!Strings.isNullOrEmpty(instance)) {
      String port = String.format("3%02d15", Integer.parseInt(instance));
      databaseSection.string("Server port", "port", port, false);
      databaseSection.unset("instance");
    } else {
      databaseSection.string("Server port", "port", "(hana default)", true);
    }
    databaseSection.string("Database name", "database", null);
    databaseSection.string("Database username", "username", username());
    databaseSection.password("username", "password");
  }
}
