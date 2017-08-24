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

import static com.google.gerrit.common.FileUtil.chmod;
import static com.google.gerrit.pgm.init.api.InitUtil.die;
import static com.google.gerrit.pgm.init.api.InitUtil.extract;
import static com.google.gerrit.pgm.init.api.InitUtil.mkdir;
import static com.google.gerrit.pgm.init.api.InitUtil.savePublic;
import static com.google.gerrit.pgm.init.api.InitUtil.version;

import com.google.gerrit.common.FileUtil;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.pgm.init.api.Section.Factory;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.mail.EmailModule;
import com.google.inject.Binding;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Initialize (or upgrade) an existing site. */
public class SitePathInitializer {
  private final ConsoleUI ui;
  private final InitFlags flags;
  private final SitePaths site;
  private final List<InitStep> steps;
  private final Factory sectionFactory;
  private final SecureStoreInitData secureStoreInitData;

  @Inject
  public SitePathInitializer(
      final Injector injector,
      final ConsoleUI ui,
      final InitFlags flags,
      final SitePaths site,
      final Section.Factory sectionFactory,
      @Nullable final SecureStoreInitData secureStoreInitData) {
    this.ui = ui;
    this.flags = flags;
    this.site = site;
    this.sectionFactory = sectionFactory;
    this.secureStoreInitData = secureStoreInitData;
    this.steps = stepsOf(injector);
  }

  public void run() throws Exception {
    ui.header("Gerrit Code Review %s", version());

    if (site.isNew) {
      if (!ui.yesno(true, "Create '%s'", site.site_path.toAbsolutePath())) {
        throw die("aborted by user");
      }
      FileUtil.mkdirsOrDie(site.site_path, "Cannot make directory");
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
    mkdir(site.data_dir);

    for (InitStep step : steps) {
      if (step instanceof InitPlugins && flags.skipPlugins) {
        continue;
      }
      step.run();
    }

    saveSecureStore();
    savePublic(flags.cfg);

    extract(site.gerrit_sh, getClass(), "gerrit.sh");
    chmod(0755, site.gerrit_sh);
    extract(site.gerrit_service, getClass(), "gerrit.service");
    chmod(0755, site.gerrit_service);
    extract(site.gerrit_socket, getClass(), "gerrit.socket");
    chmod(0755, site.gerrit_socket);
    chmod(0700, site.tmp_dir);

    extractMailExample("Abandoned.soy");
    extractMailExample("AbandonedHtml.soy");
    extractMailExample("AddKey.soy");
    extractMailExample("ChangeFooter.soy");
    extractMailExample("ChangeFooterHtml.soy");
    extractMailExample("ChangeSubject.soy");
    extractMailExample("Comment.soy");
    extractMailExample("CommentHtml.soy");
    extractMailExample("CommentFooter.soy");
    extractMailExample("CommentFooterHtml.soy");
    extractMailExample("DeleteReviewer.soy");
    extractMailExample("DeleteReviewerHtml.soy");
    extractMailExample("DeleteVote.soy");
    extractMailExample("DeleteVoteHtml.soy");
    extractMailExample("Footer.soy");
    extractMailExample("FooterHtml.soy");
    extractMailExample("HeaderHtml.soy");
    extractMailExample("Merged.soy");
    extractMailExample("MergedHtml.soy");
    extractMailExample("NewChange.soy");
    extractMailExample("NewChangeHtml.soy");
    extractMailExample("RegisterNewEmail.soy");
    extractMailExample("ReplacePatchSet.soy");
    extractMailExample("ReplacePatchSetHtml.soy");
    extractMailExample("Restored.soy");
    extractMailExample("RestoredHtml.soy");
    extractMailExample("Reverted.soy");
    extractMailExample("RevertedHtml.soy");
    extractMailExample("SetAssignee.soy");
    extractMailExample("SetAssigneeHtml.soy");

    if (!ui.isBatch()) {
      System.err.println();
    }
  }

  public void postRun(Injector injector) throws Exception {
    for (InitStep step : steps) {
      if (step instanceof InitPlugins && flags.skipPlugins) {
        continue;
      }
      injector.injectMembers(step);
      step.postRun();
    }
  }

  private void saveSecureStore() throws IOException {
    if (secureStoreInitData != null) {
      Path dst = site.lib_dir.resolve(secureStoreInitData.jarFile.getFileName());
      Files.copy(secureStoreInitData.jarFile, dst);
      Section gerritSection = sectionFactory.get("gerrit", null);
      gerritSection.set("secureStoreClass", secureStoreInitData.className);
    }
  }

  private void extractMailExample(String orig) throws Exception {
    Path ex = site.mail_dir.resolve(orig + ".example");
    extract(ex, EmailModule.class, orig);
    chmod(0444, ex);
  }

  private static List<InitStep> stepsOf(Injector injector) {
    final ArrayList<InitStep> r = new ArrayList<>();
    for (Binding<InitStep> b : all(injector)) {
      r.add(b.getProvider().get());
    }
    return r;
  }

  private static List<Binding<InitStep>> all(Injector injector) {
    return injector.findBindingsByType(new TypeLiteral<InitStep>() {});
  }
}
