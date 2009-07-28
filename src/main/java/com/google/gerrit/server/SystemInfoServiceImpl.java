// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.client.data.SshHostKey;
import com.google.gerrit.client.data.SystemInfoService;
import com.google.gerrit.client.reviewdb.ContributorAgreement;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.server.ssh.GerritSshDaemon;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;

import org.apache.sshd.common.util.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.PublicKey;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

class SystemInfoServiceImpl implements SystemInfoService {
  private static final Logger log =
      LoggerFactory.getLogger(SystemInfoServiceImpl.class);
  private static final JSch JSCH = new JSch();

  private final SchemaFactory<ReviewDb> schema;
  private final GerritSshDaemon sshd;
  private final GerritConfig config;
  private final List<PublicKey> hostKeys;

  @Inject
  SystemInfoServiceImpl(final SchemaFactory<ReviewDb> sf,
      final GerritSshDaemon daemon, final GerritConfig gc) {
    schema = sf;
    sshd = daemon;
    config = gc;
    hostKeys = sortHostKeys();
  }

  private static boolean isIPv6(final InetAddress ip) {
    return ip instanceof Inet6Address
        && ip.getHostName().equals(ip.getHostAddress());
  }

  public void loadGerritConfig(final AsyncCallback<GerritConfig> callback) {
    callback.onSuccess(config);
  }

  public void contributorAgreements(
      final AsyncCallback<List<ContributorAgreement>> callback) {
    try {
      final ReviewDb db = schema.open();
      try {
        callback.onSuccess(db.contributorAgreements().active().toList());
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      callback.onFailure(e);
    }
  }

  public void daemonHostKeys(final AsyncCallback<List<SshHostKey>> callback) {
    final String hostIdent = hostIdent();
    final ArrayList<SshHostKey> r = new ArrayList<SshHostKey>(hostKeys.size());
    for (final PublicKey pub : hostKeys) {
      try {
        final HostKey hk = toHostKey(hostIdent, pub);
        r.add(new SshHostKey(hk.getHost(), hk.getType() + " " + hk.getKey(), hk
            .getFingerPrint(JSCH)));
      } catch (JSchException e) {
        log.error("Invalid host key", e);
        continue;
      }
    }
    callback.onSuccess(r);
  }

  private List<PublicKey> sortHostKeys() {
    final List<PublicKey> r = new ArrayList<PublicKey>(2);
    r.addAll(sshd.getHostKeys());
    Collections.sort(r, new Comparator<PublicKey>() {
      @Override
      public int compare(final PublicKey a, final PublicKey b) {
        if (a == b) {
          return 0;
        }
        if (a instanceof RSAPublicKey) {
          return -1;
        }
        if (a instanceof DSAPublicKey) {
          return 1;
        }
        return 0;
      }
    });
    return Collections.unmodifiableList(r);
  }

  private HostKey toHostKey(final String hostIdent, final PublicKey pub)
      throws JSchException {
    final Buffer buf = new Buffer();
    buf.putPublicKey(pub);
    final byte[] keyBin = buf.getCompactData();
    return new HostKey(hostIdent, keyBin);
  }

  private String hostIdent() {
    final HttpServletRequest req =
        GerritJsonServlet.getCurrentCall().getHttpServletRequest();

    InetSocketAddress addr = sshd.getAddress();
    InetAddress ip = addr.getAddress();
    if (ip.isAnyLocalAddress()) {
      try {
        ip = InetAddress.getByName(req.getServerName());
      } catch (UnknownHostException e) {
        throw new RuntimeException(e);
      }
      addr = new InetSocketAddress(ip, addr.getPort());
    }

    if (addr.getPort() == 22 && !isIPv6(ip)) {
      return addr.getHostName();
    }
    return "[" + addr.getHostName() + "]:" + addr.getPort();
  }
}
