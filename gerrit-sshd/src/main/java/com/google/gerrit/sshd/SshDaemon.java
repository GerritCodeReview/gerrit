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

package com.google.gerrit.sshd;

import static com.google.gerrit.server.ssh.SshAddressesModule.IANA_SSH_PORT;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.gerrit.common.Version;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.ssh.SshAdvertisedAddresses;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.ssh.SshListenAddresses;
import com.google.gerrit.server.util.IdGenerator;
import com.google.gerrit.server.util.SocketUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import com.jcraft.jsch.HostKey;
import com.jcraft.jsch.JSchException;

import org.apache.mina.core.future.IoFuture;
import org.apache.mina.core.future.IoFutureListener;
import org.apache.mina.core.service.IoAcceptor;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.sshd.SshServer;
import org.apache.sshd.common.Channel;
import org.apache.sshd.common.Cipher;
import org.apache.sshd.common.Compression;
import org.apache.sshd.common.KeyExchange;
import org.apache.sshd.common.KeyPairProvider;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.Session;
import org.apache.sshd.common.Signature;
import org.apache.sshd.common.cipher.AES128CBC;
import org.apache.sshd.common.cipher.AES192CBC;
import org.apache.sshd.common.cipher.AES256CBC;
import org.apache.sshd.common.cipher.BlowfishCBC;
import org.apache.sshd.common.cipher.CipherNone;
import org.apache.sshd.common.cipher.TripleDESCBC;
import org.apache.sshd.common.compression.CompressionNone;
import org.apache.sshd.common.mac.HMACMD5;
import org.apache.sshd.common.mac.HMACMD596;
import org.apache.sshd.common.mac.HMACSHA1;
import org.apache.sshd.common.mac.HMACSHA196;
import org.apache.sshd.common.random.BouncyCastleRandom;
import org.apache.sshd.common.random.JceRandom;
import org.apache.sshd.common.random.SingletonRandomFactory;
import org.apache.sshd.common.signature.SignatureDSA;
import org.apache.sshd.common.signature.SignatureRSA;
import org.apache.sshd.common.util.Buffer;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.FileSystemFactory;
import org.apache.sshd.server.FileSystemView;
import org.apache.sshd.server.ForwardingFilter;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.SshFile;
import org.apache.sshd.server.UserAuth;
import org.apache.sshd.server.auth.UserAuthPublicKey;
import org.apache.sshd.server.channel.ChannelDirectTcpip;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.kex.DHG1;
import org.apache.sshd.server.kex.DHG14;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.session.SessionFactory;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * SSH daemon to communicate with Gerrit.
 * <p>
 * Use a Git URL such as <code>ssh://${email}@${host}:${port}/${path}</code>,
 * e.g. <code>ssh://sop@google.com@gerrit.com:8010/tools/gerrit.git</code> to
 * access the SSH daemon itself.
 * <p>
 * Versions of Git before 1.5.3 may require setting the username and port
 * properties in the user's <code>~/.ssh/config</code> file, and using a host
 * alias through a URL such as <code>gerrit-alias:/tools/gerrit.git:
 * <pre>
 * Host gerrit-alias
 *  User sop@google.com
 *  Hostname gerrit.com
 *  Port 8010
 * </pre>
 */
@Singleton
public class SshDaemon extends SshServer implements SshInfo, LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(SshDaemon.class);

  private final List<SocketAddress> listen;
  private final List<String> advertised;
  private final boolean keepAlive;
  private final List<HostKey> hostKeys;
  private volatile IoAcceptor acceptor;

  @Inject
  SshDaemon(final CommandFactory commandFactory, final NoShell noShell,
      final PublickeyAuthenticator userAuth,
      final KeyPairProvider hostKeyProvider, final IdGenerator idGenerator,
      @GerritServerConfig final Config cfg, final SshLog sshLog,
      @SshListenAddresses final List<SocketAddress> listen,
      @SshAdvertisedAddresses final List<String> advertised) {
    setPort(IANA_SSH_PORT /* never used */);

    this.listen = listen;
    this.advertised = advertised;
    reuseAddress = cfg.getBoolean("sshd", "reuseaddress", true);
    keepAlive = cfg.getBoolean("sshd", "tcpkeepalive", true);

    getProperties().put(SERVER_IDENTIFICATION,
        "GerritCodeReview_" + Version.getVersion() //
            + " (" + super.getVersion() + ")");

    getProperties().put(MAX_AUTH_REQUESTS,
        String.valueOf(cfg.getInt("sshd", "maxAuthTries", 6)));

    getProperties().put(
        AUTH_TIMEOUT,
        String.valueOf(MILLISECONDS.convert(ConfigUtil.getTimeUnit(cfg, "sshd",
            null, "loginGraceTime", 120, SECONDS), SECONDS)));

    long idleTimeoutSeconds = ConfigUtil.getTimeUnit(cfg, "sshd", null,
        "idleTimeout", 0, SECONDS);
    if (idleTimeoutSeconds == 0) {
      // Since Apache SSHD does not allow to turn off closing idle connections,
      // we fake it by using the highest timeout allowed by Apache SSHD, which
      // amounts to ~24 days.
      idleTimeoutSeconds = MILLISECONDS.toSeconds(Integer.MAX_VALUE);
    }
    getProperties().put(
        IDLE_TIMEOUT,
        String.valueOf(SECONDS.toMillis(idleTimeoutSeconds)));

    final int maxConnectionsPerUser =
        cfg.getInt("sshd", "maxConnectionsPerUser", 64);
    if (0 < maxConnectionsPerUser) {
      getProperties().put(MAX_CONCURRENT_SESSIONS,
          String.valueOf(maxConnectionsPerUser));
    }

    if (SecurityUtils.isBouncyCastleRegistered()) {
      initProviderBouncyCastle();
    } else {
      initProviderJce();
    }
    initCiphers(cfg);
    initMacs(cfg);
    initSignatures();
    initChannels();
    initForwardingFilter();
    initFileSystemFactory();
    initSubsystems();
    initCompression();
    initUserAuth(userAuth);
    setKeyPairProvider(hostKeyProvider);
    setCommandFactory(commandFactory);
    setShellFactory(noShell);
    setSessionFactory(new SessionFactory() {
      @Override
      protected ServerSession createSession(final IoSession io)
          throws Exception {
        if (io.getConfig() instanceof SocketSessionConfig) {
          final SocketSessionConfig c = (SocketSessionConfig) io.getConfig();
          c.setKeepAlive(keepAlive);
        }

        final ServerSession s = (ServerSession) super.createSession(io);
        final int id = idGenerator.next();
        final SocketAddress peer = io.getRemoteAddress();
        final SshSession sd = new SshSession(id, peer);
        s.setAttribute(SshSession.KEY, sd);

        // Log a session close without authentication as a failure.
        //
        io.getCloseFuture().addListener(new IoFutureListener<IoFuture>() {
          @Override
          public void operationComplete(IoFuture future) {
            if (sd.isAuthenticationError()) {
              sshLog.onAuthFail(sd);
            }
          }
        });
        return s;
      }
    });

    hostKeys = computeHostKeys();
  }

  @Override
  public List<HostKey> getHostKeys() {
    return hostKeys;
  }

  public IoAcceptor getIoAcceptor() {
    return acceptor;
  }

  @Override
  public synchronized void start() {
    if (acceptor == null && !listen.isEmpty()) {
      checkConfig();

      acceptor = createAcceptor();
      configure(acceptor);

      final SessionFactory handler = getSessionFactory();
      handler.setServer(this);
      acceptor.setHandler(handler);

      try {
        acceptor.bind(listen);
      } catch (IOException e) {
        throw new IllegalStateException("Cannot bind to " + addressList(), e);
      }

      log.info("Started Gerrit SSHD on " + addressList());
    }
  }

  @Override
  public synchronized void stop() {
    if (acceptor != null) {
      try {
        acceptor.dispose();
        log.info("Stopped Gerrit SSHD");
      } finally {
        acceptor = null;
      }
    }
  }

  @Override
  protected void checkConfig() {
    super.checkConfig();
    if (myHostKeys().isEmpty()) {
      throw new IllegalStateException("No SSHD host key");
    }
  }

  private List<HostKey> computeHostKeys() {
    if (listen.isEmpty()) {
      return Collections.emptyList();
    }

    final List<PublicKey> keys = myHostKeys();
    final ArrayList<HostKey> r = new ArrayList<HostKey>();
    for (final PublicKey pub : keys) {
      final Buffer buf = new Buffer();
      buf.putRawPublicKey(pub);
      final byte[] keyBin = buf.getCompactData();

      for (final String addr : advertised) {
        try {
          r.add(new HostKey(addr, keyBin));
        } catch (JSchException e) {
          log.warn("Cannot format SSHD host key", e);
        }
      }
    }
    return Collections.unmodifiableList(r);
  }

  private List<PublicKey> myHostKeys() {
    final KeyPairProvider p = getKeyPairProvider();
    final List<PublicKey> keys = new ArrayList<PublicKey>(2);
    addPublicKey(keys, p, KeyPairProvider.SSH_RSA);
    addPublicKey(keys, p, KeyPairProvider.SSH_DSS);
    return keys;
  }

  private static void addPublicKey(final Collection<PublicKey> out,
      final KeyPairProvider p, final String type) {
    final KeyPair pair = p.loadKey(type);
    if (pair != null && pair.getPublic() != null) {
      out.add(pair.getPublic());
    }
  }

  private String addressList() {
    final StringBuilder r = new StringBuilder();
    for (Iterator<SocketAddress> i = listen.iterator(); i.hasNext();) {
      r.append(SocketUtil.format(i.next(), IANA_SSH_PORT));
      if (i.hasNext()) {
        r.append(", ");
      }
    }
    return r.toString();
  }

  @SuppressWarnings("unchecked")
  private void initProviderBouncyCastle() {
    setKeyExchangeFactories(Arrays.<NamedFactory<KeyExchange>> asList(
        new DHG14.Factory(), new DHG1.Factory()));
    setRandomFactory(new SingletonRandomFactory(
        new BouncyCastleRandom.Factory()));
  }

  @SuppressWarnings("unchecked")
  private void initProviderJce() {
    setKeyExchangeFactories(Arrays
        .<NamedFactory<KeyExchange>> asList(new DHG1.Factory()));
    setRandomFactory(new SingletonRandomFactory(new JceRandom.Factory()));
  }

  @SuppressWarnings("unchecked")
  private void initCiphers(final Config cfg) {
    final List<NamedFactory<Cipher>> a = new LinkedList<NamedFactory<Cipher>>();
    a.add(new AES128CBC.Factory());
    a.add(new TripleDESCBC.Factory());
    a.add(new BlowfishCBC.Factory());
    a.add(new AES192CBC.Factory());
    a.add(new AES256CBC.Factory());

    for (Iterator<NamedFactory<Cipher>> i = a.iterator(); i.hasNext();) {
      final NamedFactory<Cipher> f = i.next();
      try {
        final Cipher c = f.create();
        final byte[] key = new byte[c.getBlockSize()];
        final byte[] iv = new byte[c.getIVSize()];
        c.init(Cipher.Mode.Encrypt, key, iv);
      } catch (InvalidKeyException e) {
        log.warn("Disabling cipher " + f.getName() + ": " + e.getMessage()
            + "; try installing unlimited cryptography extension");
        i.remove();
      } catch (Exception e) {
        log.warn("Disabling cipher " + f.getName() + ": " + e.getMessage());
        i.remove();
      }
    }

    a.add(null);
    a.add(new CipherNone.Factory());
    setCipherFactories(filter(cfg, "cipher", a.toArray(new NamedFactory[a
        .size()])));
  }

  @SuppressWarnings("unchecked")
  private void initMacs(final Config cfg) {
    setMacFactories(filter(cfg, "mac", new HMACMD5.Factory(),
        new HMACSHA1.Factory(), new HMACMD596.Factory(),
        new HMACSHA196.Factory()));
  }

  private static <T> List<NamedFactory<T>> filter(final Config cfg,
      final String key, final NamedFactory<T>... avail) {
    final ArrayList<NamedFactory<T>> def = new ArrayList<NamedFactory<T>>();
    for (final NamedFactory<T> n : avail) {
      if (n == null) {
        break;
      }
      def.add(n);
    }

    final String[] want = cfg.getStringList("sshd", null, key);
    if (want == null || want.length == 0) {
      return def;
    }

    boolean didClear = false;
    for (final String setting : want) {
      String name = setting.trim();
      boolean add = true;
      if (name.startsWith("-")) {
        add = false;
        name = name.substring(1).trim();
      } else if (name.startsWith("+")) {
        name = name.substring(1).trim();
      } else if (!didClear) {
        didClear = true;
        def.clear();
      }

      final NamedFactory<T> n = find(name, avail);
      if (n == null) {
        final StringBuilder msg = new StringBuilder();
        msg.append("sshd." + key + " = " + name + " unsupported; only ");
        for (int i = 0; i < avail.length; i++) {
          if (avail[i] == null) {
            continue;
          }
          if (i > 0) {
            msg.append(", ");
          }
          msg.append(avail[i].getName());
        }
        msg.append(" is supported");
        log.error(msg.toString());
      } else if (add) {
        if (!def.contains(n)) {
          def.add(n);
        }
      } else {
        def.remove(n);
      }
    }

    return def;
  }

  private static <T> NamedFactory<T> find(final String name,
      final NamedFactory<T>... avail) {
    for (final NamedFactory<T> n : avail) {
      if (n != null && name.equals(n.getName())) {
        return n;
      }
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private void initSignatures() {
    setSignatureFactories(Arrays.<NamedFactory<Signature>> asList(
        new SignatureDSA.Factory(), new SignatureRSA.Factory()));
  }

  @SuppressWarnings("unchecked")
  private void initCompression() {
    // Always disable transparent compression. The majority of our data
    // transfer is highly compressed Git pack files. We cannot make them
    // any smaller than they already are.
    //
    setCompressionFactories(Arrays
        .<NamedFactory<Compression>> asList(new CompressionNone.Factory()));
  }

  @SuppressWarnings("unchecked")
  private void initChannels() {
    setChannelFactories(Arrays.<NamedFactory<Channel>> asList(
        new ChannelSession.Factory(), //
        new ChannelDirectTcpip.Factory() //
        ));
  }

  private void initSubsystems() {
    setSubsystemFactories(Collections.<NamedFactory<Command>> emptyList());
  }

  @SuppressWarnings("unchecked")
  private void initUserAuth(final PublickeyAuthenticator pubkey) {
    setUserAuthFactories(Arrays
        .<NamedFactory<UserAuth>> asList(new UserAuthPublicKey.Factory()));
    setPublickeyAuthenticator(pubkey);
  }

  private void initForwardingFilter() {
    setForwardingFilter(new ForwardingFilter() {
      @Override
      public boolean canForwardAgent(ServerSession session) {
        return false;
      }

      @Override
      public boolean canForwardX11(ServerSession session) {
        return false;
      }

      @Override
      public boolean canConnect(InetSocketAddress address, ServerSession session) {
        return false;
      }

      @Override
      public boolean canListen(InetSocketAddress address, ServerSession session) {
        return false;
      }
    });
  }

  private void initFileSystemFactory() {
    setFileSystemFactory(new FileSystemFactory() {
      @Override
      public FileSystemView createFileSystemView(Session session)
          throws IOException {
        return new FileSystemView() {
          @Override
          public SshFile getFile(SshFile baseDir, String file) {
            return null;
          }

          @Override
          public SshFile getFile(String file) {
            return null;
          }};
      }
    });
  }
}
