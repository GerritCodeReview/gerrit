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

import com.google.gerrit.client.data.AccountCache;
import com.google.gerrit.client.data.GroupCache;
import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.client.reviewdb.SystemConfig.LoginType;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.DiffCacheEntryFactory;
import com.google.gerrit.server.ssh.SshKeyCacheEntryFactory;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.Database;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.config.CacheConfiguration;
import net.sf.ehcache.config.Configuration;
import net.sf.ehcache.config.DiskStoreConfiguration;
import net.sf.ehcache.constructs.blocking.SelfPopulatingCache;
import net.sf.ehcache.store.MemoryStoreEvictionPolicy;

import org.apache.commons.net.smtp.AuthSMTPClient;
import org.apache.commons.net.smtp.SMTPClient;
import org.apache.commons.net.smtp.SMTPReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.errors.ConfigInvalidException;
import org.spearce.jgit.errors.RepositoryNotFoundException;
import org.spearce.jgit.lib.Config;
import org.spearce.jgit.lib.FileBasedConfig;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.RepositoryCache;
import org.spearce.jgit.lib.UserConfig;
import org.spearce.jgit.lib.WindowCache;
import org.spearce.jgit.lib.WindowCacheConfig;
import org.spearce.jgit.lib.RepositoryCache.FileKey;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

import javax.servlet.http.HttpServletRequest;

/** Global server-side state for Gerrit. */
@Singleton
public class GerritServer {
  private static final Logger log = LoggerFactory.getLogger(GerritServer.class);
  private static CacheManager cacheMgr;

  static void closeCacheManager() {
    if (cacheMgr != null) {
      try {
        cacheMgr.shutdown();
      } catch (Throwable bad) {
      } finally {
        cacheMgr = null;
      }
    }
  }

  public static String serverUrl(final HttpServletRequest req) {
    // Assume this servlet is in the context with a simple name like "login"
    // and we were accessed without any path info. Clipping the last part of
    // the name from the URL should generate the web application's root path.
    //
    String uri = req.getRequestURL().toString();
    final int s = uri.lastIndexOf('/');
    if (s >= 0) {
      uri = uri.substring(0, s + 1);
    }
    final String sfx = "/gerrit/rpc/";
    if (uri.endsWith(sfx)) {
      // Nope, it was one of our RPC servlets. Drop the rpc too.
      //
      uri = uri.substring(0, uri.length() - (sfx.length() - 1));
    }
    return uri;
  }

  private final Database<ReviewDb> db;
  private final File sitePath;
  private final FileBasedConfig gerritConfigFile;
  private final int sessionAge;
  private final SignedToken xsrf;
  private final SignedToken account;
  private final SignedToken emailReg;
  private final File basepath;
  private final SelfPopulatingCache diffCache;
  private final SelfPopulatingCache sshKeysCache;

  @Inject
  GerritServer(final Database<ReviewDb> database, final SystemConfig sConfig,
      @SitePath final File path) throws OrmException, XsrfException {
    db = database;
    sitePath = path;

    final File cfgLoc = new File(sitePath, "gerrit.config");
    gerritConfigFile = new FileBasedConfig(cfgLoc);
    try {
      gerritConfigFile.load();
    } catch (FileNotFoundException e) {
      log.info("No " + cfgLoc.getAbsolutePath() + "; assuming defaults");
    } catch (ConfigInvalidException e) {
      throw new OrmException("Cannot read " + cfgLoc.getAbsolutePath(), e);
    } catch (IOException e) {
      throw new OrmException("Cannot read " + cfgLoc.getAbsolutePath(), e);
    }
    reconfigureWindowCache();
    sessionAge = gerritConfigFile.getInt("auth", "maxsessionage", 12 * 60) * 60;

    xsrf = new SignedToken(getSessionAge(), sConfig.xsrfPrivateKey);

    final int accountCookieAge;
    switch (getLoginType()) {
      case HTTP:
        accountCookieAge = -1; // expire when the browser closes
        break;
      case OPENID:
      default:
        accountCookieAge = getSessionAge();
        break;
    }
    account = new SignedToken(accountCookieAge, sConfig.accountPrivateKey);
    emailReg = new SignedToken(5 * 24 * 60 * 60, sConfig.accountPrivateKey);

    final String basePath =
        getGerritConfig().getString("gerrit", null, "basepath");
    if (basePath != null) {
      File root = new File(basePath);
      if (!root.isAbsolute()) {
        root = new File(sitePath, basePath);
      }
      basepath = root;
    } else {
      basepath = null;
    }

    Common.setSchemaFactory(db);
    Common.setProjectCache(new ProjectCache());
    Common.setAccountCache(new AccountCache());
    Common.setGroupCache(new GroupCache(sConfig));

    cacheMgr = new CacheManager(createCacheConfiguration());
    diffCache = startCacheDiff();
    sshKeysCache = startCacheSshKeys();
  }

  private Configuration createCacheConfiguration() {
    final Configuration mgrCfg = new Configuration();
    configureDiskStore(mgrCfg);
    configureDefaultCache(mgrCfg);

    if (getLoginType() == LoginType.OPENID) {
      final CacheConfiguration c;
      c = configureNamedCache(mgrCfg, "openid", false, 5);
      c.setTimeToLiveSeconds(c.getTimeToIdleSeconds());
      mgrCfg.addCache(c);
    }

    mgrCfg.addCache(configureNamedCache(mgrCfg, "diff", true, 0));
    mgrCfg.addCache(configureNamedCache(mgrCfg, "sshkeys", false, 0));
    return mgrCfg;
  }

  private void configureDiskStore(final Configuration mgrCfg) {
    String path = gerritConfigFile.getString("cache", null, "directory");
    if (path == null || path.length() == 0) {
      path = "disk_cache";
    }

    final File loc = new File(sitePath, path);
    if (loc.exists() || loc.mkdirs()) {
      if (loc.canWrite()) {
        final DiskStoreConfiguration c = new DiskStoreConfiguration();
        c.setPath(loc.getAbsolutePath());
        mgrCfg.addDiskStore(c);
        log.info("Enabling disk cache " + loc.getAbsolutePath());
      } else {
        log.warn("Can't write to disk cache: " + loc.getAbsolutePath());
      }
    } else {
      log.warn("Can't create disk cache: " + loc.getAbsolutePath());
    }
  }

  private void configureDefaultCache(final Configuration mgrCfg) {
    final Config i = gerritConfigFile;
    final CacheConfiguration c = new CacheConfiguration();

    c.setMaxElementsInMemory(i.getInt("cache", "memorylimit", 1024));
    c.setMemoryStoreEvictionPolicyFromObject(MemoryStoreEvictionPolicy.LFU);

    c.setTimeToLiveSeconds(0);
    final int oneday = 24 * 60;
    c.setTimeToIdleSeconds(i.getInt("cache", "maxage", 3 * 30 * oneday) * 60);
    c.setEternal(c.getTimeToIdleSeconds() == 0);

    if (mgrCfg.getDiskStoreConfiguration() != null) {
      c.setMaxElementsOnDisk(i.getInt("cache", "disklimit", 16384));
      c.setOverflowToDisk(false);
      c.setDiskPersistent(false);

      int diskbuffer = i.getInt("cache", "diskbuffer", 5 * 1024 * 1024);
      diskbuffer /= 1024 * 1024;
      c.setDiskSpoolBufferSizeMB(Math.max(1, diskbuffer));
      c.setDiskExpiryThreadIntervalSeconds(60 * 60);
    }

    mgrCfg.setDefaultCacheConfiguration(c);
  }

  private CacheConfiguration configureNamedCache(final Configuration mgrCfg,
      final String name, final boolean disk, final int defaultAge) {
    final Config i = gerritConfigFile;
    final CacheConfiguration def = mgrCfg.getDefaultCacheConfiguration();
    final CacheConfiguration cfg;
    try {
      cfg = def.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException("Cannot configure cache " + name, e);
    }
    cfg.setName(name);

    cfg.setMaxElementsInMemory(i.getInt("cache", name, "memorylimit", def
        .getMaxElementsInMemory()));

    cfg.setTimeToIdleSeconds(i.getInt("cache", name, "maxage", defaultAge > 0
        ? defaultAge : (int) (def.getTimeToIdleSeconds() / 60)) * 60);
    cfg.setEternal(cfg.getTimeToIdleSeconds() == 0);

    if (disk && mgrCfg.getDiskStoreConfiguration() != null) {
      cfg.setMaxElementsOnDisk(i.getInt("cache", name, "disklimit", def
          .getMaxElementsOnDisk()));

      final int m = 1024 * 1024;
      final int diskbuffer =
          i.getInt("cache", name, "diskbuffer", def.getDiskSpoolBufferSizeMB()
              * m)
              / m;
      cfg.setDiskSpoolBufferSizeMB(Math.max(1, diskbuffer));
      cfg.setOverflowToDisk(true);
      cfg.setDiskPersistent(true);
    }

    return cfg;
  }

  private SelfPopulatingCache startCacheDiff() {
    final Cache dc = cacheMgr.getCache("diff");
    final SelfPopulatingCache r;

    r = new SelfPopulatingCache(dc, new DiffCacheEntryFactory(this));
    cacheMgr.replaceCacheWithDecoratedCache(dc, r);
    return r;
  }

  private SelfPopulatingCache startCacheSshKeys() {
    final Cache dc = cacheMgr.getCache("sshkeys");
    final SelfPopulatingCache r;

    r = new SelfPopulatingCache(dc, new SshKeyCacheEntryFactory(db));
    cacheMgr.replaceCacheWithDecoratedCache(dc, r);
    return r;
  }

  public boolean isOutgoingMailEnabled() {
    return getGerritConfig().getBoolean("sendemail", null, "enable", true);
  }

  public SMTPClient createOutgoingMail() throws EmailException {
    if (!isOutgoingMailEnabled()) {
      throw new EmailException("Sending email is disabled");
    }

    final Config cfg = getGerritConfig();
    String smtpHost = cfg.getString("sendemail", null, "smtpserver");
    if (smtpHost == null) {
      smtpHost = "127.0.0.1";
    }
    int smtpPort = cfg.getInt("sendemail", null, "smtpserverport", 25);

    String smtpUser = cfg.getString("sendemail", null, "smtpuser");
    String smtpPass = cfg.getString("sendemail", null, "smtpuserpass");

    final AuthSMTPClient client = new AuthSMTPClient("UTF-8");
    client.setAllowRcpt(cfg.getStringList("sendemail", null, "allowrcpt"));
    try {
      client.connect(smtpHost, smtpPort);
      if (!SMTPReply.isPositiveCompletion(client.getReplyCode())) {
        throw new EmailException("SMTP server rejected connection");
      }
      if (!client.login()) {
        String e = client.getReplyString();
        throw new EmailException("SMTP server rejected login: " + e);
      }
      if (smtpUser != null && !client.auth(smtpUser, smtpPass)) {
        String e = client.getReplyString();
        throw new EmailException("SMTP server rejected auth: " + e);
      }
    } catch (IOException e) {
      if (client.isConnected()) {
        try {
          client.disconnect();
        } catch (IOException e2) {
        }
      }
      throw new EmailException(e.getMessage(), e);
    } catch (EmailException e) {
      if (client.isConnected()) {
        try {
          client.disconnect();
        } catch (IOException e2) {
        }
      }
      throw e;
    }
    return client;
  }

  private void reconfigureWindowCache() {
    final WindowCacheConfig c = new WindowCacheConfig();
    c.fromConfig(gerritConfigFile);
    WindowCache.reconfigure(c);
  }

  /** Time (in seconds) that user sessions stay "signed in". */
  public int getSessionAge() {
    return sessionAge;
  }

  /** Get the signature support used to protect against XSRF attacks. */
  public SignedToken getXsrfToken() {
    return xsrf;
  }

  /** Get the signature support used to protect user identity cookies. */
  public SignedToken getAccountToken() {
    return account;
  }

  /** Get the signature used for email registration/validation links. */
  public SignedToken getEmailRegistrationToken() {
    return emailReg;
  }

  public SystemConfig.LoginType getLoginType() {
    String type = getGerritConfig().getString("auth", null, "type");
    if (type == null) {
      return SystemConfig.LoginType.OPENID;
    }
    for (SystemConfig.LoginType t : SystemConfig.LoginType.values()) {
      if (type.equalsIgnoreCase(t.name())) {
        return t;
      }
    }
    throw new IllegalStateException("Unsupported auth.type: " + type);
  }

  public String getLoginHttpHeader() {
    return getGerritConfig().getString("auth", null, "httpheader");
  }

  public String getEmailFormat() {
    return getGerritConfig().getString("auth", null, "emailformat");
  }

  public String getContactStoreURL() {
    return getGerritConfig().getString("contactstore", null, "url");
  }

  public String getContactStoreAPPSEC() {
    return getGerritConfig().getString("contactstore", null, "appsec");
  }

  /** Optional canonical URL for this application. */
  public String getCanonicalURL() {
    String u = getGerritConfig().getString("gerrit", null, "canonicalweburl");
    if (u != null && !u.endsWith("/")) {
      u += "/";
    }
    return u;
  }

  /** Get the parsed <code>$site_path/gerrit.config</code> file. */
  public Config getGerritConfig() {
    return gerritConfigFile;
  }

  /**
   * Get (or open) a repository by name.
   * 
   * @param name the repository name, relative to the base directory.
   * @return the cached Repository instance. Caller must call {@code close()}
   *         when done to decrement the resource handle.
   * @throws RepositoryNotFoundException the name does not denote an existing
   *         repository, or the name cannot be read as a repository.
   */
  public Repository openRepository(String name)
      throws RepositoryNotFoundException {
    if (basepath == null) {
      throw new RepositoryNotFoundException("No gerrit.basepath configured");
    }

    if (isUnreasonableName(name)) {
      throw new RepositoryNotFoundException("Invalid name: " + name);
    }

    try {
      final FileKey loc = FileKey.lenient(new File(basepath, name));
      return RepositoryCache.open(loc);
    } catch (IOException e1) {
      final RepositoryNotFoundException e2;
      e2 = new RepositoryNotFoundException("Cannot open repository " + name);
      e2.initCause(e1);
      throw e2;
    }
  }

  private boolean isUnreasonableName(final String name) {
    if (name.length() == 0) return true; // no empty paths

    if (name.indexOf('\\') >= 0) return true; // no windows/dos stlye paths
    if (name.charAt(0) == '/') return true; // no absolute paths
    if (new File(name).isAbsolute()) return true; // no absolute paths

    if (name.startsWith("../")) return true; // no "l../etc/passwd"
    if (name.contains("/../")) return true; // no "foo/../etc/passwd"
    if (name.contains("/./")) return true; // "foo/./foo" is insane to ask
    if (name.contains("//")) return true; // windows UNC path can be "//..."

    return false; // is a reasonable name
  }

  /** Get all registered caches. */
  public Ehcache[] getAllCaches() {
    final String[] cacheNames = cacheMgr.getCacheNames();
    Arrays.sort(cacheNames);
    final Ehcache[] r = new Ehcache[cacheNames.length];
    for (int i = 0; i < cacheNames.length; i++) {
      r[i] = cacheMgr.getEhcache(cacheNames[i]);
    }
    return r;
  }

  /** Get any existing cache by name. */
  public Cache getCache(final String name) {
    return cacheMgr.getCache(name);
  }

  /** Get the self-populating cache of DiffCacheContent entities. */
  public SelfPopulatingCache getDiffCache() {
    return diffCache;
  }

  /** Get the self-populating cache of user SSH keys. */
  public SelfPopulatingCache getSshKeysCache() {
    return sshKeysCache;
  }

  /** Get a new identity representing this Gerrit server in Git. */
  public PersonIdent newGerritPersonIdent() {
    String name = getGerritConfig().getString("user", null, "name");
    if (name == null) {
      name = "Gerrit Code Review";
    }
    String email = getGerritConfig().get(UserConfig.KEY).getCommitterEmail();
    if (email == null || email.length() == 0) {
      email = "gerrit@localhost";
    }
    return new PersonIdent(name, email);
  }

  public boolean isAllowGoogleAccountUpgrade() {
    return getGerritConfig().getBoolean("auth", "allowgoogleaccountupgrade",
        false);
  }
}
