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
import static org.apache.sshd.core.CoreModuleProperties.AUTH_TIMEOUT;
import static org.apache.sshd.core.CoreModuleProperties.IDLE_TIMEOUT;
import static org.apache.sshd.core.CoreModuleProperties.MAX_AUTH_REQUESTS;
import static org.apache.sshd.core.CoreModuleProperties.MAX_CONCURRENT_SESSIONS;
import static org.apache.sshd.core.CoreModuleProperties.NIO2_READ_TIMEOUT;
import static org.apache.sshd.core.CoreModuleProperties.REKEY_BYTES_LIMIT;
import static org.apache.sshd.core.CoreModuleProperties.REKEY_TIME_LIMIT;
import static org.apache.sshd.core.CoreModuleProperties.SERVER_IDENTIFICATION;
import static org.apache.sshd.core.CoreModuleProperties.WAIT_FOR_SPACE_TIMEOUT;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
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
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.mina.transport.socket.SocketSessionConfig;
import org.apache.sshd.common.BaseBuilder;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.cipher.Cipher;
import org.apache.sshd.common.compression.BuiltinCompressions;
import org.apache.sshd.common.compression.Compression;
import org.apache.sshd.common.file.nonefs.NoneFileSystemFactory;
import org.apache.sshd.common.forward.DefaultForwarderFactory;
import org.apache.sshd.common.io.AbstractIoServiceFactory;
import org.apache.sshd.common.io.IoAcceptor;
import org.apache.sshd.common.io.IoServiceFactory;
import org.apache.sshd.common.io.IoServiceFactoryFactory;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.io.nio2.Nio2ServiceFactoryFactory;
import org.apache.sshd.common.kex.KeyExchangeFactory;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.mac.Mac;
import org.apache.sshd.common.random.Random;
import org.apache.sshd.common.random.SingletonRandomFactory;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.helpers.DefaultUnknownChannelReferenceHandler;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.buffer.ByteArrayBuffer;
import org.apache.sshd.common.util.net.SshdSocketAddress;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.mina.MinaServiceFactoryFactory;
import org.apache.sshd.mina.MinaSession;
import org.apache.sshd.server.ServerBuilder;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.UserAuthFactory;
import org.apache.sshd.server.auth.gss.GSSAuthenticator;
import org.apache.sshd.server.auth.gss.UserAuthGSSFactory;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.server.command.CommandFactory;
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

/**
 * SSH daemon to communicate with Gerrit.
 *
 * <p>Use a Git URL such as <code>ssh://${email}@${host}:${port}/${path}</code>, e.g. {@code
 * ssh://sop@google.com@gerrit.com:8010/tools/gerrit.git} to access the SSH daemon itself.
 *
 * <p>Versions of Git before 1.5.3 may require setting the username and port properties in the
 * user's {@code ~/.ssh/config} file, and using a host alias through a URL such as {@code
 * gerrit-alias:/tools/gerrit.git}:
 *
 * <pre>{@code
 * Host gerrit-alias
 *  User sop@google.com
 *  Hostname gerrit.com
 *  Port 8010
 * }</pre>
 */
@Singleton
public class SshDaemon extends SshServer implements SshInfo, LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

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
  SshDaemon(
      CommandFactory commandFactory,
      NoShell noShell,
      PublickeyAuthenticator userAuth,
      GerritGSSAuthenticator kerberosAuth,
      KeyPairProvider hostKeyProvider,
      IdGenerator idGenerator,
      @GerritServerConfig Config cfg,
      SshLog sshLog,
      @SshListenAddresses List<SocketAddress> listen,
      @SshAdvertisedAddresses List<String> advertised,
      MetricMaker metricMaker) {
    setPort(IANA_SSH_PORT /* never used */);

    this.cfg = cfg;
    this.listen = listen;
    this.advertised = advertised;
    keepAlive = cfg.getBoolean("sshd", "tcpkeepalive", true);

    SERVER_IDENTIFICATION.set(
        this,
        "GerritCodeReview_"
            + Version.getVersion() //
            + " ("
            + super.getVersion()
            + ")");
    MAX_AUTH_REQUESTS.set(this, cfg.getInt("sshd", "maxAuthTries", 6));
    AUTH_TIMEOUT.set(
        this,
        Duration.ofSeconds(
            MILLISECONDS.convert(
                ConfigUtil.getTimeUnit(cfg, "sshd", null, "loginGraceTime", 120, SECONDS),
                SECONDS)));

    long idleTimeoutSeconds = ConfigUtil.getTimeUnit(cfg, "sshd", null, "idleTimeout", 0, SECONDS);
    IDLE_TIMEOUT.set(this, Duration.ofSeconds(SECONDS.toMillis(idleTimeoutSeconds)));
    NIO2_READ_TIMEOUT.set(this, Duration.ofSeconds(SECONDS.toMillis(idleTimeoutSeconds)));

    long rekeyTimeLimit =
        ConfigUtil.getTimeUnit(cfg, "sshd", null, "rekeyTimeLimit", 3600, SECONDS);
    REKEY_TIME_LIMIT.set(this, Duration.ofSeconds(SECONDS.toMillis(rekeyTimeLimit)));

    REKEY_BYTES_LIMIT.set(
        this, cfg.getLong("sshd", "rekeyBytesLimit", 1024 * 1024 * 1024 /* 1GB */));

    long waitTimeoutSeconds = ConfigUtil.getTimeUnit(cfg, "sshd", null, "waitTimeout", 30, SECONDS);
    WAIT_FOR_SPACE_TIMEOUT.set(this, Duration.ofSeconds(SECONDS.toMillis(waitTimeoutSeconds)));

    final int maxConnectionsPerUser = cfg.getInt("sshd", "maxConnectionsPerUser", 64);
    if (0 < maxConnectionsPerUser) {
      MAX_CONCURRENT_SESSIONS.set(this, maxConnectionsPerUser);
    }

    final String kerberosKeytab = cfg.getString("sshd", null, "kerberosKeytab");
    final String kerberosPrincipal = cfg.getString("sshd", null, "kerberosPrincipal");

    final boolean enableCompression = cfg.getBoolean("sshd", "enableCompression", false);

    SshSessionBackend backend = cfg.getEnum("sshd", null, "backend", SshSessionBackend.NIO2);
    boolean channelIdTracking = cfg.getBoolean("sshd", "enableChannelIdTracking", true);

    System.setProperty(
        IoServiceFactoryFactory.class.getName(),
        backend == SshSessionBackend.MINA
            ? MinaServiceFactoryFactory.class.getName()
            : Nio2ServiceFactoryFactory.class.getName());

    initProviderBouncyCastle(cfg);
    initCiphers(cfg);
    initKeyExchanges(cfg);
    initMacs(cfg);
    initSignatures();
    initChannels();
    initUnknownChannelReferenceHandler(channelIdTracking);
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
        new Description("Currently connected SSH sessions").setGauge().setUnit("sessions"),
        connected::get);

    final Counter0 sessionsCreated =
        metricMaker.newCounter(
            "sshd/sessions/created",
            new Description("Rate of new SSH sessions").setRate().setUnit("sessions"));

    final Counter0 authFailures =
        metricMaker.newCounter(
            "sshd/sessions/authentication_failures",
            new Description("Rate of SSH authentication failures").setRate().setUnit("failures"));

    setSessionFactory(
        new SessionFactory(this) {
          @Override
          protected ServerSessionImpl createSession(IoSession io) throws Exception {
            connected.incrementAndGet();
            sessionsCreated.increment();
            if (io instanceof MinaSession) {
              if (((MinaSession) io).getSession().getConfig() instanceof SocketSessionConfig) {
                ((SocketSessionConfig) ((MinaSession) io).getSession().getConfig())
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
            s.addCloseFutureListener(
                future -> {
                  connected.decrementAndGet();
                  if (sd.isAuthenticationError()) {
                    authFailures.increment();
                    sshLog.onAuthFail(sd);
                  }
                });
            return s;
          }

          @Override
          protected ServerSessionImpl doCreateSession(IoSession ioSession) throws Exception {
            return new ServerSessionImpl(getServer(), ioSession);
          }
        });
    setGlobalRequestHandlers(
        Arrays.asList(
            new KeepAliveHandler(),
            new NoMoreSessionsHandler(),
            new TcpipForwardHandler(),
            new CancelTcpipForwardHandler()));

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
      setupSessionTimeout(getSessionFactory());
      daemonAcceptor = createAcceptor();

      try {
        String listenAddress = cfg.getString("sshd", null, "listenAddress");
        boolean rewrite = !Strings.isNullOrEmpty(listenAddress) && listenAddress.endsWith(":0");
        daemonAcceptor.bind(listen);
        if (rewrite) {
          SocketAddress bound = Iterables.getOnlyElement(daemonAcceptor.getBoundAddresses());
          cfg.setString("sshd", null, "listenAddress", format((InetSocketAddress) bound));
        }
      } catch (IOException e) {
        throw new IllegalStateException("Cannot bind to " + addressList(), e);
      }

      logger.atInfo().log("Started Gerrit %s on %s", getVersion(), addressList());
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
        shutdownExecutors();
        logger.atInfo().log("Stopped Gerrit SSHD");
      } catch (IOException e) {
        logger.atWarning().withCause(e).log("Exception caught while closing");
      } finally {
        daemonAcceptor = null;
      }
    }
  }

  private void shutdownExecutors() {
    if (executor != null) {
      executor.shutdownNow();
    }

    IoServiceFactory serviceFactory = getIoServiceFactory();
    if (serviceFactory instanceof AbstractIoServiceFactory) {
      shutdownServiceFactoryExecutor((AbstractIoServiceFactory) serviceFactory);
    }
  }

  private void shutdownServiceFactoryExecutor(AbstractIoServiceFactory ioServiceFactory) {
    ioServiceFactory.close(true);
    ExecutorService serviceFactoryExecutor = ioServiceFactory.getExecutorService();
    if (serviceFactoryExecutor != null && serviceFactoryExecutor != executor) {
      serviceFactoryExecutor.shutdownNow();
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

    List<HostKey> r = new ArrayList<>();
    List<PublicKey> keys = myHostKeys();
    for (PublicKey pub : keys) {
      Buffer buf = new ByteArrayBuffer();
      buf.putRawPublicKey(pub);
      byte[] keyBin = buf.getCompactData();

      for (String addr : advertised) {
        try {
          r.add(new HostKey(addr, keyBin));
        } catch (JSchException e) {
          logger.atWarning().log(
              "Cannot format SSHD host key [%s]: %s", pub.getAlgorithm(), e.getMessage());
        }
      }
    }

    return Collections.unmodifiableList(r);
  }

  private List<PublicKey> myHostKeys() {
    KeyPairProvider p = getKeyPairProvider();
    List<PublicKey> keys = new ArrayList<>(6);
    try {
      addPublicKey(keys, p, KeyPairProvider.SSH_ED25519);
      addPublicKey(keys, p, KeyPairProvider.ECDSA_SHA2_NISTP256);
      addPublicKey(keys, p, KeyPairProvider.ECDSA_SHA2_NISTP384);
      addPublicKey(keys, p, KeyPairProvider.ECDSA_SHA2_NISTP521);
      addPublicKey(keys, p, KeyPairProvider.SSH_RSA);
      addPublicKey(keys, p, KeyPairProvider.SSH_DSS);
    } catch (IOException | GeneralSecurityException e) {
      throw new IllegalStateException("Cannot load SSHD host key", e);
    }
    return keys;
  }

  private static void addPublicKey(final Collection<PublicKey> out, KeyPairProvider p, String type)
      throws IOException, GeneralSecurityException {
    final KeyPair pair = p.loadKey(null, type);
    if (pair != null && pair.getPublic() != null) {
      out.add(pair.getPublic());
    }
  }

  private String addressList() {
    final StringBuilder r = new StringBuilder();
    for (Iterator<SocketAddress> i = listen.iterator(); i.hasNext(); ) {
      r.append(SocketUtil.format(i.next(), IANA_SSH_PORT));
      if (i.hasNext()) {
        r.append(", ");
      }
    }
    return r.toString();
  }

  private void initKeyExchanges(Config cfg) {
    List<KeyExchangeFactory> a = ServerBuilder.setUpDefaultKeyExchanges(true);
    setKeyExchangeFactories(filter(cfg, "kex", a.toArray(new KeyExchangeFactory[a.size()])));
  }

  private void initProviderBouncyCastle(Config cfg) {
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
          return (int) ((n * (long) next(31)) >> 31);
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

  @SuppressWarnings("unchecked")
  private void initCiphers(Config cfg) {
    List<NamedFactory<Cipher>> a = BaseBuilder.setUpDefaultCiphers(true);

    for (Iterator<NamedFactory<Cipher>> i = a.iterator(); i.hasNext(); ) {
      NamedFactory<Cipher> f = i.next();
      try {
        Cipher c = f.create();
        byte[] key = new byte[c.getKdfSize()];
        byte[] iv = new byte[c.getIVSize()];
        c.init(Cipher.Mode.Encrypt, key, iv);
      } catch (InvalidKeyException e) {
        logger.atWarning().log(
            "Disabling cipher %s: %s; try installing unlimited cryptography extension",
            f.getName(), e.getMessage());
        i.remove();
      } catch (Exception e) {
        logger.atWarning().log("Disabling cipher %s: %s", f.getName(), e.getMessage());
        i.remove();
      }
    }

    a.add(null);
    setCipherFactories(
        filter(cfg, "cipher", (NamedFactory<Cipher>[]) a.toArray(new NamedFactory<?>[a.size()])));
  }

  @SuppressWarnings("unchecked")
  private void initMacs(Config cfg) {
    List<NamedFactory<Mac>> m = BaseBuilder.setUpDefaultMacs(true);
    setMacFactories(
        filter(cfg, "mac", (NamedFactory<Mac>[]) m.toArray(new NamedFactory<?>[m.size()])));
  }

  @SafeVarargs
  private static <T extends NamedResource> List<T> filter(Config cfg, String key, T... avail) {
    List<T> def = new ArrayList<>();
    for (T n : avail) {
      if (n == null) {
        break;
      }
      def.add(n);
    }

    String[] want = cfg.getStringList("sshd", null, key);
    if (want == null || want.length == 0) {
      return def;
    }

    boolean didClear = false;
    for (String setting : want) {
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

      T n = find(name, avail);
      if (n == null) {
        StringBuilder msg = new StringBuilder();
        msg.append("sshd.").append(key).append(" = ").append(name).append(" unsupported; only ");
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
        logger.atSevere().log(msg.toString());
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
  private static <T extends NamedResource> T find(String name, T... avail) {
    for (T n : avail) {
      if (n != null && name.equals(n.getName())) {
        return n;
      }
    }
    return null;
  }

  private void initSignatures() {
    setSignatureFactories(ServerBuilder.setUpDefaultSignatureFactories(false));
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
    // Compression can be especially useful when Gerrit replica are being used
    // for the larger clones and fetches and the primary server handling write
    // operations mostly takes small receive-packs.

    if (enableCompression) {
      compressionFactories.add(BuiltinCompressions.zlib);
    }

    setCompressionFactories(compressionFactories);
  }

  private void initChannels() {
    setChannelFactories(ServerBuilder.DEFAULT_CHANNEL_FACTORIES);
  }

  private void initUnknownChannelReferenceHandler(boolean enableChannelIdTracking) {
    setUnknownChannelReferenceHandler(
        enableChannelIdTracking
            ? ChannelIdTrackingUnknownChannelReferenceHandler.TRACKER
            : DefaultUnknownChannelReferenceHandler.INSTANCE);
  }

  private void initSubsystems() {
    setSubsystemFactories(Collections.emptyList());
  }

  private void initUserAuth(
      final PublickeyAuthenticator pubkey,
      final GSSAuthenticator kerberosAuthenticator,
      String kerberosKeytab,
      String kerberosPrincipal) {
    List<UserAuthFactory> authFactories = new ArrayList<>();
    if (kerberosKeytab != null) {
      authFactories.add(UserAuthGSSFactory.INSTANCE);
      logger.atInfo().log("Enabling kerberos with keytab %s", kerberosKeytab);
      if (!new File(kerberosKeytab).canRead()) {
        logger.atSevere().log(
            "Keytab %s does not exist or is not readable; further errors are possible",
            kerberosKeytab);
      }
      kerberosAuthenticator.setKeytabFile(kerberosKeytab);
      if (kerberosPrincipal == null) {
        try {
          kerberosPrincipal = "host/" + InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
          kerberosPrincipal = "host/localhost";
        }
      }
      logger.atInfo().log("Using kerberos principal %s", kerberosPrincipal);
      if (!kerberosPrincipal.startsWith("host/")) {
        logger.atWarning().log(
            "Host principal does not start with host/ "
                + "which most SSH clients will supply automatically");
      }
      kerberosAuthenticator.setServicePrincipalName(kerberosPrincipal);
      setGSSAuthenticator(kerberosAuthenticator);
    }
    authFactories.add(UserAuthPublicKeyFactory.INSTANCE);
    setUserAuthFactories(authFactories);
    setPublickeyAuthenticator(pubkey);
  }

  private void initForwarding() {
    setForwardingFilter(
        new ForwardingFilter() {
          @Override
          public boolean canForwardAgent(Session session, String requestType) {
            return false;
          }

          @Override
          public boolean canForwardX11(Session session, String requestType) {
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
    setForwarderFactory(new DefaultForwarderFactory());
  }

  private void initFileSystemFactory() {
    setFileSystemFactory(NoneFileSystemFactory.INSTANCE);
  }
}
