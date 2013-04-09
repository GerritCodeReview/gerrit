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

package com.google.gerrit.server.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Important paths within a {@link SitePath}. */
@Singleton
public final class SitePaths {
  public static final String CSS_FILENAME = "GerritSite.css";
  public static final String HEADER_FILENAME = "GerritSiteHeader.html";
  public static final String FOOTER_FILENAME = "GerritSiteFooter.html";

  public final File site_path;
  public final File bin_dir;
  public final File etc_dir;
  public final File lib_dir;
  public final File tmp_dir;
  public final File logs_dir;
  public final File plugins_dir;
  public final File data_dir;
  public final File mail_dir;
  public final File hooks_dir;
  public final File static_dir;
  public final File themes_dir;

  public final File gerrit_sh;
  public final File gerrit_war;

  public final File gerrit_config;
  public final File secure_config;
  public final File contact_information_pub;

  public final File ssl_keystore;
  public final File ssh_key;
  public final File ssh_rsa;
  public final File ssh_dsa;
  public final File peer_keys;

  public final File site_css;
  public final File site_header;
  public final File site_footer;
  public final File site_gitweb;

  /** {@code true} if {@link #site_path} has not been initialized. */
  public final boolean isNew;

  @Inject
  public SitePaths(final @SitePath File sitePath) throws FileNotFoundException {
    site_path = sitePath;

    bin_dir = new File(site_path, "bin");
    etc_dir = new File(site_path, "etc");
    lib_dir = new File(site_path, "lib");
    tmp_dir = new File(site_path, "tmp");
    plugins_dir = new File(site_path, "plugins");
    data_dir = new File(site_path, "data");
    logs_dir = new File(site_path, "logs");
    mail_dir = new File(etc_dir, "mail");
    hooks_dir = new File(site_path, "hooks");
    static_dir = new File(site_path, "static");
    themes_dir = new File(site_path, "themes");

    gerrit_sh = new File(bin_dir, "gerrit.sh");
    gerrit_war = new File(bin_dir, "gerrit.war");

    gerrit_config = new File(etc_dir, "gerrit.config");
    secure_config = new File(etc_dir, "secure.config");
    contact_information_pub = new File(etc_dir, "contact_information.pub");

    ssl_keystore = new File(etc_dir, "keystore");
    ssh_key = new File(etc_dir, "ssh_host_key");
    ssh_rsa = new File(etc_dir, "ssh_host_rsa_key");
    ssh_dsa = new File(etc_dir, "ssh_host_dsa_key");
    peer_keys = new File(etc_dir, "peer_keys");

    site_css = new File(etc_dir, CSS_FILENAME);
    site_header = new File(etc_dir, HEADER_FILENAME);
    site_footer = new File(etc_dir, FOOTER_FILENAME);
    site_gitweb = new File(etc_dir, "gitweb_config.perl");

    if (site_path.exists()) {
      final String[] contents = site_path.list();
      if (contents != null)
        isNew = contents.length == 0;
      else if (site_path.isDirectory())
        throw new FileNotFoundException("Cannot access " + site_path);
      else
        throw new FileNotFoundException("Not a directory: " + site_path);
    } else {
      isNew = true;
    }
  }

  /**
   * Resolve an absolute or relative path.
   * <p>
   * Relative paths are resolved relative to the {@link #site_path}.
   *
   * @param path the path string to resolve. May be null.
   * @return the resolved path; null if {@code path} was null or empty.
   */
  public File resolve(final String path) {
    if (path != null && !path.isEmpty()) {
      File loc = new File(path);
      if (!loc.isAbsolute()) {
        loc = new File(site_path, path);
      }
      try {
        return loc.getCanonicalFile();
      } catch (IOException e) {
        return loc.getAbsoluteFile();
      }
    }
    return null;
  }
}
