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

import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SystemConfig;
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
      impl = new GerritServer();
    }
    return impl;
  }

  private final Database<ReviewDb> db;
  private final SystemConfig config;
  private final SignedToken xsrf;
  private final SignedToken account;

  private GerritServer() throws OrmException, XsrfException {
    db = createDatabase();
    config = readSystemConfig();
    if (config == null) {
      throw new OrmException("No " + SystemConfig.class.getName() + " found");
    }

    xsrf = new SignedToken(config.maxSessionAge, config.xsrfPrivateKey);
    account = new SignedToken(config.maxSessionAge, config.accountPrivateKey);
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
    final SystemConfig s = SystemConfig.create();
    s.xsrfPrivateKey = SignedToken.generateRandomKey();
    s.accountPrivateKey = SignedToken.generateRandomKey();
    c.systemConfig().insert(Collections.singleton(s));
  }

  private void initCodeReviewCategory(final ReviewDb c) throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("CRVW"), "Code Review");
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 2, "Looks good to me, approved"));
    vals.add(value(cat, 1, "Looks good to me, but someone else must approve"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "I would prefer that you didn't submit this"));
    vals.add(value(cat, -2, "Do not submit"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals);
    txn.commit();
  }

  private void initVerifiedCategory(final ReviewDb c) throws OrmException {
    final Transaction txn = c.beginTransaction();
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("VRIF"), "Verified");
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Verified"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "Fails"));
    c.approvalCategories().insert(Collections.singleton(cat), txn);
    c.approvalCategoryValues().insert(vals);
    txn.commit();
  }

  private static ApprovalCategoryValue value(final ApprovalCategory cat,
      final int value, final String name) {
    return new ApprovalCategoryValue(new ApprovalCategoryValue.Key(cat.getId(),
        (short) value), name);
  }

  private SystemConfig readSystemConfig() throws OrmException {
    final ReviewDb c = db.open();
    try {
      SystemConfig sysconf;
      try {
        sysconf = c.systemConfig().get(new SystemConfig.Key());
      } catch (OrmException e) {
        // Assume the schema doesn't exist, and create it.
        // TODO Implement schema upgrades and/or exporting to a script file.
        //
        sysconf = null;
        c.createSchema();
      }

      if (sysconf == null) {
        // Assume the schema is empty and populate it.
        //
        initSystemConfig(c);
        initCodeReviewCategory(c);
        initVerifiedCategory(c);
        sysconf = c.systemConfig().get(new SystemConfig.Key());
      }

      return sysconf;
    } finally {
      c.close();
    }
  }

  /** Get the {@link ReviewDb} schema factory for the server. */
  public Database<ReviewDb> getDatabase() {
    return db;
  }

  /** Time (in seconds) that user sessions stay "signed in". */
  public int getSessionAge() {
    return config.maxSessionAge;
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
    byte[] r = new byte[config.accountPrivateKey.length()];
    for (int k = r.length - 1; k >= 0; k--) {
      r[k] = (byte) config.accountPrivateKey.charAt(k);
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
    return config.sitePath != null ? new File(config.sitePath) : null;
  }
}
