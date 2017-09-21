// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.ssh;

import com.google.common.collect.Lists;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.util.SocketUtil;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SshAddressesModule extends AbstractModule {
  private static final Logger log = LoggerFactory.getLogger(SshAddressesModule.class);

  public static final int DEFAULT_PORT = 29418;
  public static final int IANA_SSH_PORT = 22;

  @Override
  protected void configure() {}

  @Provides
  @Singleton
  @SshListenAddresses
  public List<SocketAddress> getListenAddresses(@GerritServerConfig Config cfg) {
    List<SocketAddress> listen = Lists.newArrayListWithExpectedSize(2);
    String[] want = cfg.getStringList("sshd", null, "listenaddress");
    if (want == null || want.length == 0) {
      listen.add(new InetSocketAddress(DEFAULT_PORT));
      return listen;
    }

    if (want.length == 1 && isOff(want[0])) {
      return listen;
    }

    for (String desc : want) {
      try {
        listen.add(SocketUtil.resolve(desc, DEFAULT_PORT));
      } catch (IllegalArgumentException e) {
        log.error("Bad sshd.listenaddress: " + desc + ": " + e.getMessage());
      }
    }
    return listen;
  }

  private static boolean isOff(String listenHostname) {
    return "off".equalsIgnoreCase(listenHostname)
        || "none".equalsIgnoreCase(listenHostname)
        || "no".equalsIgnoreCase(listenHostname);
  }

  @Provides
  @Singleton
  @SshAdvertisedAddresses
  List<String> getAdvertisedAddresses(
      @GerritServerConfig Config cfg, @SshListenAddresses List<SocketAddress> listen) {
    String[] want = cfg.getStringList("sshd", null, "advertisedaddress");
    if (want.length > 0) {
      return Arrays.asList(want);
    }
    List<InetSocketAddress> pub = new ArrayList<>();
    List<InetSocketAddress> local = new ArrayList<>();

    for (SocketAddress addr : listen) {
      if (addr instanceof InetSocketAddress) {
        InetSocketAddress inetAddr = (InetSocketAddress) addr;
        if (inetAddr.getAddress().isLoopbackAddress()) {
          local.add(inetAddr);
        } else {
          pub.add(inetAddr);
        }
      }
    }
    if (pub.isEmpty()) {
      pub = local;
    }
    List<String> adv = Lists.newArrayListWithCapacity(pub.size());
    for (InetSocketAddress addr : pub) {
      adv.add(SocketUtil.format(addr, IANA_SSH_PORT));
    }
    return adv;
  }
}
