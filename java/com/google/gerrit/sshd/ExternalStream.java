// Copyright (C) 2017 The Android Open Source Project
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.transport.SideBandOutputStream.MAX_BUF;

import com.google.common.base.Splitter;
import com.google.common.net.InetAddresses;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.util.IdGenerator;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.KeyPair;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.PropertyResolver;
import org.apache.sshd.common.Service;
import org.apache.sshd.common.channel.ChannelListener;
import org.apache.sshd.common.channel.PtyMode;
import org.apache.sshd.common.cipher.Cipher;
import org.apache.sshd.common.cipher.CipherInformation;
import org.apache.sshd.common.compression.Compression;
import org.apache.sshd.common.compression.CompressionInformation;
import org.apache.sshd.common.forward.PortForwardingEventListener;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.future.KeyExchangeFuture;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.io.IoWriteFuture;
import org.apache.sshd.common.kex.KexProposalOption;
import org.apache.sshd.common.kex.KeyExchange;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.mac.Mac;
import org.apache.sshd.common.mac.MacInformation;
import org.apache.sshd.common.session.ReservedSessionMessagesHandler;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ServerFactoryManager;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.Signal;
import org.apache.sshd.server.SignalListener;
import org.apache.sshd.server.auth.UserAuth;
import org.apache.sshd.server.auth.gss.GSSAuthenticator;
import org.apache.sshd.server.auth.hostbased.HostBasedAuthenticator;
import org.apache.sshd.server.auth.keyboard.KeyboardInteractiveAuthenticator;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerProxyAcceptor;
import org.apache.sshd.server.session.ServerSession;
import org.eclipse.jgit.transport.PacketLineIn;
import org.eclipse.jgit.transport.SideBandOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ExternalStream {
  private static final Logger log = LoggerFactory.getLogger(ExternalStream.class);

  static interface Factory {
    ExternalStream create(Socket s);
  }

  private final ExternalDaemon daemon;
  private final AccountCache accountCache;
  private final SshKeyCacheImpl keyCache;
  private final IdGenerator idGenerator;
  private final CommandFactory cmdFactory;
  private final IdentifiedUser.GenericFactory userFactory;
  private final Socket sock;

  private SshSession session;

  @Inject
  ExternalStream(
      ExternalDaemon daemon,
      AccountCache accountCache,
      SshKeyCacheImpl keyCache,
      IdGenerator idGenerator,
      CommandFactory cmdFactory,
      IdentifiedUser.GenericFactory userFactory,
      @Assisted Socket sock) {
    this.daemon = daemon;
    this.accountCache = accountCache;
    this.keyCache = keyCache;
    this.idGenerator = idGenerator;
    this.cmdFactory = cmdFactory;
    this.userFactory = userFactory;
    this.sock = sock;
  }

  void begin(ExecutorService startExecutor) {
    startExecutor.submit(() -> start());
  }

  private void start() {
    try {
      PacketLineIn in = new PacketLineIn(sock.getInputStream());

      String line = in.readString();
      if (!line.startsWith("auth ")) {
        sock.close();
        return;
      }
      if (!daemon.checkAuth(line.substring(5))) {
        sock.close();
        return;
      }

      line = in.readString();
      if (line.startsWith("keys ")) {
        listKeys(line.substring(5));
        sock.close();
        return;
      }

      if (!line.startsWith("conn ")) {
        sock.close();
        return;
      }

      String client = line.substring(5);
      line = in.readString();
      if (!line.startsWith("user ")) {
        sock.close();
        return;
      }
      if (!login(client, line.substring(5))) {
        sock.close();
        return;
      }

      line = in.readString();
      if (!line.startsWith("exec ")) {
          sock.close();
          return;
      }
      exec(line.substring(5));
    } catch (IOException e1) {
      log.warn("cannot read stream", e1);
      try {
        sock.close();
      } catch (IOException e2) {
      }
    }
  }

  private void listKeys(String username) throws IOException {
    try (OutputStream out = stdout()) {
      for (SshKeyCacheEntry key : keyCache.get(username)) {
        out.write(key.getKey().getBytes(UTF_8));
        out.write('\n');
      }
      out.flush();
    }
  }

  private boolean login(String clientAddr, String username) throws IOException {
    AccountState as = accountCache.getByUsername(username);
    if (as == null) {
      try (OutputStream err = stderr()) {
        err.write("Invalid authentication.\n".getBytes(UTF_8));
        err.flush();
      }
      return false;
    }

    List<String> addrParts = Splitter.on(' ').splitToList(clientAddr);
    if (addrParts.size() < 2) {
      return false;
    }

    InetAddress ip = InetAddresses.forString(addrParts.get(0));
    int port = Integer.parseInt(addrParts.get(1), 10);
    SocketAddress peer = new InetSocketAddress(ip, port);
    CurrentUser user = userFactory.create(peer, as.getAccount().getId());

    session = new SshSession(idGenerator.next(), peer);
    session.authenticationSuccess(username, user);
    return true;
  }

  private void exec(String cmdLine) throws IOException {
    Command cmd = cmdFactory.createCommand(cmdLine);
    cmd.setInputStream(sock.getInputStream());
    cmd.setOutputStream(stdout());
    cmd.setErrorStream(stderr());
    cmd.setExitCallback((rc, msg) -> exit(rc));
    if (cmd instanceof SessionAware) {
      ((SessionAware) cmd).setSession(new ServerSessionImpl());
    }
    cmd.start(new SshEnv());
  }

  private void exit(int rc) {
    try {
      try (SideBandOutputStream status = new SideBandOutputStream(3, 8, sock.getOutputStream())) {
        status.write((byte) rc);
        status.flush();
      }
      sock.close();
    } catch (IOException e) {
      log.error("cannot close SSH stream", e);
    }
  }

  private SideBandOutputStream stdout() throws IOException {
    return new SideBandOutputStream(1, MAX_BUF, sock.getOutputStream());
  }

  private OutputStream stderr() throws IOException {
    return new SideBandOutputStream(2, MAX_BUF, sock.getOutputStream());
  }

  private class SshEnv implements Environment {
    private final Map<String, String> env = new HashMap<>();

    @Override
    public Map<String, String> getEnv() {
      return env;
    }

    @Override
    public Map<PtyMode, Integer> getPtyModes() {
      return Collections.emptyMap();
    }

    @Override
    public void addSignalListener(SignalListener listener, Signal... signal) {}

    @Override
    public void addSignalListener(SignalListener listener, Collection<Signal> signals) {}

    @Override
    public void addSignalListener(SignalListener listener) {}

    @Override
    public void removeSignalListener(SignalListener listener) {}
  }

  private class ServerSessionImpl implements ServerSession {
    private final Map<AttributeKey<Object>, Object> att = new HashMap<>();

    ServerSessionImpl() {
      setAttribute(SshSession.KEY, session);
    }

    @Override
    public String getUsername() {
      return session != null ? session.getUsername() : null;
    }

    @Override
    public boolean isAuthenticated() {
      return session != null;
    }

    @Override
    public Map<String, Object> getProperties() {
      return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getAttribute(AttributeKey<T> key) {
      return (T) att.get(key);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T setAttribute(AttributeKey<T> key, T value) {
      return (T) att.put((AttributeKey<Object>) key, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T removeAttribute(AttributeKey<T> key) {
      return (T) att.remove(key);
    }

    @Override
    public <T> T resolveAttribute(AttributeKey<T> key) {
      return getAttribute(key);
    }

    @Override
    public String getClientVersion() {
      return null;
    }

    @Override
    public String getServerVersion() {
      return null;
    }

    @Override
    public String getNegotiatedKexParameter(KexProposalOption paramType) {
      return null;
    }

    @Override
    public CipherInformation getCipherInformation(boolean incoming) {
      return null;
    }

    @Override
    public CompressionInformation getCompressionInformation(boolean incoming) {
      return null;
    }

    @Override
    public MacInformation getMacInformation(boolean incoming) {
      return null;
    }

    @Override
    public Buffer createBuffer(byte cmd) {
      return null;
    }

    @Override
    public Buffer createBuffer(byte cmd, int estimatedSize) {
      return null;
    }

    @Override
    public Buffer prepareBuffer(byte cmd, Buffer buffer) {
      return null;
    }

    @Override
    public IoWriteFuture sendDebugMessage(boolean display, Object msg, String lang)
        throws IOException {
      return null;
    }

    @Override
    public IoWriteFuture sendIgnoreMessage(byte... data) throws IOException {
      return null;
    }

    @Override
    public IoWriteFuture writePacket(Buffer buffer) throws IOException {
      return null;
    }

    @Override
    public IoWriteFuture writePacket(Buffer buffer, long timeout, TimeUnit unit)
        throws IOException {
      return null;
    }

    @Override
    public Buffer request(String request, Buffer buffer, long timeout, TimeUnit unit)
        throws IOException {
      return null;
    }

    @Override
    public void exceptionCaught(Throwable t) {}

    @Override
    public KeyExchangeFuture reExchangeKeys() throws IOException {
      return null;
    }

    @Override
    public <T extends Service> T getService(Class<T> clazz) {
      return null;
    }

    @Override
    public IoSession getIoSession() {
      return null;
    }

    @Override
    public void resetIdleTimeout() {}

    @Override
    public TimeoutStatus getTimeoutStatus() {
      return null;
    }

    @Override
    public long getAuthTimeout() {
      return 0;
    }

    @Override
    public long getIdleTimeout() {
      return 0;
    }

    @Override
    public void setAuthenticated() throws IOException {}

    @Override
    public byte[] getSessionId() {
      return null;
    }

    @Override
    public KeyExchange getKex() {
      return null;
    }

    @Override
    public void disconnect(int reason, String msg) throws IOException {}

    @Override
    public void startService(String name) throws Exception {}

    @Override
    public List<NamedFactory<KeyExchange>> getKeyExchangeFactories() {
      return null;
    }

    @Override
    public void setKeyExchangeFactories(List<NamedFactory<KeyExchange>> keyExchangeFactories) {}

    @Override
    public List<NamedFactory<Cipher>> getCipherFactories() {
      return null;
    }

    @Override
    public void setCipherFactories(List<NamedFactory<Cipher>> cipherFactories) {}

    @Override
    public List<NamedFactory<Compression>> getCompressionFactories() {
      return null;
    }

    @Override
    public void setCompressionFactories(List<NamedFactory<Compression>> compressionFactories) {}

    @Override
    public List<NamedFactory<Mac>> getMacFactories() {
      return null;
    }

    @Override
    public void setMacFactories(List<NamedFactory<Mac>> macFactories) {}

    @Override
    public KeyPairProvider getKeyPairProvider() {
      return null;
    }

    @Override
    public void setKeyPairProvider(KeyPairProvider keyPairProvider) {}

    @Override
    public List<NamedFactory<Signature>> getSignatureFactories() {
      return null;
    }

    @Override
    public void setSignatureFactories(List<NamedFactory<Signature>> factories) {}

    @Override
    public void addSessionListener(SessionListener listener) {}

    @Override
    public void removeSessionListener(SessionListener listener) {}

    @Override
    public SessionListener getSessionListenerProxy() {
      return null;
    }

    @Override
    public ReservedSessionMessagesHandler getReservedSessionMessagesHandler() {
      return null;
    }

    @Override
    public void setReservedSessionMessagesHandler(ReservedSessionMessagesHandler handler) {}

    @Override
    public void addChannelListener(ChannelListener listener) {}

    @Override
    public void removeChannelListener(ChannelListener listener) {}

    @Override
    public ChannelListener getChannelListenerProxy() {
      return null;
    }

    @Override
    public void addPortForwardingEventListener(PortForwardingEventListener listener) {}

    @Override
    public void removePortForwardingEventListener(PortForwardingEventListener listener) {}

    @Override
    public PortForwardingEventListener getPortForwardingEventListenerProxy() {
      return null;
    }

    @Override
    public PropertyResolver getParentPropertyResolver() {
      return null;
    }

    @Override
    public CloseFuture close(boolean immediately) {
      return null;
    }

    @Override
    public void addCloseFutureListener(SshFutureListener<CloseFuture> listener) {}

    @Override
    public void removeCloseFutureListener(SshFutureListener<CloseFuture> listener) {}

    @Override
    public boolean isClosed() {
      return false;
    }

    @Override
    public boolean isClosing() {
      return false;
    }

    @Override
    public boolean isOpen() {
      return true;
    }

    @Override
    public void close() throws IOException {}

    @Override
    public void setUsername(String username) {}

    @Override
    public ServerProxyAcceptor getServerProxyAcceptor() {
      return null;
    }

    @Override
    public void setServerProxyAcceptor(ServerProxyAcceptor proxyAcceptor) {}

    @Override
    public List<NamedFactory<UserAuth>> getUserAuthFactories() {
      return null;
    }

    @Override
    public void setUserAuthFactories(List<NamedFactory<UserAuth>> userAuthFactories) {}

    @Override
    public PublickeyAuthenticator getPublickeyAuthenticator() {
      return null;
    }

    @Override
    public void setPasswordAuthenticator(PasswordAuthenticator passwordAuthenticator) {}

    @Override
    public PasswordAuthenticator getPasswordAuthenticator() {
      return null;
    }

    @Override
    public void setPublickeyAuthenticator(PublickeyAuthenticator publickeyAuthenticator) {}

    @Override
    public KeyboardInteractiveAuthenticator getKeyboardInteractiveAuthenticator() {
      return null;
    }

    @Override
    public void setKeyboardInteractiveAuthenticator(
        KeyboardInteractiveAuthenticator interactiveAuthenticator) {}

    @Override
    public GSSAuthenticator getGSSAuthenticator() {
      return null;
    }

    @Override
    public void setGSSAuthenticator(GSSAuthenticator gssAuthenticator) {}

    @Override
    public HostBasedAuthenticator getHostBasedAuthenticator() {
      return null;
    }

    @Override
    public void setHostBasedAuthenticator(HostBasedAuthenticator hostBasedAuthenticator) {}

    @Override
    public ServerFactoryManager getFactoryManager() {
      return null;
    }

    @Override
    public SocketAddress getClientAddress() {
      return null;
    }

    @Override
    public KeyPair getHostKey() {
      return null;
    }

    @Override
    public int getActiveSessionCountForUser(String userName) {
      return 1;
    }
  }
}
