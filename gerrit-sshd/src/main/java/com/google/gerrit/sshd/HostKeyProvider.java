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
    final File anyKey = site.ssh_key;
    final File rsaKey = site.ssh_rsa;
    final File dsaKey = site.ssh_dsa;

    final List<String> keys = new ArrayList<String>(2);
    if (rsaKey.exists()) {
      keys.add(rsaKey.getAbsolutePath());
    }
    if (dsaKey.exists()) {
      keys.add(dsaKey.getAbsolutePath());
    }

    if (anyKey.exists() && !keys.isEmpty()) {
      // If both formats of host key exist, we don't know which format
      // should be authoritative. Complain and abort.
      //
      keys.add(anyKey.getAbsolutePath());
      throw new ProvisionException("Multiple host keys exist: " + keys);
    }

    if (keys.isEmpty()) {
      throw new ProvisionException("No SSH keys under " + site.etc_dir);
    }
    if (!SecurityUtils.isBouncyCastleRegistered()) {
      throw new ProvisionException("Bouncy Castle Crypto not installed;"
          + " needed to read server host keys: " + keys + "");
    }
    return new FileKeyPairProvider(keys.toArray(new String[keys.size()]));
  }
}
