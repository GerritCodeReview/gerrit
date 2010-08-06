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

import static com.google.gerrit.pgm.init.InitUtil.die;
import static com.google.gerrit.pgm.init.InitUtil.savePublic;
import static com.google.gerrit.pgm.init.InitUtil.saveSecure;

import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.schema.DataSourceProvider;
import com.google.gerrit.server.util.SocketUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.storage.file.FileBasedConfig;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

/** Upgrade from a 2.0.x site to a 2.1 site. */
@Singleton
class UpgradeFrom2_0_x implements InitStep {
  static final String[] etcFiles = {"gerrit.config", //
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
  private final FileBasedConfig sec;
  private final File site_path;
  private final File etc_dir;
  private final Section.Factory sections;

  @Inject
  UpgradeFrom2_0_x(final SitePaths site, final InitFlags flags,
      final ConsoleUI ui, final Section.Factory sections) {
    this.ui = ui;
    this.sections = sections;

    this.cfg = flags.cfg;
    this.sec = flags.sec;
    this.site_path = site.site_path;
    this.etc_dir = site.etc_dir;
  }

  boolean isNeedUpgrade() {
    for (String name : etcFiles) {
      if (new File(site_path, name).exists()) {
        return true;
      }
    }
    return false;
  }

  public void run() throws IOException, ConfigInvalidException {
    if (!isNeedUpgrade()) {
      return;
    }

    if (!ui.yesno(true, "Upgrade '%s'", site_path.getCanonicalPath())) {
      throw die("aborted by user");
    }

    for (String name : etcFiles) {
      final File src = new File(site_path, name);
      final File dst = new File(etc_dir, name);
      if (src.exists()) {
        if (dst.exists()) {
          throw die("File " + src + " would overwrite " + dst);
        }
        if (!src.renameTo(dst)) {
          throw die("Cannot rename " + src + " to " + dst);
        }
      }
    }

    // We have to reload the configuration after the rename as
    // the initial load pulled up an non-existent (and thus
    // believed to be empty) file.
    //
    cfg.load();
    sec.load();

    final Properties oldprop = readGerritServerProperties();
    if (oldprop != null) {
      final Section database = sections.get("database");

      String url = oldprop.getProperty("url");
      if (url != null && !convertUrl(database, url)) {
        database.set("type", DataSourceProvider.Type.JDBC);
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
        sec.setString("database", null, "password", password);
      }
    }

    String[] values;

    values = cfg.getStringList("ldap", null, "password");
    cfg.unset("ldap", null, "password");
    sec.setStringList("ldap", null, "password", Arrays.asList(values));

    values = cfg.getStringList("sendemail", null, "smtpPass");
    cfg.unset("sendemail", null, "smtpPass");
    sec.setStringList("sendemail", null, "smtpPass", Arrays.asList(values));

    saveSecure(sec);
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

        String n = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
        String v = URLDecoder.decode(pair.substring(eq + 1), "UTF-8");

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
      database.set("type", DataSourceProvider.Type.H2);
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
      database.set("type", DataSourceProvider.Type.POSTGRESQL);
      sethost(database, addr);
      database.set("database", url.substring(sl + 1));
      setuser(database, username, password);
      return true;
    }

    if (url.startsWith("jdbc:postgresql:")) {
      url = url.substring("jdbc:postgresql:".length());
      database.set("type", DataSourceProvider.Type.POSTGRESQL);
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
      database.set("type", DataSourceProvider.Type.MYSQL);
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
      sec.setString("database", null, "password", password);
    }
  }

  private Properties readGerritServerProperties() throws IOException {
    final Properties srvprop = new Properties();
    final String name = System.getProperty("GerritServer");
    File path;
    if (name != null) {
      path = new File(name);
    } else {
      path = new File(site_path, "GerritServer.properties");
      if (!path.exists()) {
        path = new File("GerritServer.properties");
      }
    }
    if (path.exists()) {
      try {
        final InputStream in = new FileInputStream(path);
        try {
          srvprop.load(in);
        } finally {
          in.close();
        }
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
    } else {
      return null;
    }
  }
}
