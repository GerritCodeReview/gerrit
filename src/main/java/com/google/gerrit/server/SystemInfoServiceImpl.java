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
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.server.ssh.GerritSshDaemon;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;

import org.apache.sshd.common.util.Buffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

public class SystemInfoServiceImpl implements SystemInfoService {
  private static final Logger log =
      LoggerFactory.getLogger(SystemInfoServiceImpl.class);
  private static final JSch JSCH = new JSch();

  public void loadGerritConfig(final AsyncCallback<GerritConfig> callback) {
    callback.onSuccess(Common.getGerritConfig());
  }

  public void contributorAgreements(
      final AsyncCallback<List<ContributorAgreement>> callback) {
    try {
      final ReviewDb db = Common.getSchemaFactory().open();
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
    final Collection<PublicKey> keys = GerritSshDaemon.getHostKeys();
    final ArrayList<SshHostKey> r = new ArrayList<SshHostKey>(keys.size());
    for (final PublicKey pub : keys) {
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
    final String serverName = req.getServerName();
    final int serverPort = GerritSshDaemon.getSshdPort();
    return serverPort == 22 ? serverName : "[" + serverName + "]:" + serverPort;
  }
}
