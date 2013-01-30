// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.gerrit.pgm.Init;

class GerritSite {
  private String path;

  private GerritSite() {
  }

  static GerritSite init() throws Exception {
    GerritSite site = new GerritSite();
    Init init = new Init();
    init.main(new String[] {"-d", site.getPath(), "--batch", "--no-auto-start"});
    return site;
  }

  String getPath() {
    if (path == null) {
      DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
      path = "target/test_site_" + df.format(new Date());
    }
    return path;
  }
}
