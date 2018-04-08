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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.common.io.ByteStreams;
import com.google.gerrit.common.FileUtil;
import com.google.gerrit.config.SitePaths;
import com.google.gerrit.extensions.securestore.SecureStore;
import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.Section;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.junit.Test;

public class UpgradeFrom2_0_xTest extends InitTestCase {

  @Test
  public void upgrade() throws IOException, ConfigInvalidException {
    final Path p = newSitePath();
    final SitePaths site = new SitePaths(p);
    assertTrue(site.isNew);
    FileUtil.mkdirsOrDie(site.etc_dir, "Failed to create");

    for (String n : UpgradeFrom2_0_x.etcFiles) {
      Files.write(p.resolve(n), ("# " + n + "\n").getBytes(UTF_8));
    }

    FileBasedConfig old = new FileBasedConfig(p.resolve("gerrit.config").toFile(), FS.DETECTED);

    old.setString("ldap", null, "username", "ldap.user");
    old.setString("ldap", null, "password", "ldap.s3kr3t");

    old.setString("sendemail", null, "smtpUser", "email.user");
    old.setString("sendemail", null, "smtpPass", "email.s3kr3t");
    old.save();

    final InMemorySecureStore secureStore = new InMemorySecureStore();
    final InitFlags flags =
        new InitFlags(site, secureStore, Collections.<String>emptyList(), false);
    final ConsoleUI ui = createStrictMock(ConsoleUI.class);
    Section.Factory sections =
        new Section.Factory() {
          @Override
          public Section get(String name, String subsection) {
            return new Section(flags, site, secureStore, ui, name, subsection);
          }
        };

    expect(ui.yesno(eq(true), eq("Upgrade '%s'"), eq(p.toAbsolutePath().normalize())))
        .andReturn(true);
    replay(ui);

    UpgradeFrom2_0_x u = new UpgradeFrom2_0_x(site, flags, ui, sections);
    assertTrue(u.isNeedUpgrade());
    u.run();
    assertFalse(u.isNeedUpgrade());
    verify(ui);

    for (String n : UpgradeFrom2_0_x.etcFiles) {
      if ("gerrit.config".equals(n) || "secure.config".equals(n)) {
        continue;
      }
      try (InputStream in = Files.newInputStream(site.etc_dir.resolve(n))) {
        assertEquals("# " + n + "\n", new String(ByteStreams.toByteArray(in), UTF_8));
      }
    }

    FileBasedConfig cfg = new FileBasedConfig(site.gerrit_config.toFile(), FS.DETECTED);
    cfg.load();

    assertEquals("email.user", cfg.getString("sendemail", null, "smtpUser"));
    assertNull(cfg.getString("sendemail", null, "smtpPass"));
    assertEquals("email.s3kr3t", secureStore.get("sendemail", null, "smtpPass"));

    assertEquals("ldap.user", cfg.getString("ldap", null, "username"));
    assertNull(cfg.getString("ldap", null, "password"));
    assertEquals("ldap.s3kr3t", secureStore.get("ldap", null, "password"));

    u.run();
  }

  private static class InMemorySecureStore extends SecureStore {
    private final Config cfg = new Config();

    @Override
    public String[] getList(String section, String subsection, String name) {
      return cfg.getStringList(section, subsection, name);
    }

    @Override
    public String[] getListForPlugin(
        String pluginName, String section, String subsection, String name) {
      throw new UnsupportedOperationException("not used by tests");
    }

    @Override
    public void setList(String section, String subsection, String name, List<String> values) {
      cfg.setStringList(section, subsection, name, values);
    }

    @Override
    public void unset(String section, String subsection, String name) {
      cfg.unset(section, subsection, name);
    }

    @Override
    public Iterable<EntryKey> list() {
      throw new UnsupportedOperationException("not used by tests");
    }

    @Override
    public boolean isOutdated() {
      throw new UnsupportedOperationException("not used by tests");
    }

    @Override
    public void reload() {
      throw new UnsupportedOperationException("not used by tests");
    }
  }
}
