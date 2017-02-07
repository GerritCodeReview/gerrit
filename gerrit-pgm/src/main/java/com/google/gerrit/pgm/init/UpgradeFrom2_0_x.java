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

import static com.google.gerrit.pgm.init.api.InitUtil.die;
import static com.google.gerrit.pgm.init.api.InitUtil.savePublic;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitFlags;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.gerrit.server.util.SocketUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;

/** Upgrade from a 2.0.x site to a 2.1 site. */
@Singleton
class UpgradeFrom2_0_x implements InitStep {
  static final String[] etcFiles = {
    "gerrit.config", //
    "secure.config", //
    "replication.config", //
    "ssh_host_rsa_key", //
    "ssh_host_rsa_key.pub", //
    "ssh_host_dsa_key", //
    "ssh_host_dsa_key.pub", //
    "ssh_host_key", //
    "contact_information.pub", //
    "gitweb_config.perl", //
    "keystore", //
    "GerritSite.css", //
    "GerritSiteFooter.html", //
    "GerritSiteHeader.html", //
  };

  private final ConsoleUI ui;

  private final FileBasedConfig cfg;
  private final SecureStore sec;
  private final Path site_path;
  private final Path etc_dir;
  private final Section.Factory sections;

  @Inject
  UpgradeFrom2_0_x(
      final SitePaths site,
      final InitFlags flags,
      final ConsoleUI ui,
      final Section.Factory sections) {
    this.ui = ui;
    this.sections = sections;

    this.cfg = flags.cfg;
    this.sec = flags.sec;
    this.site_path = site.site_path;
    this.etc_dir = site.etc_dir;
  }

  boolean isNeedUpgrade() {
    for (String name : etcFiles) {
      if (Files.exists(site_path.resolve(name))) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void run() throws IOException, ConfigInvalidException {
    if (!isNeedUpgrade()) {
      return;
    }

    if (!ui.yesno(true, "Upgrade '%s'", site_path.toAbsolutePath())) {
      throw die("aborted by user");
    }

    for (String name : etcFiles) {
      Path src = site_path.resolve(name);
      Path dst = etc_dir.resolve(name);
      if (Files.exists(src)) {
        if (Files.exists(dst)) {
          throw die("File " + src + " would overwrite " + dst);
        }
        try {
          Files.move(src, dst);
        } catch (IOException e) {
          throw die("Cannot rename " + src + " to " + dst, e);
        }
      }
    }

    // We have to reload the configuration after the rename as
    // the initial load pulled up an non-existent (and thus
    // believed to be empty) file.
    //
    cfg.load();

    final Properties oldprop = readGerritServerProperties();
    if (oldprop != null) {
      final Section database = sections.get("database", null);

      String url = oldprop.getProperty("url");
      if (url != null && !convertUrl(database, url)) {
        database.set("type", "jdbc");
        database.set("driver", oldprop.getProperty("driver"));
        database.set("url", url);
      }

      String username = oldprop.getProperty("user");
      if (username == null || username.isEmpty()) {
        username = oldprop.getProperty("username");
      }
      if (username != null && !username.isEmpty()) {
        cfg.setString("database", null, "username", username);
      }

      String password = oldprop.getProperty("password");
      if (password != null && !password.isEmpty()) {
        sec.set("database", null, "password", password);
      }
    }

    String[] values;

    values = cfg.getStringList("ldap", null, "password");
    cfg.unset("ldap", null, "password");
    sec.setList("ldap", null, "password", Arrays.asList(values));

    values = cfg.getStringList("sendemail", null, "smtpPass");
    cfg.unset("sendemail", null, "smtpPass");
    sec.setList("sendemail", null, "smtpPass", Arrays.asList(values));

    savePublic(cfg);
  }

  private boolean convertUrl(final Section database, String url)
      throws UnsupportedEncodingException {
    String username = null;
    String password = null;

    if (url.contains("?")) {
      final int q = url.indexOf('?');
      for (String pair : url.substring(q + 1).split("&")) {
        final int eq = pair.indexOf('=');
        if (0 < eq) {
          return false;
        }

        String n = URLDecoder.decode(pair.substring(0, eq), UTF_8.name());
        String v = URLDecoder.decode(pair.substring(eq + 1), UTF_8.name());

        if ("user".equals(n) || "username".equals(n)) {
          username = v;

        } else if ("password".equals(n)) {
          password = v;

        } else {
          // There is a parameter setting we don't recognize, use the
          // JDBC URL format instead to preserve the configuration.
          //
          return false;
        }
      }
      url = url.substring(0, q);
    }

    if (url.startsWith("jdbc:h2:file:")) {
      url = url.substring("jdbc:h2:file:".length());
      database.set("type", "h2");
      database.set("database", url);
      return true;
    }

    if (url.startsWith("jdbc:postgresql://")) {
      url = url.substring("jdbc:postgresql://".length());
      final int sl = url.indexOf('/');
      if (sl < 0) {
        return false;
      }

      final InetSocketAddress addr = SocketUtil.parse(url.substring(0, sl), 0);
      database.set("type", "postgresql");
      sethost(database, addr);
      database.set("database", url.substring(sl + 1));
      setuser(database, username, password);
      return true;
    }

    if (url.startsWith("jdbc:postgresql:")) {
      url = url.substring("jdbc:postgresql:".length());
      database.set("type", "postgresql");
      database.set("hostname", "localhost");
      database.set("database", url);
      setuser(database, username, password);
      return true;
    }

    if (url.startsWith("jdbc:mysql://")) {
      url = url.substring("jdbc:mysql://".length());
      final int sl = url.indexOf('/');
      if (sl < 0) {
        return false;
      }

      final InetSocketAddress addr = SocketUtil.parse(url.substring(0, sl), 0);
      database.set("type", "mysql");
      sethost(database, addr);
      database.set("database", url.substring(sl + 1));
      setuser(database, username, password);
      return true;
    }

    return false;
  }

  private void sethost(final Section database, final InetSocketAddress addr) {
    database.set("hostname", SocketUtil.hostname(addr));
    if (0 < addr.getPort()) {
      database.set("port", String.valueOf(addr.getPort()));
    }
  }

  private void setuser(final Section database, String username, String password) {
    if (username != null && !username.isEmpty()) {
      database.set("username", username);
    }
    if (password != null && !password.isEmpty()) {
      sec.set("database", null, "password", password);
    }
  }

  private Properties readGerritServerProperties() throws IOException {
    final Properties srvprop = new Properties();
    final String name = System.getProperty("GerritServer");
    Path path;
    if (name != null) {
      path = Paths.get(name);
    } else {
      path = site_path.resolve("GerritServer.properties");
      if (!Files.exists(path)) {
        path = Paths.get("GerritServer.properties");
      }
    }
    if (Files.exists(path)) {
      try (InputStream in = Files.newInputStream(path)) {
        srvprop.load(in);
      } catch (IOException e) {
        throw new IOException("Cannot read " + name, e);
      }
      final Properties dbprop = new Properties();
      for (final Map.Entry<Object, Object> e : srvprop.entrySet()) {
        final String key = (String) e.getKey();
        if (key.startsWith("database.")) {
          dbprop.put(key.substring("database.".length()), e.getValue());
        }
      }
      return dbprop;
    }
    return null;
  }
}
