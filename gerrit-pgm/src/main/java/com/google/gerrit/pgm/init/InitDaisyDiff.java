// Copyright (C) 2014 The Android Open Source Project
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

import static com.google.gerrit.pgm.init.InitUtil.extract;

import com.google.gerrit.pgm.BaseInit;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.File;
import java.io.IOException;

@Singleton
public class InitDaisyDiff implements InitStep {
  private final SitePaths site;

  @Inject
  public InitDaisyDiff(SitePaths site) {
    this.site = site;
  }

  @Override
  public void run() {
  }

  @Override
  public void postRun() throws IOException {
    extract(new File(site.static_dir, "daisydiff/css/diff.css"),
        BaseInit.class, "daisydiff.css");
    extract(new File(site.static_dir, "daisydiff/css/diff-sidebyside-a.css"),
        BaseInit.class, "daisydiff-sidebyside-a.css");
    extract(new File(site.static_dir, "daisydiff/css/diff-sidebyside-b.css"),
        BaseInit.class, "daisydiff-sidebyside-b.css");
  }
}
