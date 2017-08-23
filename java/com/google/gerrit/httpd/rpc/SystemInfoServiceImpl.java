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

package com.google.gerrit.httpd.rpc;

import com.google.gerrit.common.data.SshHostKey;
import com.google.gerrit.common.data.SystemInfoService;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gwtjsonrpc.common.AsyncCallback;
import com.google.gwtjsonrpc.common.VoidResult;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSch;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SystemInfoServiceImpl implements SystemInfoService {
  private static final Logger log = LoggerFactory.getLogger(SystemInfoServiceImpl.class);

  private static final JSch JSCH = new JSch();

  private final List<HostKey> hostKeys;
  private final Provider<HttpServletRequest> httpRequest;

  @Inject
  SystemInfoServiceImpl(SshInfo daemon, Provider<HttpServletRequest> hsr) {
    hostKeys = daemon.getHostKeys();
    httpRequest = hsr;
  }

  @Override
  public void daemonHostKeys(AsyncCallback<List<SshHostKey>> callback) {
    final ArrayList<SshHostKey> r = new ArrayList<>(hostKeys.size());
    for (HostKey hk : hostKeys) {
      String host = hk.getHost();
      if (host.startsWith("*:")) {
        final String port = host.substring(2);
        host = "[" + httpRequest.get().getServerName() + "]:" + port;
      }
      final String fp = hk.getFingerPrint(JSCH);
      r.add(new SshHostKey(host, hk.getType() + " " + hk.getKey(), fp));
    }
    callback.onSuccess(r);
  }

  @Override
  public void clientError(String message, AsyncCallback<VoidResult> callback) {
    HttpServletRequest r = httpRequest.get();
    String ua = r.getHeader("User-Agent");
    message = message.replaceAll("\n", "\n  ");
    log.error("Client UI JavaScript error: User-Agent=" + ua + ": " + message);
    callback.onSuccess(VoidResult.INSTANCE);
  }
}
