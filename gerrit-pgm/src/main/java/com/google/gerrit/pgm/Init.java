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

import static com.google.gerrit.pgm.DataSourceProvider.Type.H2;
import static org.eclipse.jgit.util.StringUtils.equalsIgnoreCase;

import com.google.gerrit.reviewdb.AuthType;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.SubmitType;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.mail.SmtpEmailSender.Encryption;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Module;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileBasedConfig;
import org.eclipse.jgit.lib.LockFile;
import org.eclipse.jgit.lib.RepositoryCache.FileKey;
import org.kohsuke.args4j.Option;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Initialize a new Gerrit installation. */
public class Init extends SiteProgram {
  @Option(name = "--import-projects", usage = "Import git repositories as projects")
  private boolean importProjects;

  @Inject
  private GitRepositoryManager repositoryManager;

  @Inject
  private SchemaFactory<ReviewDb> schema;

  private Console console;
  private Injector dbInjector;
  private Injector sysInjector;

  @Override
  public int run() throws Exception {
    initSitePath();
    inject();
    initGit();

    System.err.println("Initialized " + getSitePath().getCanonicalPath());
    return 0;
  }

  private void initSitePath() throws IOException {
    final File sitePath = getSitePath();

    final File gerrit_config = new File(sitePath, "gerrit.config");
    final File secure_config = new File(sitePath, "secure.config");
    final File replication_config = new File(sitePath, "replication.config");
    final File lib_dir = new File(sitePath, "lib");
    final File logs_dir = new File(sitePath, "logs");
    final File static_dir = new File(sitePath, "static");

    if (sitePath.exists()) {
      if (!gerrit_config.exists()) {
        throw die("'" + sitePath + "' is not a Gerrit server site");
      }
    } else if (!gerrit_config.exists()) {
      needyes("Initialize '%s'", sitePath.getCanonicalPath());

      final FileBasedConfig cfg = new FileBasedConfig(gerrit_config);
      final FileBasedConfig sec = new FileBasedConfig(secure_config);
      init_gerrit_basepath(cfg);
      init_database(cfg, sec);
      init_auth(cfg, sec);
      init_sendemail(cfg, sec);

      if (!sitePath.mkdirs()) {
        throw die("Cannot make directory " + sitePath);
      }

      cfg.save();
      secureSave(sec);

      if (console != null) {
        System.err.println();
      }
    }

    if (!secure_config.exists()) {
      secure(secure_config);
    }
    if (!replication_config.exists()) {
      replication_config.createNewFile();
    }

    lib_dir.mkdir();
    logs_dir.mkdir();
    static_dir.mkdir();
  }

  private void initGit() throws OrmException {
    final File root = repositoryManager.getBasePath();
    if (root == null) {
      throw die("No gerrit.basePath configured; edit by hand");
    }
    if (!root.exists() && !root.mkdirs()) {
      throw die("Cannot create " + root);
    }

    if (importProjects) {
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
      final ReviewDb db, final Set<String> have) throws OrmException {
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

        p.setDescription(""); // TODO Import description file from Git
        p.setSubmitType(SubmitType.MERGE_IF_NECESSARY);
        p.setUseContributorAgreements(false);
        p.setUseSignedOffBy(false);
        db.projects().insert(Collections.singleton(p));

      } else if (f.isDirectory()) {
        importProjects(f, prefix + f.getName() + "/", db, have);
      }
    }
  }

  private void secureSave(final FileBasedConfig sec) throws IOException {
    final byte[] out = Constants.encode(sec.toText());
    final File path = sec.getFile();
    final LockFile lf = new LockFile(path);
    if (!lf.lock()) {
      throw new IOException("Cannot lock " + path);
    }
    try {
      secure(new File(path.getParentFile(), path.getName() + ".lock"));
      lf.write(out);
      if (!lf.commit()) {
        throw new IOException("Cannot commit write to " + path);
      }
    } finally {
      lf.unlock();
    }
  }

  private void secure(final File path) throws IOException {
    if (!path.exists() && !path.createNewFile()) {
      throw new IOException("Cannot create " + path);
    }
    path.setWritable(false, false /* all */);
    path.setReadable(false, false /* all */);
    path.setExecutable(false, false /* all */);

    path.setWritable(true, true /* owner only */);
    path.setReadable(true, true /* owner only */);
  }

  private void init_gerrit_basepath(final FileBasedConfig cfg) {
    header("Git Repositories");

    File git_dir = new File(askdefault("git", "Location of Git repositories"));
    set(cfg, "gerrit", "basepath", git_dir.getPath());

    if (!importProjects) {
      importProjects = yesno("Import existing repositories");
    }
  }

  private void init_database(final FileBasedConfig cfg,
      final FileBasedConfig sec) {
    header("SQL Database");

    final String D = "database";
    DataSourceProvider.Type db_type = askdefault(H2, "Database server type");
    if (db_type == DataSourceProvider.Type.DEFAULT) {
      db_type = H2;
    }
    set(cfg, D, "type", db_type, null);

    switch (db_type) {
      case H2:
        break;

      case JDBC: {
        String driver = askdefault("", "Driver class name");
        String url = askdefault("", "url");

        set(cfg, D, "driver", driver);
        set(cfg, D, "url", url);
        break;
      }

      case POSTGRES:
      case POSTGRESQL:
      case MYSQL: {
        String def_port = "(" + db_type.toString() + " default)";
        String hostname = askdefault("localhost", "Server hostname");
        String port = askdefault(def_port, "Server port");
        String database = askdefault("reviewdb", "Database name");
        String username = askdefault("gerrit2", "Application username");
        String password =
            username != null ? password("%s's password", username) : null;

        set(cfg, D, "hostname", hostname);
        set(cfg, D, "port", port != def_port ? port : null);
        set(cfg, D, "database", database);

        set(cfg, D, "username", username);
        set(sec, D, "password", password);
        break;
      }
    }
  }

  private void init_auth(final FileBasedConfig cfg, final FileBasedConfig sec) {
    header("User Authentication");

    final String A = "auth";
    AuthType auth_type = askdefault(AuthType.OPENID, "Authentication method");
    set(cfg, A, "type", auth_type, null);

    switch (auth_type) {
      case HTTP:
      case HTTP_LDAP: {
        String def_hdr = "(HTTP Basic)";
        String hdr = askdefault(def_hdr, "Username HTTP header");
        String logoutUrl = askdefault("", "Single-sign-on logout URL");

        set(cfg, A, "httpHeader", hdr != def_hdr ? hdr : null);
        set(cfg, A, "logoutUrl", logoutUrl);
        break;
      }
    }

    switch (auth_type) {
      case LDAP:
      case HTTP_LDAP: {
        final String D = "ldap";
        String server = askdefault("ldap://localhost", "LDAP server");
        String username = askdefault(null, "Application username");
        String password =
            username != null ? password("%s's password", username) : null;

        String def_dn = server;
        if (def_dn != null) {
          if (0 < def_dn.indexOf("://")) {
            def_dn = def_dn.substring(def_dn.indexOf("://") + 3);
          }
          if (0 < def_dn.indexOf(".")) {
            def_dn = def_dn.substring(def_dn.indexOf(".") + 1);
            def_dn = "DC=" + def_dn.replaceAll("\\.", ",DC=");
          } else {
            def_dn = null;
          }
        }

        String accountBase = askdefault(def_dn, "Account BaseDN");
        String groupBase = askdefault(accountBase, "Group BaseDN");

        if (server != null && !server.startsWith("ldap://")
            && !server.startsWith("ldaps://")) {
          server = "ldap://" + server;
        }

        set(cfg, D, "server", server);
        set(cfg, D, "username", username);
        set(sec, D, "password", password);

        set(cfg, D, "accountBase", accountBase);
        set(cfg, D, "groupBase", groupBase);
        break;
      }
    }
  }

  private void init_sendemail(final FileBasedConfig cfg,
      final FileBasedConfig sec) {
    header("Email Delivery");

    final String D = "sendemail";

    String def_port = "(default)";
    String smtpserver = askdefault("localhost", "SMTP server hostname");
    String port = askdefault(def_port, "SMTP server port");
    Encryption enc = askdefault(Encryption.NONE, "SMTP encryption");
    String username = askdefault(null, "Application username");
    String password =
        username != null ? password("%s's password", username) : null;

    set(cfg, D, "smtpServer", smtpserver);
    set(cfg, D, "smtpServerPort", port != def_port ? port : null);
    set(cfg, D, "smtpEncryption", enc, Encryption.NONE);

    set(cfg, D, "smtpUser", username);
    set(sec, D, "smtpPass", password);
  }

  private <T extends Enum<?>> void set(Config cfg, String section, String name,
      T value, T def) {
    if (value != null && value != def) {
      cfg.setString(section, null, name, value.toString());
    } else {
      cfg.unset(section, null, name);
    }
  }

  private void set(Config cfg, String section, String name, String value) {
    if (value != null && !value.isEmpty()) {
      cfg.setString(section, null, name, value);
    } else {
      cfg.unset(section, null, name);
    }
  }

  private void inject() {
    dbInjector = createDbInjector();
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

  private void needyes(String msg, Object... args) {
    if (!yesno(msg, args)) {
      throw die("aborted by user");
    }
  }

  private boolean yesno(String fmt, Object... args) {
    if (console == null) {
      console = System.console();
    }
    if (console == null) {
      return true;
    }

    final String prompt = String.format(fmt, args);
    for (;;) {
      final String yn = console.readLine("%-30s [y/n]? ", prompt);
      if (yn == null) {
        throw die("aborted by user");
      }
      if (yn.equalsIgnoreCase("y") || yn.equalsIgnoreCase("yes")) {
        return true;
      }
      if (yn.equalsIgnoreCase("n") || yn.equalsIgnoreCase("no")) {
        return false;
      }
    }
  }

  private String askdefault(String def, String fmt, Object... args) {
    if (def != null && def.isEmpty()) {
      def = null;
    }
    if (console == null) {
      console = System.console();
    }
    if (console == null) {
      return def;
    }

    final String prompt = String.format(fmt, args);
    String r;
    if (def != null) {
      r = console.readLine("%-30s [%s]: ", prompt, def);
    } else {
      r = console.readLine("%-30s : ", prompt);
    }
    if (r == null) {
      throw die("aborted by user");
    }
    r = r.trim();
    if (r.isEmpty()) {
      return def;
    }
    return r;
  }

  private String password(String fmt, Object... args) {
    if (console == null) {
      console = System.console();
    }
    if (console == null) {
      return null;
    }

    final String prompt = String.format(fmt, args);
    char[] r = console.readPassword("%-30s : ", prompt);
    if (r == null) {
      throw die("aborted by user");
    }
    String s = new String(r).trim();
    return !s.isEmpty() ? s : null;
  }

  private <T extends Enum<?>> T askdefault(T def, String fmt, Object... args) {
    if (console == null) {
      console = System.console();
    }
    if (console == null) {
      return def;
    }

    final String prompt = String.format(fmt, args);
    final T[] options = all(def);
    for (;;) {
      String r = console.readLine("%-30s [%s/?]: ", fmt, def.toString());
      if (r == null) {
        throw die("aborted by user");
      }
      r = r.trim();
      if (r.isEmpty()) {
        return def;
      }
      for (final T e : options) {
        if (equalsIgnoreCase(e.toString(), r)) {
          return e;
        }
      }
      if (!"?".equals(r)) {
        console.printf("error: '%s' is not a valid choice\n", r);
      }
      console.printf("       Supported options are:\n");
      for (final T e : options) {
        console.printf("         %s\n", e.toString().toLowerCase());
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static <T extends Enum<?>> T[] all(final T defaultValue) {
    try {
      return (T[]) defaultValue.getClass().getMethod("values").invoke(null);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (SecurityException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (IllegalAccessException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (InvocationTargetException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    } catch (NoSuchMethodException e) {
      throw new IllegalArgumentException("Cannot obtain enumeration values", e);
    }
  }

  private void header(String fmt, Object... args) {
    if (console == null) {
      console = System.console();
    }
    if (console != null) {
      console.printf("\n*** " + fmt + "\n*** \n\n", args);
    }
  }
}
