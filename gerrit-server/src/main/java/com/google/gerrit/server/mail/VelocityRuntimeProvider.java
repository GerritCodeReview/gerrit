// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.mail;

import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.RuntimeInstance;

import java.util.Properties;

/** Configures Velocity template engine for sending email. */
public class VelocityRuntimeProvider implements Provider<RuntimeInstance> {
  private final SitePaths site;

  @Inject
  VelocityRuntimeProvider(SitePaths site) {
    this.site = site;
  }

  public RuntimeInstance get() {
    String rl = "resource.loader";
    String pkg = "org.apache.velocity.runtime.resource.loader";

    Properties p = new Properties();
    p.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS,
        "org.apache.velocity.runtime.log.SimpleLog4JLogSystem" );
    p.setProperty("runtime.log.logsystem.log4j.category", "velocity");

    if (site.mail_dir.isDirectory()) {
      p.setProperty(rl, "file, class");
      p.setProperty("file." + rl + ".class", pkg + ".FileResourceLoader");
      p.setProperty("file." + rl + ".path", site.mail_dir.getAbsolutePath());
      p.setProperty("class." + rl + ".class", pkg + ".ClasspathResourceLoader");
    } else {
      p.setProperty(rl, "class");
      p.setProperty("class." + rl + ".class", pkg + ".ClasspathResourceLoader");
    }

    RuntimeInstance ri = new RuntimeInstance();
    try {
      ri.init(p);
    } catch (Exception err) {
      throw new ProvisionException("Cannot configure Velocity templates", err);
    }
    return ri;
  }
}
