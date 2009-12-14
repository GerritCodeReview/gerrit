// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.pgm;

import static com.google.gerrit.pgm.util.DataSourceProvider.Context.SINGLE_USER;
import static com.google.gerrit.pgm.util.DataSourceProvider.Type.H2;

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.main.GerritLauncher;
import com.google.gerrit.pgm.util.ConsoleUI;
import com.google.gerrit.pgm.util.DataSourceProvider;
import com.google.gerrit.pgm.util.ErrorLogFile;
import com.google.gerrit.pgm.util.IoUtil;
import com.google.gerrit.pgm.util.LibraryDownloader;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.AuthType;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.SubmitType;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.SmtpEmailSender.Encryption;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;

import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileBasedConfig;
import org.eclipse.jgit.lib.LockFile;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.eclipse.jgit.util.SystemReader;
import org.h2.util.StartBrowser;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Initialize a new Gerrit installation. */
public class Init extends SiteProgram {
  @Option(name = "--batch", usage = "Batch mode; skip interactive prompting")
  private boolean batchMode;

  @Option(name = "--import-projects", usage = "Import git repositories as projects")
  private boolean importProjects;

  @Option(name = "--no-auto-start", usage = "Don't automatically start daemon after init")
  private boolean noAutoStart;

  @Inject
  private GitRepositoryManager repositoryManager;

  @Inject
  private SchemaFactory<ReviewDb> schema;

  private boolean deleteOnFailure;
  private ConsoleUI ui;
  private Injector dbInjector;
  private Injector sysInjector;

  private File site_path;
  private File bin_dir;
  private File etc_dir;
  private File lib_dir;
  private File logs_dir;
  private File static_dir;

  private File gerrit_sh;
  private File gerrit_config;
  private File secure_config;
  private File replication_config;

  private FileBasedConfig cfg;
  private FileBasedConfig sec;

  @Override
  public int run() throws Exception {
    ErrorLogFile.errorOnlyConsole();
    ui = ConsoleUI.getInstance(batchMode);
    initPathLocations();

    final boolean isNew = !site_path.exists();
    try {
      upgradeFrom2_0();

      initSitePath();
      generateSshHostKeys();
      deleteOnFailure = false;

      inject();
      initGit();
    } catch (Exception failure) {
      if (deleteOnFailure) {
        recursiveDelete(getSitePath());
      }
      throw failure;
    } catch (Error failure) {
      if (deleteOnFailure) {
        recursiveDelete(getSitePath());
      }
      throw failure;
    }

    System.err.println("Initialized " + getSitePath().getCanonicalPath());

    if (isNew && !noAutoStart) {
      if (IoUtil.isWin32()) {
        System.err.println("Automatic startup not supported on this platform.");

      } else {
        start();
        openBrowser();
      }
    }

    return 0;
  }

  private void initPathLocations() {
    site_path = getSitePath();

    bin_dir = new File(site_path, "bin");
    etc_dir = new File(site_path, "etc");
    lib_dir = new File(site_path, "lib");
    logs_dir = new File(site_path, "logs");
    static_dir = new File(site_path, "static");

    gerrit_sh = new File(bin_dir, "gerrit.sh");
    gerrit_config = new File(etc_dir, "gerrit.config");
    secure_config = new File(etc_dir, "secure.config");
    replication_config = new File(etc_dir, "replication.config");

    cfg = new FileBasedConfig(gerrit_config);
    sec = new FileBasedConfig(secure_config);
  }

  private void upgradeFrom2_0() throws IOException {
    boolean isPre2_1 = false;
    final String[] etcFiles =
        {"gerrit.config", "secure.config", "replication.config",
            "ssh_host_rsa_key", "ssh_host_rsa_key.pub", "ssh_host_dsa_key",
            "ssh_host_dsa_key.pub", "ssh_host_key", "contact_information.pub",
            "gitweb_config.perl", "keystore", "GerritSite.css",
            "GerritSiteFooter.html", "GerritSiteHeader.html"};
    for (String name : etcFiles) {
      if (new File(site_path, name).exists()) {
        isPre2_1 = true;
        break;
      }
    }

    if (isPre2_1) {
      if (!ui.yesno(true, "Upgrade '%s'", site_path.getCanonicalPath())) {
        throw die("aborted by user");
      }

      if (!etc_dir.exists() && !etc_dir.mkdirs()) {
        throw die("Cannot create directory " + etc_dir);
      }
      for (String name : etcFiles) {
        final File src = new File(site_path, name);
        final File dst = new File(etc_dir, name);
        if (src.exists()) {
          if (dst.exists()) {
            throw die("File " + src + " would overwrite " + dst);
          }
          if (!src.renameTo(dst)) {
            throw die("Cannot rename " + src + " to " + dst);
          }
        }
      }
    }
  }

  private void initSitePath() throws IOException, InterruptedException,
      ConfigInvalidException {
    ui.header("Gerrit Code Review %s", version());

    if (!gerrit_config.exists()) {
      if (!ui.yesno(true, "Create '%s'", site_path.getCanonicalPath())) {
        throw die("aborted by user");
      }
      if (!site_path.mkdirs()) {
        throw die("Cannot make directory " + site_path);
      }
      if (!etc_dir.mkdir()) {
        throw die("Cannot make directory " + etc_dir);
      }
      deleteOnFailure = true;
    }

    bin_dir.mkdir();
    etc_dir.mkdir();
    lib_dir.mkdir();
    logs_dir.mkdir();
    static_dir.mkdir();

    cfg.load();
    sec.load();

    downloadOptionalLibraries();
    init_gerrit_basepath();
    init_database();
    init_auth();
    init_sendemail();
    init_container();
    init_sshd();
    init_httpd();
    init_cache();

    cfg.save();
    saveSecureConfig(sec);

    if (ui != null) {
      System.err.println();
    }

    if (!secure_config.exists()) {
      chmod(0600, secure_config);
    }
    if (!replication_config.exists()) {
      replication_config.createNewFile();
    }
    if (!gerrit_sh.exists()) {
      extract(gerrit_sh, "WEB-INF/extra/bin/gerrit.sh");
      chmod(0755, gerrit_sh);
    }
  }

  private void initGit() throws OrmException, IOException {
    final File root = repositoryManager.getBasePath();
    if (root != null && importProjects) {
      System.err.println("Scanning projects under " + root);
      final ReviewDb db = schema.open();
      try {
        final HashSet<String> have = new HashSet<String>();
        for (Project p : db.projects().all()) {
          have.add(p.getName());
        }
        importProjects(root, "", db, have);
      } finally {
        db.close();
      }
    }
  }

  private void importProjects(final File dir, final String prefix,
      final ReviewDb db, final Set<String> have) throws OrmException,
      IOException {
    final File[] ls = dir.listFiles();
    if (ls == null) {
      return;
    }

    for (File f : ls) {
      if (".".equals(f.getName()) || "..".equals(f.getName())) {
      } else if (FileKey.isGitRepository(f)) {
        String name = f.getName();
        if (name.equals(".git")) {
          name = prefix.substring(0, prefix.length() - 1);
        } else if (name.endsWith(".git")) {
          name = prefix + name.substring(0, name.length() - 4);
        } else {
          name = prefix + name;
          System.err.println("Ignoring non-standard name '" + name + "'");
          continue;
        }

        if (have.contains(name)) {
          continue;
        }

        final Project.NameKey nameKey = new Project.NameKey(name);
        final Project.Id idKey = new Project.Id(db.nextProjectId());
        final Project p = new Project(nameKey, idKey);

        p.setDescription(repositoryManager.getProjectDescription(name));
        p.setSubmitType(SubmitType.MERGE_IF_NECESSARY);
        p.setUseContributorAgreements(false);
        p.setUseSignedOffBy(false);
        db.projects().insert(Collections.singleton(p));

      } else if (f.isDirectory()) {
        importProjects(f, prefix + f.getName() + "/", db, have);
      }
    }
  }

  private void saveSecureConfig(final FileBasedConfig sec) throws IOException {
    final byte[] out = Constants.encode(sec.toText());
    final File path = sec.getFile();
    final LockFile lf = new LockFile(path);
    if (!lf.lock()) {
      throw new IOException("Cannot lock " + path);
    }
    try {
      chmod(0600, new File(path.getParentFile(), path.getName() + ".lock"));
      lf.write(out);
      if (!lf.commit()) {
        throw new IOException("Cannot commit write to " + path);
      }
    } finally {
      lf.unlock();
    }
  }

  private static void chmod(final int mode, final File path) throws IOException {
    if (!path.exists() && !path.createNewFile()) {
      throw new IOException("Cannot create " + path);
    }
    path.setReadable(false, false /* all */);
    path.setWritable(false, false /* all */);
    path.setExecutable(false, false /* all */);

    path.setReadable((mode & 0400) == 0400, true /* owner only */);
    path.setWritable((mode & 0200) == 0200, true /* owner only */);
    if (path.isDirectory() || (mode & 0100) == 0100) {
      path.setExecutable(true, true /* owner only */);
    }

    if ((mode & 0044) == 0044) {
      path.setReadable(true, false /* all */);
    }
    if ((mode & 0011) == 0011) {
      path.setExecutable(true, false /* all */);
    }
  }

  private void downloadOptionalLibraries() {
    // Download and install BouncyCastle if the user wants to use it.
    //
    createDownloader().setRequired(false).setName("Bouncy Castle Crypto v144")
        .setJarUrl("http://www.bouncycastle.org/download/bcprov-jdk16-144.jar")
        .setSHA1("6327a5f7a3dc45e0fd735adb5d08c5a74c05c20c").download();
    loadSiteLib();
  }

  private void init_gerrit_basepath() {
    final Section cfg = new Section("gerrit");
    ui.header("Git Repositories");

    File d = cfg.path("Location of Git repositories", "basePath", "git");
    if (d == null) {
      throw die("gerrit.basePath is required");
    }
    if (d.exists()) {
      if (!importProjects && d.list() != null && d.list().length > 0) {
        importProjects = ui.yesno(true, "Import existing repositories");
      }
    } else if (!d.mkdirs()) {
      throw die("Cannot create " + d);
    }
  }

  private void init_database() {
    final Section cfg = new Section("database");
    ui.header("SQL Database");

    final DataSourceProvider.Type db_type =
        cfg.select("Database server type", "type", H2);

    switch (db_type) {
      case MYSQL:
        createDownloader()
            .setRequired(true)
            .setName("MySQL Connector/J 5.1.10")
            .setJarUrl(
                "http://repo2.maven.org/maven2/mysql/mysql-connector-java/5.1.10/mysql-connector-java-5.1.10.jar")
            .setSHA1("b83574124f1a00d6f70d56ba64aa52b8e1588e6d").download();
        loadSiteLib();
        break;
    }

    final boolean userPassAuth;
    switch (db_type) {
      case H2: {
        userPassAuth = false;
        String path = cfg.get("database");
        if (path == null) {
          path = "db/ReviewDB";
          cfg.set("database", path);
        }
        File db = resolve(path);
        if (db == null) {
          throw die("database.database must be supplied for H2");
        }
        db = db.getParentFile();
        if (!db.exists() && !db.mkdirs()) {
          throw die("cannot create database.database " + db.getAbsolutePath());
        }
        break;
      }

      case JDBC: {
        userPassAuth = true;
        cfg.string("Driver class name", "driver", null);
        cfg.string("URL", "url", null);
        break;
      }

      case POSTGRES:
      case POSTGRESQL:
      case MYSQL: {
        userPassAuth = true;
        final String defPort = "(" + db_type.toString() + " default)";
        cfg.string("Server hostname", "hostname", "localhost");
        cfg.string("Server port", "port", defPort, true);
        cfg.string("Database name", "database", "reviewdb");
        break;
      }

      default:
        throw die("internal bug, database " + db_type + " not supported");
    }

    if (userPassAuth) {
      cfg.string("Database username", "username", username());
      cfg.password("username", "password");
    }
  }

  private void init_auth() {
    final Section cfg = new Section("auth");
    ui.header("User Authentication");

    final AuthType auth_type =
        cfg.select("Authentication method", "type", AuthType.OPENID);

    switch (auth_type) {
      case HTTP:
      case HTTP_LDAP: {
        String hdr = cfg.get("httpHeader");
        if (ui.yesno(hdr != null, "Get username from custom HTTP header")) {
          cfg.string("Username HTTP header", "httpHeader", "SM_USER");
        } else if (hdr != null) {
          cfg.unset("httpHeader");
        }
        cfg.string("SSO logout URL", "logoutUrl", null);
        break;
      }
    }

    switch (auth_type) {
      case LDAP:
      case HTTP_LDAP: {
        String server = cfg.string("LDAP server", "server", "ldap://localhost");
        if (server != null //
            && !server.startsWith("ldap://") //
            && !server.startsWith("ldaps://")) {
          if (ui.yesno(false, "Use SSL")) {
            server = "ldaps://" + server;
          } else {
            server = "ldap://" + server;
          }
          cfg.set("server", server);
        }

        cfg.string("LDAP username", "username", null);
        cfg.password("username", "password");

        final String def_dn = dnOf(server);
        String aBase = cfg.string("Account BaseDN", "accountBase", def_dn);
        String gBase = cfg.string("Group BaseDN", "groupBase", aBase);
        break;
      }
    }
  }

  private void init_sendemail() {
    final Section cfg = new Section("sendemail");
    ui.header("Email Delivery");

    final String hostname =
        cfg.string("SMTP server hostname", "smtpServer", "localhost");
    cfg.string("SMTP server port", "smtpServerPort", "(default)", true);
    final Encryption enc =
        cfg.select("SMTP encryption", "smtpEncryption", Encryption.NONE, true);

    String username = null;
    if ((enc != null && enc != Encryption.NONE) || !isLocal(hostname)) {
      username = username();
    }
    cfg.string("SMTP username", "smtpUser", username);
    cfg.password("smtpUser", "smtpPass");
  }

  private void init_container() throws IOException {
    final Section cfg = new Section("container");
    ui.header("Container Process");

    cfg.string("Run as", "user", username());
    cfg.string("Java runtime", "javaHome", System.getProperty("java.home"));

    final File siteWar = new File(bin_dir, "gerrit.war");
    File myWar;
    try {
      myWar = GerritLauncher.getDistributionArchive();
    } catch (FileNotFoundException e) {
      System.err.println("warn: Cannot find gerrit.war");
      myWar = null;
    }

    String path = cfg.get("war");
    if (path != null) {
      path = cfg.string("Gerrit runtime", "war", //
          myWar != null ? myWar.getAbsolutePath() : null);
      if (path == null || path.isEmpty()) {
        throw die("container.war is required");
      }

    } else if (myWar != null) {
      final boolean copy;
      if (siteWar.exists()) {
        copy = ui.yesno(true, "Upgrade %s", siteWar.getPath());
      } else {
        copy = ui.yesno(true, "Copy gerrit.war to %s", siteWar.getPath());
        if (copy) {
          cfg.unset("war");
        } else {
          cfg.set("war", myWar.getAbsolutePath());
        }
      }
      if (copy) {
        if (!ui.isBatch()) {
          System.err.format("Copying gerrit.war to %s", siteWar.getPath());
          System.err.println();
        }
        copy(siteWar, new FileInputStream(myWar));
      }
    }
  }

  private void init_sshd() {
    final Section cfg = new Section("sshd");
    ui.header("SSH Daemon");

    String hostname = "*", port = "29418";
    String listenAddress = cfg.get("listenAddress");
    if (listenAddress != null && !listenAddress.isEmpty()) {
      if (listenAddress.startsWith("[")) {
        final int hostEnd = listenAddress.indexOf(']');
        if (0 < hostEnd) {
          hostname = listenAddress.substring(1, hostEnd);
          if (hostEnd + 1 < listenAddress.length() //
              && listenAddress.charAt(hostEnd + 1) == ':') {
            port = listenAddress.substring(hostEnd + 2);
          }
        }

      } else {
        final int hostEnd = listenAddress.indexOf(':');
        if (0 < hostEnd) {
          hostname = listenAddress.substring(0, hostEnd);
          port = listenAddress.substring(hostEnd + 1);
        } else {
          hostname = listenAddress;
        }
      }
    }

    hostname = ui.readString(hostname, "Listen on address");
    port = ui.readString(port, "Listen on port");
    cfg.set("listenAddress", hostname + ":" + port);
  }

  private void init_httpd() throws IOException, InterruptedException {
    final Section httpd = new Section("httpd");
    ui.header("HTTP Daemon");

    boolean proxy = false, ssl = false;
    String address = "*", port = null, context = "/";
    String listenUrl = httpd.get("listenUrl");
    if (listenUrl != null && !listenUrl.isEmpty()) {
      try {
        final URI uri = toURI(listenUrl);
        proxy = uri.getScheme().startsWith("proxy-");
        ssl = uri.getScheme().endsWith("https");
        address = isAnyAddress(new URI(listenUrl)) ? "*" : uri.getHost();
        port = String.valueOf(uri.getPort());
        context = uri.getPath();
      } catch (URISyntaxException e) {
        System.err.println("warning: invalid httpd.listenUrl " + listenUrl);
      }
    }

    proxy = ui.yesno(proxy, "Behind reverse proxy");

    if (proxy) {
      ssl = ui.yesno(ssl, "Proxy uses SSL (https://)");
      context = ui.readString(context, "Subdirectory on proxy server");
    } else {
      ssl = ui.yesno(ssl, "Use SSL (https://)");
      context = "/";
    }

    address = ui.readString(address, "Listen on address");

    if (port == null) {
      if (proxy) {
        port = "8081";
      } else if (ssl) {
        port = "8443";
      } else {
        port = "8080";
      }
    }
    port = ui.readString(port, "Listen on port");

    final StringBuilder urlbuf = new StringBuilder();
    urlbuf.append(proxy ? "proxy-" : "");
    urlbuf.append(ssl ? "https" : "http");
    urlbuf.append("://");
    urlbuf.append(address);
    urlbuf.append(":");
    urlbuf.append(port);
    urlbuf.append(context);
    httpd.set("listenUrl", urlbuf.toString());

    URI uri;
    try {
      uri = toURI(httpd.get("listenUrl"));
      if (uri.getScheme().startsWith("proxy-")) {
        // If its a proxy URL, assume the reverse proxy is on our system
        // at the protocol standard ports (so omit the ports from the URL).
        //
        String s = uri.getScheme().substring("proxy-".length());
        uri = new URI(s + "://" + uri.getHost() + uri.getPath());
      }
    } catch (URISyntaxException e) {
      throw die("invalid httpd.listenUrl");
    }
    final Section gerrit = new Section("gerrit");
    if (gerrit.get("canonicalWebUrl") != null
        || (!proxy && ssl)
        || ConfigUtil.getEnum(cfg, "auth", null, "type", AuthType.OPENID) == AuthType.OPENID) {
      gerrit.string("Canonical URL", "canonicalWebUrl", uri.toString());
    }

    generateSslCertificate();
  }

  private void generateSslCertificate() throws IOException,
      InterruptedException {
    final Section httpd = new Section("httpd");
    final String listenUrl = httpd.get("listenUrl");

    if (!listenUrl.startsWith("https://")) {
      // We aren't responsible for SSL processing.
      //
      return;
    }

    String hostname;
    try {
      String url = cfg.getString("gerrit", null, "canonicalWebUrl");
      if (url == null || url.isEmpty()) {
        url = listenUrl;
      }
      hostname = toURI(url).getHost();
    } catch (URISyntaxException e) {
      System.err.println("Invalid httpd.listenUrl, not checking certificate");
      return;
    }

    final File store = new File(etc_dir, "keystore");
    if (!ui.yesno(!store.exists(), "Create new self-signed SSL certificate")) {
      return;
    }

    String ssl_pass = httpd.get("sslKeyPassword");
    if (ssl_pass == null || ssl_pass.isEmpty()) {
      ssl_pass = SignedToken.generateRandomKey();
      httpd.set("sslKeyPassword", ssl_pass);
    }

    hostname = ui.readString(hostname, "Certificate server name");
    final String validity =
        ui.readString("365", "Certificate expires in (days)");

    final String dname =
        "CN=" + hostname + ",OU=Gerrit Code Review,O=" + domainOf(hostname);

    final File tmpdir = new File(etc_dir, "tmp.sslcertgen");
    if (!tmpdir.mkdir()) {
      throw die("Cannot create directory " + tmpdir);
    }
    chmod(0600, tmpdir);

    final File tmpstore = new File(tmpdir, "keystore");
    Runtime.getRuntime().exec(new String[] {"keytool", //
        "-keystore", tmpstore.getAbsolutePath(), //
        "-storepass", ssl_pass, //
        "-genkeypair", //
        "-alias", hostname, //
        "-keyalg", "RSA", //
        "-validity", validity, //
        "-dname", dname, //
        "-keypass", ssl_pass, //
    }).waitFor();
    chmod(0600, tmpstore);

    if (!tmpstore.renameTo(store)) {
      throw die("Cannot rename " + tmpstore + " to " + store);
    }
    if (!tmpdir.delete()) {
      throw die("Cannot delete " + tmpdir);
    }
  }

  private void init_cache() {
    final Section cfg = new Section("cache");
    String path = cfg.get("directory");

    if (path != null && path.isEmpty()) {
      // Explicitly set to empty implies the administrator has
      // disabled the on disk cache and doesn't want it enabled.
      //
      return;
    }

    if (path == null) {
      path = "cache";
      cfg.set("directory", path);
    }

    final File loc = resolve(path);
    if (!loc.exists() && !loc.mkdirs()) {
      throw die("cannot create cache.directory " + loc.getAbsolutePath());
    }
  }

  private File resolve(final String path) {
    if (path != null && !path.isEmpty()) {
      File loc = new File(path);
      if (!loc.isAbsolute()) {
        loc = new File(site_path, path);
      }
      return loc;
    }
    return null;
  }

  private void generateSshHostKeys() throws InterruptedException, IOException {
    final File key = new File(etc_dir, "ssh_host_key");
    final File rsa = new File(etc_dir, "ssh_host_rsa_key");
    final File dsa = new File(etc_dir, "ssh_host_dsa_key");

    if (!key.exists() && !rsa.exists() && !dsa.exists()) {
      System.err.print("Generating SSH host key ...");
      System.err.flush();

      if (SecurityUtils.isBouncyCastleRegistered()) {
        // Generate the SSH daemon host key using ssh-keygen.
        //
        final String comment = "gerrit-code-review@" + hostname();

        System.err.print(" rsa...");
        System.err.flush();
        Runtime.getRuntime().exec(new String[] {"ssh-keygen", //
            "-q" /* quiet */, //
            "-t", "rsa", //
            "-P", "", //
            "-C", comment, //
            "-f", rsa.getAbsolutePath() //
            }).waitFor();

        System.err.print(" dsa...");
        System.err.flush();
        Runtime.getRuntime().exec(new String[] {"ssh-keygen", //
            "-q" /* quiet */, //
            "-t", "dsa", //
            "-P", "", //
            "-C", comment, //
            "-f", dsa.getAbsolutePath() //
            }).waitFor();

      } else {
        // Generate the SSH daemon host key ourselves. This is complex
        // because SimpleGeneratorHostKeyProvider doesn't mark the data
        // file as only readable by us, exposing the private key for a
        // short period of time. We try to reduce that risk by creating
        // the key within a temporary directory.
        //
        final File tmpdir = new File(etc_dir, "tmp.sshkeygen");
        if (!tmpdir.mkdir()) {
          throw die("Cannot create directory " + tmpdir);
        }
        chmod(0600, tmpdir);

        final File tmpkey = new File(tmpdir, key.getName());
        final SimpleGeneratorHostKeyProvider p;

        System.err.print(" rsa(simple)...");
        System.err.flush();
        p = new SimpleGeneratorHostKeyProvider();
        p.setPath(tmpkey.getAbsolutePath());
        p.setAlgorithm("RSA");
        p.loadKeys(); // forces the key to generate.
        chmod(0600, tmpkey);

        if (!tmpkey.renameTo(key)) {
          throw die("Cannot rename " + tmpkey + " to " + key);
        }
        if (!tmpdir.delete()) {
          throw die("Cannot delete " + tmpdir);
        }
      }
      System.err.println(" done");
    }
  }

  private void start() {
    final String[] argv = {gerrit_sh.getAbsolutePath(), "start"};
    final Process proc;
    try {
      System.err.println("Executing " + argv[0] + " " + argv[1]);
      proc = Runtime.getRuntime().exec(argv);
    } catch (IOException e) {
      System.err.println("error: cannot start Gerrit: " + e.getMessage());
      return;
    }

    try {
      proc.getOutputStream().close();
    } catch (IOException e) {
    }

    IoUtil.copyWithThread(proc.getInputStream(), System.err);
    IoUtil.copyWithThread(proc.getErrorStream(), System.err);

    for (;;) {
      try {
        final int rc = proc.waitFor();
        if (rc != 0) {
          System.err.println("error: cannot start Gerrit: exit status " + rc);
        }
        break;
      } catch (InterruptedException e) {
        // retry
      }
    }
  }

  private void openBrowser() throws IOException {
    if (ui.isBatch()) {
      return;
    }

    String url = cfg.getString("httpd", null, "listenUrl");
    if (url == null) {
      return;
    }

    if (url.startsWith("proxy-")) {
      url = url.substring("proxy-".length());
    }
    if (!url.endsWith("/")) {
      url += "/";
    }
    url += "#" + PageLinks.ADMIN_PROJECTS;

    final URI uri;
    try {
      uri = toURI(url);
    } catch (URISyntaxException e) {
      System.err.println("error: invalid httpd.listenUrl: " + url);
      return;
    }
    final String hostname = uri.getHost();
    final int port = portOf(uri);

    System.err.print("Waiting for server to start ... ");
    System.err.flush();
    for (;;) {
      final Socket s;
      try {
        s = new Socket(hostname, port);
      } catch (IOException e) {
        try {
          Thread.sleep(100);
        } catch (InterruptedException ie) {
        }
        continue;
      }
      s.close();
      break;
    }
    System.err.println("OK");

    System.err.println("Opening browser ...");
    StartBrowser.openURL(uri.toString());
  }

  private static URI toURI(String url) throws URISyntaxException {
    final URI u = new URI(url);
    if (isAnyAddress(u)) {
      // If the URL uses * it means all addresses on this system, use the
      // current hostname instead in the returned URI.
      //
      final int s = url.indexOf('*');
      url = url.substring(0, s) + hostname() + url.substring(s + 1);
    }
    return new URI(url);
  }

  private static boolean isAnyAddress(final URI u) {
    return u.getHost() == null
        && (u.getAuthority().equals("*") || u.getAuthority().startsWith("*:"));
  }

  private static int portOf(final URI uri) {
    int port = uri.getPort();
    if (port < 0) {
      port = "https".equals(uri.getScheme()) ? 443 : 80;
    }
    return port;
  }

  private void inject() {
    dbInjector = createDbInjector(SINGLE_USER);
    sysInjector = createSysInjector();
    sysInjector.injectMembers(this);
  }

  private Injector createSysInjector() {
    final List<Module> modules = new ArrayList<Module>();
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(GitRepositoryManager.class);
      }
    });
    return dbInjector.createChildInjector(modules);
  }

  private LibraryDownloader createDownloader() {
    return new LibraryDownloader(ui, getSitePath());
  }

  private static String version() {
    return com.google.gerrit.common.Version.getVersion();
  }

  private static String username() {
    return System.getProperty("user.name");
  }

  private static String hostname() {
    return SystemReader.getInstance().getHostname();
  }

  private static boolean isLocal(final String hostname) {
    try {
      return InetAddress.getByName(hostname).isLoopbackAddress();
    } catch (UnknownHostException e) {
      return false;
    }
  }

  private static String dnOf(String name) {
    if (name != null) {
      int p = name.indexOf("://");
      if (0 < p) {
        name = name.substring(p + 3);
      }

      p = name.indexOf(".");
      if (0 < p) {
        name = name.substring(p + 1);
        name = "DC=" + name.replaceAll("\\.", ",DC=");
      } else {
        name = null;
      }
    }
    return name;
  }

  private static String domainOf(String name) {
    if (name != null) {
      int p = name.indexOf("://");
      if (0 < p) {
        name = name.substring(p + 3);
      }
      p = name.indexOf(".");
      if (0 < p) {
        name = name.substring(p + 1);
      }
    }
    return name;
  }

  private static void recursiveDelete(File path) {
    File[] entries = path.listFiles();
    if (entries != null) {
      for (File e : entries) {
        recursiveDelete(e);
      }
    }
    if (!path.delete() && path.exists()) {
      System.err.println("warn: Cannot remove " + path);
    }
  }

  private static void extract(final File dst, final String path)
      throws IOException {
    final URL u = GerritLauncher.class.getClassLoader().getResource(path);
    if (u == null) {
      System.err.println("warn: Cannot read " + path);
      return;
    }
    copy(dst, u.openStream());
  }

  private static void copy(final File dst, final InputStream in)
      throws FileNotFoundException, IOException {
    try {
      dst.getParentFile().mkdirs();
      final FileOutputStream out = new FileOutputStream(dst);
      try {
        final byte[] buf = new byte[4096];
        int n;
        while (0 < (n = in.read(buf))) {
          out.write(buf, 0, n);
        }
      } finally {
        out.close();
      }
    } finally {
      in.close();
    }
  }

  private class Section {
    final String section;

    Section(final String section) {
      this.section = section;
    }

    String get(String name) {
      return cfg.getString(section, null, name);
    }

    void set(final String name, final String value) {
      final ArrayList<String> all = new ArrayList<String>();
      all.addAll(Arrays.asList(cfg.getStringList(section, null, name)));

      if (value != null) {
        if (all.size() == 0 || all.size() == 1) {
          cfg.setString(section, null, name, value);
        } else {
          all.set(0, value);
          cfg.setStringList(section, null, name, all);
        }

      } else if (all.size() == 0) {
      } else if (all.size() == 1) {
        cfg.unset(section, null, name);
      } else {
        all.remove(0);
        cfg.setStringList(section, null, name, all);
      }
    }

    void unset(String name) {
      set(name, null);
    }

    String string(final String title, final String name, final String dv) {
      return string(title, name, dv, false);
    }

    String string(final String title, final String name, final String dv,
        final boolean nullIfDefault) {
      final String ov = get(name);
      String nv = ui.readString(ov != null ? ov : dv, "%s", title);
      if (nullIfDefault && nv == dv) {
        nv = null;
      }
      if (!eq(ov, nv)) {
        set(name, nv);
      }
      return nv;
    }

    File path(final String title, final String name, final String defValue) {
      return resolve(string(title, name, defValue));
    }

    <T extends Enum<?>> T select(final String title, final String name,
        final T defValue) {
      return select(title, name, defValue, false);
    }

    <T extends Enum<?>> T select(final String title, final String name,
        final T defValue, final boolean nullIfDefault) {
      final boolean set = get(name) != null;
      T oldValue = ConfigUtil.getEnum(cfg, section, null, name, defValue);
      T newValue = ui.readEnum(oldValue, "%s", title);
      if (nullIfDefault && newValue == defValue) {
        newValue = null;
      }
      if (!set || oldValue != newValue) {
        if (newValue != null) {
          set(name, newValue.name());
        } else {
          unset(name);
        }
      }
      return newValue;
    }

    String password(final String username, final String password) {
      final String ov = sec.getString(section, null, password);

      String user = sec.getString(section, null, username);
      if (user == null) {
        user = get(username);
      }

      if (user == null) {
        sec.unset(section, null, password);
        return null;
      }

      if (ov != null) {
        // If the user already has a password stored, try to reuse it
        // rather than prompting for a whole new one.
        //
        if (ui.isBatch() || !ui.yesno(false, "Change %s's password", user)) {
          return ov;
        }
      }

      final String nv = ui.password("%s's password", user);
      if (!eq(ov, nv)) {
        if (nv != null) {
          sec.setString(section, null, password, nv);
        } else {
          sec.unset(section, null, password);
        }
      }
      return nv;
    }
  }

  private static boolean eq(final String a, final String b) {
    if (a == null && b == null) {
      return true;
    }
    return a != null ? a.equals(b) : false;
  }
}
