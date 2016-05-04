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

import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.Version;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
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

import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.sshd.common.BaseBuilder;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.channel.RequestHandler;
import org.apache.sshd.common.cipher.Cipher;
import org.apache.sshd.common.compression.BuiltinCompressions;
import org.apache.sshd.common.compression.Compression;
import org.apache.sshd.common.file.FileSystemFactory;
import org.apache.sshd.common.forward.DefaultTcpipForwarderFactory;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.io.IoServiceFactoryFactory;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.io.mina.MinaServiceFactoryFactory;
import org.apache.sshd.common.io.mina.MinaSession;
import org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory;
import org.apache.sshd.common.kex.BuiltinDHFactories;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.mac.Mac;
import org.apache.sshd.common.random.JceRandomFactory;
import org.apache.sshd.common.random.Random;
import org.apache.sshd.common.random.SingletonRandomFactory;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.ServerBuilder;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.UserAuth;
import org.apache.sshd.server.auth.gss.GSSAuthenticator;
import org.apache.sshd.server.auth.gss.UserAuthGSSFactory;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.server.forward.ForwardingFilter;
import org.apache.sshd.server.global.CancelTcpipForwardHandler;
import org.apache.sshd.server.global.KeepAliveHandler;
import org.apache.sshd.server.global.NoMoreSessionsHandler;
import org.apache.sshd.server.global.TcpipForwardHandler;
import org.apache.sshd.server.session.ServerSessionImpl;
import org.apache.sshd.server.session.SessionFactory;
import org.bouncycastle.crypto.prng.RandomGenerator;
import org.bouncycastle.crypto.prng.VMPCRandomGenerator;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSH daemon to communicate with Gerrit.
 * <p>
 * Use a Git URL such as <code>ssh://${email}@${host}:${port}/${path}</code>,
 * e.g. {@code ssh://sop@google.com@gerrit.com:8010/tools/gerrit.git} to
 * access the SSH daemon itself.
 * <p>
 * Versions of Git before 1.5.3 may require setting the username and port
 * properties in the user's {@code ~/.ssh/config} file, and using a host
 * alias through a URL such as {@code gerrit-alias:/tools/gerrit.git}:
 * <pre>
 * {@code
 * Host gerrit-alias
 *  User sop@google.com
 *  Hostname gerrit.com
 *  Port 8010
 * }
 * </pre>
 */
@Singleton
public class SshDaemon extends SshServer implements SshInfo, LifecycleListener {
  private static final Logger sshDaemonLog =
      LoggerFactory.getLogger(SshDaemon.class);

  public enum SshSessionBackend {
    MINA,
    NIO2
  }

  private final List<SocketAddress> listen;
  private final List<String> advertised;
  private final boolean keepAlive;
  private final List<HostKey> hostKeys;
  private volatile IoAcceptor daemonAcceptor;
  private final Config cfg;

  @Inject
  SshDaemon(final CommandFactory commandFactory, final NoShell noShell,
      final PublickeyAuthenticator userAuth,
      final GerritGSSAuthenticator kerberosAuth,
      final KeyPairProvider hostKeyProvider, final IdGenerator idGenerator,
      @GerritServerConfig final Config cfg, final SshLog sshLog,
      @SshListenAddresses final List<SocketAddress> listen,
      @SshAdvertisedAddresses final List<String> advertised,
      MetricMaker metricMaker) {
    setPort(IANA_SSH_PORT /* never used */);

    this.cfg = cfg;
    this.listen = listen;
    this.advertised = advertised;
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
    getProperties().put(
        IDLE_TIMEOUT,
        String.valueOf(SECONDS.toMillis(idleTimeoutSeconds)));

    long rekeyTimeLimit = ConfigUtil.getTimeUnit(cfg, "sshd", null,
        "rekeyTimeLimit", 3600, SECONDS);
    getProperties().put(
        REKEY_TIME_LIMIT,
        String.valueOf(SECONDS.toMillis(rekeyTimeLimit)));

    getProperties().put(REKEY_BYTES_LIMIT,
        String.valueOf(cfg.getLong("sshd", "rekeyBytesLimit", 1024 * 1024 * 1024 /* 1GB */)));

    final int maxConnectionsPerUser =
        cfg.getInt("sshd", "maxConnectionsPerUser", 64);
    if (0 < maxConnectionsPerUser) {
      getProperties().put(MAX_CONCURRENT_SESSIONS,
          String.valueOf(maxConnectionsPerUser));
    }

    final String kerberosKeytab = cfg.getString(
        "sshd", null, "kerberosKeytab");
    final String kerberosPrincipal = cfg.getString(
        "sshd", null, "kerberosPrincipal");

    final boolean enableCompression = cfg.getBoolean(
        "sshd", "enableCompression", false);

    SshSessionBackend backend = cfg.getEnum(
        "sshd", null, "backend", SshSessionBackend.NIO2);

    System.setProperty(IoServiceFactoryFactory.class.getName(),
        backend == SshSessionBackend.MINA
            ? MinaServiceFactoryFactory.class.getName()
            : Nio2ServiceFactoryFactory.class.getName());

    if (SecurityUtils.isBouncyCastleRegistered()) {
      initProviderBouncyCastle(cfg);
    } else {
      initProviderJce();
    }
    initCiphers(cfg);
    initMacs(cfg);
    initSignatures();
    initChannels();
    initForwarding();
    initFileSystemFactory();
    initSubsystems();
    initCompression(enableCompression);
    initUserAuth(userAuth, kerberosAuth, kerberosKeytab, kerberosPrincipal);
    setKeyPairProvider(hostKeyProvider);
    setCommandFactory(commandFactory);
    setShellFactory(noShell);

    final AtomicInteger connected = new AtomicInteger();
    metricMaker.newCallbackMetric(
        "sshd/sessions/connected",
        Integer.class,
        new Description("Currently connected SSH sessions")
          .setGauge()
          .setUnit("sessions"),
        new Supplier<Integer>() {
          @Override
          public Integer get() {
            return connected.get();
          }
        });

    final Counter0 sessionsCreated = metricMaker.newCounter(
        "sshd/sessions/created",
        new Description("Rate of new SSH sessions")
          .setRate()
          .setUnit("sessions"));

    final Counter0 authFailures = metricMaker.newCounter(
        "sshd/sessions/authentication_failures",
        new Description("Rate of SSH authentication failures")
          .setRate()
          .setUnit("failures"));

    setSessionFactory(new SessionFactory(this) {
      @Override
      protected ServerSessionImpl createSession(final IoSession io)
          throws Exception {
        connected.incrementAndGet();
        sessionsCreated.increment();
        if (io instanceof MinaSession) {
          if (((MinaSession) io).getSession()
              .getConfig() instanceof SocketSessionConfig) {
            ((SocketSessionConfig) ((MinaSession) io).getSession()
                .getConfig())
                .setKeepAlive(keepAlive);
          }
        }

        ServerSessionImpl s = super.createSession(io);
        int id = idGenerator.next();
        SocketAddress peer = io.getRemoteAddress();
        final SshSession sd = new SshSession(id, peer);
        s.setAttribute(SshSession.KEY, sd);

        // Log a session close without authentication as a failure.
        //
        s.addCloseFutureListener(new SshFutureListener<CloseFuture>() {
          @Override
          public void operationComplete(CloseFuture future) {
            connected.decrementAndGet();
            if (sd.isAuthenticationError()) {
              authFailures.increment();
              sshLog.onAuthFail(sd);
            }
          }
        });
        return s;
      }

      @Override
      protected ServerSessionImpl doCreateSession(IoSession ioSession)
          throws Exception {
        return new ServerSessionImpl(getServer(), ioSession);
      }
    });
    setGlobalRequestHandlers(Arrays.<RequestHandler<ConnectionService>> asList(
          new KeepAliveHandler(),
          new NoMoreSessionsHandler(),
          new TcpipForwardHandler(),
          new CancelTcpipForwardHandler()
        ));

    hostKeys = computeHostKeys();
  }

  @Override
  public List<HostKey> getHostKeys() {
    return hostKeys;
  }

  public IoAcceptor getIoAcceptor() {
    return daemonAcceptor;
  }

  @Override
  public synchronized void start() {
    if (daemonAcceptor == null && !listen.isEmpty()) {
      checkConfig();
      if (getSessionFactory() == null) {
        setSessionFactory(createSessionFactory());
      }
      daemonAcceptor = createAcceptor();

      try {
        String listenAddress = cfg.getString("sshd", null, "listenAddress");
        boolean rewrite = !Strings.isNullOrEmpty(listenAddress)
            && listenAddress.endsWith(":0");
        daemonAcceptor.bind(listen);
        if (rewrite) {
          SocketAddress bound = Iterables.getOnlyElement(daemonAcceptor.getBoundAddresses());
          cfg.setString("sshd", null, "listenAddress", format((InetSocketAddress)bound));
        }
      } catch (IOException e) {
        throw new IllegalStateException("Cannot bind to " + addressList(), e);
      }

      sshDaemonLog.info(String.format("Started Gerrit %s on %s",
          getVersion(), addressList()));
    }
  }

  private static String format(InetSocketAddress s) {
    return String.format("%s:%d", s.getAddress().getHostAddress(), s.getPort());
  }

  @Override
  public synchronized void stop() {
    if (daemonAcceptor != null) {
      try {
        daemonAcceptor.close(true).await();
        sshDaemonLog.info("Stopped Gerrit SSHD");
      } catch (IOException e) {
        sshDaemonLog.warn("Exception caught while closing", e);
      } finally {
        daemonAcceptor = null;
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
    final List<HostKey> r = new ArrayList<>();
    for (final PublicKey pub : keys) {
      final Buffer buf = new ByteArrayBuffer();
      buf.putRawPublicKey(pub);
      final byte[] keyBin = buf.getCompactData();

      for (final String addr : advertised) {
        try {
          r.add(new HostKey(addr, keyBin));
        } catch (JSchException e) {
          sshDaemonLog.warn("Cannot format SSHD host key", e);
        }
      }
    }
    return Collections.unmodifiableList(r);
  }

  private List<PublicKey> myHostKeys() {
    final KeyPairProvider p = getKeyPairProvider();
    final List<PublicKey> keys = new ArrayList<>(2);
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

  private void initProviderBouncyCastle(Config cfg) {
    setKeyExchangeFactories(
        NamedFactory.Utils.setUpTransformedFactories(true,
            Collections.unmodifiableList(Arrays.asList(
                BuiltinDHFactories.dhg14,
                BuiltinDHFactories.dhg1
            )),
        ServerBuilder.DH2KEX));
    NamedFactory<Random> factory;
    if (cfg.getBoolean("sshd", null, "testUseInsecureRandom", false)) {
      factory = new InsecureBouncyCastleRandom.Factory();
    } else {
      factory = SecurityUtils.getRandomFactory();
    }
    setRandomFactory(new SingletonRandomFactory(factory));
  }

  private static class InsecureBouncyCastleRandom implements Random {
    private static class Factory implements NamedFactory<Random> {
      @Override
      public String getName() {
        return "INSECURE_bouncycastle";
      }

      @Override
      public Random create() {
        return new InsecureBouncyCastleRandom();
      }
    }

    private final RandomGenerator random;

    private InsecureBouncyCastleRandom() {
      random = new VMPCRandomGenerator();
      random.addSeedMaterial(1234);
    }

    @Override
    public String getName() {
      return "InsecureBouncyCastleRandom";
    }

    @Override
    public void fill(byte[] bytes, int start, int len) {
      random.nextBytes(bytes, start, len);
    }

    @Override
    public void fill(byte[] bytes) {
      random.nextBytes(bytes);
    }

    @Override
    public int random(int n) {
      if (n > 0) {
        if ((n & -n) == n) {
          return (int)((n * (long) next(31)) >> 31);
        }
        int bits;
        int val;
        do {
          bits = next(31);
          val = bits % n;
        } while (bits - val + (n - 1) < 0);
        return val;
      }
      throw new IllegalArgumentException();
    }

    protected final int next(int numBits) {
      int bytes = (numBits + 7) / 8;
      byte[] next = new byte[bytes];
      int ret = 0;
      random.nextBytes(next);
      for (int i = 0; i < bytes; i++) {
        ret = (next[i] & 0xFF) | (ret << 8);
      }
      return ret >>> (bytes * 8 - numBits);
    }
  }

  private void initProviderJce() {
    setKeyExchangeFactories(
        NamedFactory.Utils.setUpTransformedFactories(true,
            Collections.unmodifiableList(Arrays.asList(
                BuiltinDHFactories.dhg1
            )),
        ServerBuilder.DH2KEX));
    setKeyExchangeFactories(ServerBuilder.setUpDefaultKeyExchanges(true));
    setRandomFactory(new SingletonRandomFactory(JceRandomFactory.INSTANCE));
  }

  @SuppressWarnings("unchecked")
  private void initCiphers(final Config cfg) {
    final List<NamedFactory<Cipher>> a = BaseBuilder.setUpDefaultCiphers(true);

    for (Iterator<NamedFactory<Cipher>> i = a.iterator(); i.hasNext();) {
      final NamedFactory<Cipher> f = i.next();
      try {
        final Cipher c = f.create();
        final byte[] key = new byte[c.getBlockSize()];
        final byte[] iv = new byte[c.getIVSize()];
        c.init(Cipher.Mode.Encrypt, key, iv);
      } catch (InvalidKeyException e) {
        sshDaemonLog.warn("Disabling cipher " + f.getName() + ": " + e.getMessage()
            + "; try installing unlimited cryptography extension");
        i.remove();
      } catch (Exception e) {
        sshDaemonLog.warn("Disabling cipher " + f.getName() + ": " + e.getMessage());
        i.remove();
      }
    }

    a.add(null);
    setCipherFactories(filter(cfg, "cipher",
        (NamedFactory<Cipher>[])a.toArray(new NamedFactory[a.size()])));
  }

  @SuppressWarnings("unchecked")
  private void initMacs(Config cfg) {
    List<NamedFactory<Mac>> m = BaseBuilder.setUpDefaultMacs(true);
    setMacFactories(filter(cfg, "mac",
       (NamedFactory<Mac>[]) m.toArray(new NamedFactory[m.size()])));
  }

  @SafeVarargs
  private static <T> List<NamedFactory<T>> filter(final Config cfg,
      final String key, final NamedFactory<T>... avail) {
    final ArrayList<NamedFactory<T>> def = new ArrayList<>();
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
        msg.append("sshd.").append(key).append(" = ").append(name)
           .append(" unsupported; only ");
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
        sshDaemonLog.error(msg.toString());
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

  @SafeVarargs
  private static <T> NamedFactory<T> find(final String name,
      final NamedFactory<T>... avail) {
    for (final NamedFactory<T> n : avail) {
      if (n != null && name.equals(n.getName())) {
        return n;
      }
    }
    return null;
  }

  private void initSignatures() {
    setSignatureFactories(BaseBuilder.setUpDefaultSignatures(true));
  }

  private void initCompression(boolean enableCompression) {
    List<NamedFactory<Compression>> compressionFactories = new ArrayList<>();

    // Always support no compression over SSHD.
    compressionFactories.add(BuiltinCompressions.none);

    // In the general case, we want to disable transparent compression, since
    // the majority of our data transfer is highly compressed Git pack files
    // and we cannot make them any smaller than they already are.
    //
    // However, if there are CPU in abundance and the server is reachable through
    // slow networks, gits with huge amount of refs can benefit from SSH-compression
    // since git does not compress the ref announcement during the handshake.
    //
    // Compression can be especially useful when Gerrit slaves are being used
    // for the larger clones and fetches and the master server mostly takes small
    // receive-packs.

    if (enableCompression) {
      compressionFactories.add(BuiltinCompressions.zlib);
    }

    setCompressionFactories(compressionFactories);
  }

  private void initChannels() {
    setChannelFactories(ServerBuilder.DEFAULT_CHANNEL_FACTORIES);
  }

  private void initSubsystems() {
    setSubsystemFactories(Collections.<NamedFactory<Command>> emptyList());
  }

  private void initUserAuth(final PublickeyAuthenticator pubkey,
      final GSSAuthenticator kerberosAuthenticator,
      String kerberosKeytab, String kerberosPrincipal) {
    List<NamedFactory<UserAuth>> authFactories = new ArrayList<>();
    if (kerberosKeytab != null) {
      authFactories.add(UserAuthGSSFactory.INSTANCE);
      log.info("Enabling kerberos with keytab " + kerberosKeytab);
      if (!new File(kerberosKeytab).canRead()) {
        sshDaemonLog.error("Keytab " + kerberosKeytab +
            " does not exist or is not readable; further errors are possible");
      }
      kerberosAuthenticator.setKeytabFile(kerberosKeytab);
      if (kerberosPrincipal == null) {
        try {
          kerberosPrincipal = "host/" +
              InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
          kerberosPrincipal = "host/localhost";
        }
      }
      sshDaemonLog.info("Using kerberos principal " + kerberosPrincipal);
      if (!kerberosPrincipal.startsWith("host/")) {
        sshDaemonLog.warn("Host principal does not start with host/ " +
            "which most SSH clients will supply automatically");
      }
      kerberosAuthenticator.setServicePrincipalName(kerberosPrincipal);
      setGSSAuthenticator(kerberosAuthenticator);
    }
    authFactories.add(UserAuthPublicKeyFactory.INSTANCE);
    setUserAuthFactories(authFactories);
    setPublickeyAuthenticator(pubkey);
  }

  private void initForwarding() {
    setTcpipForwardingFilter(new ForwardingFilter() {
      @Override
      public boolean canForwardAgent(Session session) {
          return false;
      }

      @Override
      public boolean canForwardX11(Session session) {
          return false;
      }

      @Override
      public boolean canListen(SshdSocketAddress address, Session session) {
          return false;
      }

      @Override
      public boolean canConnect(Type type, SshdSocketAddress address, Session session) {
          return false;
      }
    });
    setTcpipForwarderFactory(new DefaultTcpipForwarderFactory());
  }

  private void initFileSystemFactory() {
    setFileSystemFactory(new FileSystemFactory() {
      @Override
      public FileSystem createFileSystem(Session session)
          throws IOException {
        return new FileSystem() {
          @Override
          public void close() throws IOException {
          }

          @Override
          public Iterable<FileStore> getFileStores() {
            return null;
          }

          @Override
          public Path getPath(String arg0, String... arg1) {
            return null;
          }

          @Override
          public PathMatcher getPathMatcher(String arg0) {
            return null;
          }

          @Override
          public Iterable<Path> getRootDirectories() {
            return null;
          }

          @Override
          public String getSeparator() {
            return null;
          }

          @Override
          public UserPrincipalLookupService getUserPrincipalLookupService() {
            return null;
          }

          @Override
          public boolean isOpen() {
            return false;
          }

          @Override
          public boolean isReadOnly() {
            return false;
          }

          @Override
          public WatchService newWatchService() throws IOException {
            return null;
          }

          @Override
          public FileSystemProvider provider() {
            return null;
          }

          @Override
          public Set<String> supportedFileAttributeViews() {
            return null;
          }
        };
      }
    });
  }
}
