// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.sshd;

import static com.google.gerrit.server.config.SshClientImplementation.APACHE;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.sshd.DefaultProxyDataFactory;
import org.eclipse.jgit.transport.sshd.JGitKeyCache;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.eclipse.jgit.util.FS;

public class SshSessionFactoryInitializer {
  public static void init(Config config) {
    switch (config.getEnum("ssh", null, "clientImplementation", APACHE)) {
      case APACHE:
        SshdSessionFactory factory =
            new SshdSessionFactory(new JGitKeyCache(), new DefaultProxyDataFactory());
        factory.setHomeDirectory(FS.DETECTED.userHome());
        SshSessionFactory.setInstance(factory);
        break;

      case JSCH:
        SshSessionFactory.setInstance(new JschConfigSessionFactory());
    }
  }

  private SshSessionFactoryInitializer() {}
}
