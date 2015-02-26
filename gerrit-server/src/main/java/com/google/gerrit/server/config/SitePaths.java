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

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/** Important paths within a {@link SitePath}. */
@Singleton
public final class SitePaths {
  public static final String CSS_FILENAME = "GerritSite.css";
  public static final String HEADER_FILENAME = "GerritSiteHeader.html";
  public static final String FOOTER_FILENAME = "GerritSiteFooter.html";

  public final Path site_path;
  public final Path bin_dir;
  public final Path etc_dir;
  public final Path lib_dir;
  public final Path tmp_dir;
  public final Path logs_dir;
  public final Path plugins_dir;
  public final Path data_dir;
  public final Path mail_dir;
  public final Path hooks_dir;
  public final Path static_dir;
  public final Path themes_dir;
  public final Path index_dir;

  public final Path gerrit_sh;
  public final Path gerrit_war;

  public final Path gerrit_config;
  public final Path secure_config;
  public final Path contact_information_pub;

  public final Path ssl_keystore;
  public final Path ssh_key;
  public final Path ssh_rsa;
  public final Path ssh_dsa;
  public final Path peer_keys;

  public final Path site_css;
  public final Path site_header;
  public final Path site_footer;
  public final Path site_gitweb;

  /** {@code true} if {@link #site_path} has not been initialized. */
  public final boolean isNew;

  @Inject
  public SitePaths(@SitePath Path sitePath) throws IOException {
    site_path = sitePath;
    Path p = sitePath;

    bin_dir = p.resolve("bin");
    etc_dir = p.resolve("etc");
    lib_dir = p.resolve("lib");
    tmp_dir = p.resolve("tmp");
    plugins_dir = p.resolve("plugins");
    data_dir = p.resolve("data");
    logs_dir = p.resolve("logs");
    mail_dir = etc_dir.resolve("mail");
    hooks_dir = p.resolve("hooks");
    static_dir = p.resolve("static");
    themes_dir = p.resolve("themes");
    index_dir = p.resolve("index");

    gerrit_sh = bin_dir.resolve("gerrit.sh");
    gerrit_war = bin_dir.resolve("gerrit.war");

    gerrit_config = etc_dir.resolve("gerrit.config");
    secure_config = etc_dir.resolve("secure.config");
    contact_information_pub = etc_dir.resolve("contact_information.pub");

    ssl_keystore = etc_dir.resolve("keystore");
    ssh_key = etc_dir.resolve("ssh_host_key");
    ssh_rsa = etc_dir.resolve("ssh_host_rsa_key");
    ssh_dsa = etc_dir.resolve("ssh_host_dsa_key");
    peer_keys = etc_dir.resolve("peer_keys");

    site_css = etc_dir.resolve(CSS_FILENAME);
    site_header = etc_dir.resolve(HEADER_FILENAME);
    site_footer = etc_dir.resolve(FOOTER_FILENAME);
    site_gitweb = etc_dir.resolve("gitweb_config.perl");

    boolean isNew;
    try (DirectoryStream<Path> files = Files.newDirectoryStream(site_path)) {
      isNew = Iterables.isEmpty(files);
    } catch (NoSuchFileException e) {
      isNew = true;
    }
    this.isNew = isNew;
  }

  /**
   * Resolve an absolute or relative path.
   * <p>
   * Relative paths are resolved relative to the {@link #site_path}.
   *
   * @param path the path string to resolve. May be null.
   * @return the resolved path; null if {@code path} was null or empty.
   */
  public Path resolve(String path) {
    if (path != null && !path.isEmpty()) {
      Path loc = site_path.resolve(path).normalize();
      try {
        return loc.toRealPath();
      } catch (IOException e) {
        return loc.toAbsolutePath();
      }
    }
    return null;
  }
}
