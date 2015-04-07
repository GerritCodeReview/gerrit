// Copyright (C) 2014 The Android Open Source Project
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

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.sshd.common.Session;
import org.apache.sshd.common.SessionListener;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class CachingPublicKeyAuthenticator implements PublickeyAuthenticator,
    SessionListener {

  private final PublickeyAuthenticator authenticator;
  private final Map<ServerSession, Map<PublicKey, Boolean>> sessionCache;

  @Inject
  public CachingPublicKeyAuthenticator(DatabasePubKeyAuth authenticator) {
    this.authenticator = authenticator;
    this.sessionCache = new ConcurrentHashMap<>();
  }

  @Override
  public boolean authenticate(String username, PublicKey key,
      ServerSession session) {
    Map<PublicKey, Boolean> m = sessionCache.get(session);
    if (m == null) {
      m = new HashMap<>();
      sessionCache.put(session, m);
      session.addListener(this);
    }
    if (m.containsKey(key)) {
      return m.get(key);
    }
    boolean r = authenticator.authenticate(username, key, session);
    m.put(key, r);
    return r;
  }

  @Override
  public void sessionCreated(Session session) {
  }

  @Override
  public void sessionEvent(Session sesssion, Event event) {
  }

  @Override
  public void sessionClosed(Session session) {
    sessionCache.remove(session);
  }
}
