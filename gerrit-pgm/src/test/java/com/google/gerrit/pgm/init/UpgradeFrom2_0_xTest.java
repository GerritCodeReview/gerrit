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

import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.server.config.SitePaths;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.IO;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;


public class UpgradeFrom2_0_xTest extends InitTestCase {
  public void testUpgrade() throws IOException, ConfigInvalidException {
    final File p = newSitePath();
    final SitePaths site = new SitePaths(p);
    assertTrue(site.isNew);
    assertTrue(site.site_path.mkdir());
    assertTrue(site.etc_dir.mkdir());

    for (String n : UpgradeFrom2_0_x.etcFiles) {
      Writer w = new FileWriter(new File(p, n));
      try {
        w.write("# " + n + "\n");
      } finally {
        w.close();
      }
    }

    FileBasedConfig old =
        new FileBasedConfig(new File(p, "gerrit.config"), FS.DETECTED);

    old.setString("ldap", null, "username", "ldap.user");
    old.setString("ldap", null, "password", "ldap.s3kr3t");

    old.setString("sendemail", null, "smtpUser", "email.user");
    old.setString("sendemail", null, "smtpPass", "email.s3kr3t");
    old.save();

    final InitFlags flags = new InitFlags(site);
    final ConsoleUI ui = createStrictMock(ConsoleUI.class);
    Section.Factory sections = new Section.Factory() {
      @Override
      public Section get(String name) {
        return new Section(flags, site, ui, name);
      }
    };

    expect(ui.yesno(eq(true), eq("Upgrade '%s'"), eq(p.getCanonicalPath())))
        .andReturn(true);
    replay(ui);

    UpgradeFrom2_0_x u = new UpgradeFrom2_0_x(site, flags, ui, sections);
    assertTrue(u.isNeedUpgrade());
    u.run();
    assertFalse(u.isNeedUpgrade());
    verify(ui);

    for (String n : UpgradeFrom2_0_x.etcFiles) {
      if ("gerrit.config".equals(n)) continue;
      if ("secure.config".equals(n)) continue;
      assertEquals("# " + n + "\n",//
          new String(IO.readFully(new File(site.etc_dir, n)), "UTF-8"));
    }

    FileBasedConfig cfg = new FileBasedConfig(site.gerrit_config, FS.DETECTED);
    FileBasedConfig sec = new FileBasedConfig(site.secure_config, FS.DETECTED);
    cfg.load();
    sec.load();

    assertEquals("email.user", cfg.getString("sendemail", null, "smtpUser"));
    assertNull(cfg.getString("sendemail", null, "smtpPass"));
    assertEquals("email.s3kr3t", sec.getString("sendemail", null, "smtpPass"));

    assertEquals("ldap.user", cfg.getString("ldap", null, "username"));
    assertNull(cfg.getString("ldap", null, "password"));
    assertEquals("ldap.s3kr3t", sec.getString("ldap", null, "password"));

    u.run();
  }
}
