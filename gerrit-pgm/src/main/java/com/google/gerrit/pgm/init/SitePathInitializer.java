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

package com.google.gerrit.pgm.init;

import static com.google.gerrit.pgm.init.InitUtil.chmod;
import static com.google.gerrit.pgm.init.InitUtil.die;
import static com.google.gerrit.pgm.init.InitUtil.extract;
import static com.google.gerrit.pgm.init.InitUtil.mkdir;
import static com.google.gerrit.pgm.init.InitUtil.savePublic;
import static com.google.gerrit.pgm.init.InitUtil.saveSecure;
import static com.google.gerrit.pgm.init.InitUtil.version;

import com.google.gerrit.pgm.Init;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.mail.OutgoingEmail;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Initialize (or upgrade) an existing site. */
public class SitePathInitializer {
  private final ConsoleUI ui;
  private final InitFlags flags;
  private final SitePaths site;
  private final List<InitStep> steps;

  @Inject
  public SitePathInitializer(final Injector injector, final ConsoleUI ui,
      final InitFlags flags, final SitePaths site) {
    this.ui = ui;
    this.flags = flags;
    this.site = site;
    this.steps = stepsOf(injector);
  }

  public void run() throws Exception {
    ui.header("Gerrit Code Review %s", version());

    if (site.isNew) {
      if (!ui.yesno(true, "Create '%s'", site.site_path.getCanonicalPath())) {
        throw die("aborted by user");
      }
      if (!site.site_path.isDirectory() && !site.site_path.mkdirs()) {
        throw die("Cannot make directory " + site.site_path);
      }
      flags.deleteOnFailure = true;
    }

    mkdir(site.bin_dir);
    mkdir(site.etc_dir);
    mkdir(site.lib_dir);
    mkdir(site.tmp_dir);
    mkdir(site.logs_dir);
    mkdir(site.mail_dir);
    mkdir(site.static_dir);
    mkdir(site.plugins_dir);

    for (InitStep step : steps) {
      step.run();
    }

    savePublic(flags.cfg);
    saveSecure(flags.sec);

    if (!site.replication_config.exists()) {
      site.replication_config.createNewFile();
    }

    extract(site.gerrit_sh, Init.class, "gerrit.sh");
    chmod(0755, site.gerrit_sh);
    chmod(0700, site.tmp_dir);

    extractMailExample("Abandoned.vm");
    extractMailExample("ChangeFooter.vm");
    extractMailExample("ChangeSubject.vm");
    extractMailExample("Comment.vm");
    extractMailExample("Merged.vm");
    extractMailExample("MergeFail.vm");
    extractMailExample("NewChange.vm");
    extractMailExample("RegisterNewEmail.vm");
    extractMailExample("ReplacePatchSet.vm");

    if (!ui.isBatch()) {
      System.err.println();
    }
  }

  private void extractMailExample(String orig) throws Exception {
    File ex = new File(site.mail_dir, orig + ".example");
    extract(ex, OutgoingEmail.class, orig);
    chmod(0444, ex);
  }

  private static List<InitStep> stepsOf(final Injector injector) {
    final ArrayList<InitStep> r = new ArrayList<InitStep>();
    for (Binding<InitStep> b : all(injector)) {
      r.add(b.getProvider().get());
    }
    return r;
  }

  private static List<Binding<InitStep>> all(final Injector injector) {
    return injector.findBindingsByType(new TypeLiteral<InitStep>() {});
  }
}
