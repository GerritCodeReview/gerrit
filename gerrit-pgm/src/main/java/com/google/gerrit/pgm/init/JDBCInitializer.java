// Copyright (C) 2012 The Android Open Source Project
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

import static com.google.gerrit.pgm.init.InitUtil.username;

import com.google.common.base.Strings;

class JDBCInitializer implements DatabaseConfigInitializer {
  @Override
  public void initConfig(Section database) {
    boolean hasUrl = Strings.emptyToNull(database.get("url")) != null;
    database.string("URL", "url", null);
    guessDriver(database);
    database.string("Driver class name", "driver", null);
    database.string("Database username", "username", hasUrl ? null : username());
    database.password("username", "password");
  }

  private void guessDriver(Section database) {
    String url = Strings.emptyToNull(database.get("url"));
    if (url != null && Strings.isNullOrEmpty(database.get("driver"))) {
      if (url.startsWith("jdbc:h2:")) {
        database.set("driver", "org.h2.Driver");
      } else if (url.startsWith("jdbc:mysql:")) {
        database.set("driver", "com.mysql.jdbc.Driver");
      } else if (url.startsWith("jdbc:postgresql:")) {
        database.set("driver", "org.postgresql.Driver");
      }
    }
  }
}
