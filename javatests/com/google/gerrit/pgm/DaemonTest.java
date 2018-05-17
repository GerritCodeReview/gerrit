// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.pgm;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.truth.ExitCodeSubject.exitCode;

import com.google.gerrit.pgm.util.RuntimeShutdown;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

/** Runs the real Daemon server (not test environment) and makes assumptions. */
public class DaemonTest {
  @Rule public SiteRule siteRule = new SiteRule();

  @Test
  public void daemonStartsWithoutHttpd() throws Exception {
    siteRule.initSite();

    AtomicBoolean success = new AtomicBoolean(false);
    Daemon daemon =
        new Daemon(
            () -> {
              success.set(true);
              RuntimeShutdown.manualShutdown(); // Prevent the infinite loop
            },
            siteRule.getSitePaths().site_path);
    int exitCode = daemon.main(new String[] {"--disable-httpd"});

    assertAbout(exitCode()).that(exitCode).isSuccessful();
    assertThat(success.get()).named("Daemon started successfully").isTrue();
  }

  @Test
  public void daemonStartsWithoutSshd() throws Exception {
    siteRule.initSite();

    AtomicBoolean success = new AtomicBoolean(false);

    final Daemon daemon =
        new Daemon(
            () -> {
              success.set(true);
              RuntimeShutdown.manualShutdown(); // Prevent the infinite loop
            },
            siteRule.getSitePaths().site_path);

    int exitCode = daemon.main(new String[] {"--disable-sshd"});

    assertAbout(exitCode()).that(exitCode).isSuccessful();
    assertThat(success.get()).named("Daemon started successfully").isTrue();
  }

  @Test(timeout = 15_000) // Safety timeout
  public void daemonRefusesToStartWithoutServices() throws Exception {
    siteRule.initSite();

    // Prevent the infinite loop
    Daemon daemon =
        new Daemon(
            RuntimeShutdown::manualShutdown, // Prevent the infinite loop
            siteRule.getSitePaths().site_path);
    int exitCode = daemon.main(new String[] {"--disable-sshd", "--disable-httpd"});

    // When these two options are provided, the Daemon class should Die: no services are run, so
    // having a daemon is pointless.
    assertAbout(exitCode()).that(exitCode).isEqualTo(128);
  }
}
