// Copyright (C) 2012 The Android Open Source Project
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
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Config;

/** Provides {@link SshdListenAddressl} from {@code sshd.listenAddress}. */
public class SshdListenAddressProvider implements Provider<String> {
  private final String sshAddress;

  @Inject
  public SshdListenAddressProvider(@GerritServerConfig final Config config) {
    String sshdListenAddress = config.getString("sshd", null, "listenAddress");
    String sshdAdvertisedAddress = config.getString("sshd", null, "advertisedAddress");

    /*
     * If advertised address is specified it should take precedence over the
     * "normal" listening address.
     */
    if (sshdAdvertisedAddress != null && ! sshdAdvertisedAddress.isEmpty()) {
      sshAddress = sshdAdvertisedAddress;
    } else {
      sshAddress = sshdListenAddress;
    }
  }

  @Override
  public String get() {
    return sshAddress;
  }
}
