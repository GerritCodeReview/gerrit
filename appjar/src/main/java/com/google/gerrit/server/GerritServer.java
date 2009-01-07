// Copyright 2008 Google Inc.
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

import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.client.data.GitwebLink;
import com.google.gerrit.client.data.GroupCache;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.git.RepositoryCache;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;
import com.google.gwtorm.jdbc.Database;
import com.google.gwtorm.jdbc.SimpleDataSource;

import org.apache.commons.codec.binary.Base64;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Global server-side state for Gerrit. */
public class GerritServer {
  private static GerritServer impl;

  /**
   * Obtain the singleton server instance for this web application.
   * 
   * @return the server instance. Never null.
   * @throws OrmException the database could not be configured. There is
   *         something wrong with the schema configuration in {@link ReviewDb}
   *         that must be addressed by a developer.
   * @throws XsrfException the XSRF support could not be correctly configured to
   *         protect the application against cross-site request forgery. The JVM
   *         is most likely lacking critical security algorithms.
   */
  public static synchronized GerritServer getInstance() throws OrmException,
      XsrfException {
    if (impl == null) {
      try {
        impl = new GerritServer();
      } catch (OrmException e) {
        e.printStackTrace();
        throw e;
      }
    }
    return impl;
  }

  private final Database<ReviewDb> db;
  private SystemConfig sConfig;
  private final SignedToken xsrf;
  private final SignedToken account;
  private final RepositoryCache repositories;
  private final GroupCache groupCache;

  private GerritServer() throws OrmException, XsrfException {
    db = createDatabase();
    loadSystemConfig();
    if (sConfig == null) {
      throw new OrmException("No " + SystemConfig.class.getName() + " found");
    }

    xsrf = new SignedToken(sConfig.maxSessionAge, sConfig.xsrfPrivateKey);
    account = new SignedToken(sConfig.maxSessionAge, sConfig.accountPrivateKey);

    if (sConfig.gitBasePath != null) {
      repositories = new RepositoryCache(new File(sConfig.gitBasePath));
    } else {
      repositories = null;
    }

    groupCache = new GroupCache(db, sConfig);
  }

  private Database<ReviewDb> createDatabase() throws OrmException {
    final String dsName = "java:comp/env/jdbc/ReviewDb";
    final String pName = "GerritServer.properties";
    DataSource ds;
    try {
      ds = (DataSource) new InitialContext().lookup(dsName);
    } catch (NamingException namingErr) {
      final Properties p = readGerritDataSource(pName);
      if (p == null) {
        throw new OrmException("No DataSource " + dsName + " and no " + pName
            + " in CLASSPATH.  GerritServer requires either format.", namingErr);
      }

      try {
        ds = new SimpleDataSource(p);
      } catch (SQLException se) {
        throw new OrmException("Database in " + pName + " unavailable", se);
      }
    }
    return new Database<ReviewDb>(ds, ReviewDb.class);
  }

  private Properties readGerritDataSource(final String name)
      throws OrmException {
    final Properties srvprop = new Properties();
    final InputStream in;

    in = getClass().getClassLoader().getResourceAsStream(name);
    if (in == null) {
      return null;
    }
    try {
      try {
        srvprop.load(in);
      } finally {
        in.close();
      }
    } catch (IOException e) {
      throw new OrmException("Cannot read " + name, e);
    }

    final Properties dbprop = new Properties();
    for (final Map.Entry<Object, Object> e : srvprop.entrySet()) {
      final String key = (String) e.getKey();
      if (key.startsWith("database.")) {
        dbprop.put(key.substring("database.".length()), e.getValue());
      }
    }
    return dbprop;
  }

  private void initSystemConfig(final ReviewDb c) throws OrmException {
    final AccountGroup admin =
        new AccountGroup(new AccountGroup.NameKey("Administrators"),
            new AccountGroup.Id(c.nextAccountGroupId()));
    admin.setDescription("Gerrit Site Administrators");
    c.accountGroups().insert(Collections.singleton(admin));

    final AccountGroup anonymous =
        new AccountGroup(new AccountGroup.NameKey("Anonymous Users"),
            new AccountGroup.Id(c.nextAccountGroupId()));
    anonymous.setDescription("Any user, signed-in or not");
    anonymous.setOwnerGroupId(admin.getId());
    c.accountGroups().insert(Collections.singleton(anonymous));

    final AccountGroup registered =
        new AccountGroup(new AccountGroup.NameKey("Registered Users"),
            new AccountGroup.Id(c.nextAccountGroupId()));
    registered.setDescription("Any signed-in user");
    registered.setOwnerGroupId(admin.getId());
    c.accountGroups().insert(Collections.singleton(registered));

    final SystemConfig s = SystemConfig.create();
    s.xsrfPrivateKey = SignedToken.generateRandomKey();
    s.accountPrivateKey = SignedToken.generateRandomKey();
    s.sshdPort = 29418;
    s.adminGroupId = admin.getId();
    s.anonymousGroupId = anonymous.getId();
    s.registeredGroupId = registered.getId();
    c.systemConfig().insert(Collections.singleton(s));
  }

  private void initVerifiedCategory(final ReviewDb c) throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("VRIF"), "Verified");
    cat.setPosition((short) 0);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Verified"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "Fails"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals);
    txn.commit();
  }

  private void initCodeReviewCategory(final ReviewDb c) throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("CRVW"), "Code Review");
    cat.setPosition((short) 1);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 2, "Looks good to me, approved"));
    vals.add(value(cat, 1, "Looks good to me, but someone else must approve"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "I would prefer that you didn't submit this"));
    vals.add(value(cat, -2, "Do not submit"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals);
    txn.commit();

    final ProjectRight approve =
        new ProjectRight(new ProjectRight.Key(ProjectRight.WILD_PROJECT, cat
            .getId(), sConfig.registeredGroupId));
    approve.setMaxValue((short) 1);
    approve.setMinValue((short) -1);
    c.projectRights().insert(Collections.singleton(approve));
  }

  private void initSubmitCategory(final ReviewDb c) throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("SUBM"), "Submit");
    cat.setPosition((short) -1);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Submit"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals);
    txn.commit();
  }

  private static ApprovalCategoryValue value(final ApprovalCategory cat,
      final int value, final String name) {
    return new ApprovalCategoryValue(new ApprovalCategoryValue.Id(cat.getId(),
        (short) value), name);
  }

  private void loadSystemConfig() throws OrmException {
    final ReviewDb c = db.open();
    try {
      try {
        sConfig = c.systemConfig().get(new SystemConfig.Key());
      } catch (OrmException e) {
        // Assume the schema doesn't exist, and create it.
        // TODO Implement schema upgrades and/or exporting to a script file.
        //
        sConfig = null;
        c.createSchema();
      }

      if (sConfig == null) {
        // Assume the schema is empty and populate it.
        //
        initSystemConfig(c);
        sConfig = c.systemConfig().get(new SystemConfig.Key());
        initVerifiedCategory(c);
        initCodeReviewCategory(c);
        initSubmitCategory(c);
      }

      loadGerritConfig(c);
    } finally {
      c.close();
    }
  }

  private void loadGerritConfig(final ReviewDb db) throws OrmException {
    final GerritConfig r = new GerritConfig();
    r.setCanonicalUrl(getCanonicalURL());
    r.setSshdPort(sConfig.sshdPort);
    if (sConfig.gitwebUrl != null) {
      r.setGitwebLink(new GitwebLink(sConfig.gitwebUrl));
    }

    for (final ApprovalCategory c : db.approvalCategories().all()) {
      r.add(new ApprovalType(c, db.approvalCategoryValues().byCategory(
          c.getId()).toList()));
    }

    Common.setGerritConfig(r);
  }

  /** Get the {@link ReviewDb} schema factory for the server. */
  public Database<ReviewDb> getDatabase() {
    return db;
  }

  /** Time (in seconds) that user sessions stay "signed in". */
  public int getSessionAge() {
    return sConfig.maxSessionAge;
  }

  /** Get the signature support used to protect against XSRF attacks. */
  public SignedToken getXsrfToken() {
    return xsrf;
  }

  /** Get the signature support used to protect user identity cookies. */
  public SignedToken getAccountToken() {
    return account;
  }

  /** A binary string key to encrypt cookies related to account data. */
  public String getAccountCookieKey() {
    byte[] r = new byte[sConfig.accountPrivateKey.length()];
    for (int k = r.length - 1; k >= 0; k--) {
      r[k] = (byte) sConfig.accountPrivateKey.charAt(k);
    }
    r = Base64.decodeBase64(r);
    final StringBuilder b = new StringBuilder();
    for (int i = 0; i < r.length; i++) {
      b.append((char) r[i]);
    }
    return b.toString();
  }

  /** Local filesystem location of header/footer/CSS configuration files. */
  public File getSitePath() {
    return sConfig.sitePath != null ? new File(sConfig.sitePath) : null;
  }

  /** Optional canonical URL for this application. */
  public String getCanonicalURL() {
    String u = sConfig.canonicalUrl;
    if (u != null && !u.endsWith("/")) {
      u += "/";
    }
    return u;
  }

  /** Get the repositories maintained by this server. */
  public RepositoryCache getRepositoryCache() {
    return repositories;
  }

  /** Get the group membership cache. */
  public GroupCache getGroupCache() {
    return groupCache;
  }
}
