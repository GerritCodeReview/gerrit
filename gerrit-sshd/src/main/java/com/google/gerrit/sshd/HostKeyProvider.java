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

package com.google.gerrit.sshd;

import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

class HostKeyProvider implements Provider<KeyPairProvider> {
  private final SitePaths site;

  @Inject
  HostKeyProvider(final SitePaths site) {
    this.site = site;
  }

  @Override
  public KeyPairProvider get() {
    final File objKey = site.ssh_key;
    final File rsaKey = site.ssh_rsa;
    final File dsaKey = site.ssh_dsa;

    final List<String> stdKeys = new ArrayList<String>(2);
    if (rsaKey.exists()) {
      stdKeys.add(rsaKey.getAbsolutePath());
    }
    if (dsaKey.exists()) {
      stdKeys.add(dsaKey.getAbsolutePath());
    }

    if (objKey.exists()) {
      if (stdKeys.isEmpty()) {
        SimpleGeneratorHostKeyProvider p = new SimpleGeneratorHostKeyProvider();
        p.setPath(objKey.getAbsolutePath());
        return p;

      } else {
        // Both formats of host key exist, we don't know which format
        // should be authoritative. Complain and abort.
        //
        stdKeys.add(objKey.getAbsolutePath());
        throw new ProvisionException("Multiple host keys exist: " + stdKeys);
      }

    } else {
      if (stdKeys.isEmpty()) {
        throw new ProvisionException("No SSH keys under " + site.etc_dir);
      }
      if (!SecurityUtils.isBouncyCastleRegistered()) {
        throw new ProvisionException("Bouncy Castle Crypto not installed;"
            + " needed to read server host keys: " + stdKeys + "");
      }
      return new FileKeyPairProvider(stdKeys
          .toArray(new String[stdKeys.size()]));
    }
  }
}
