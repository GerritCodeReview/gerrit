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

package com.google.gerrit.server.contact;

import com.google.gerrit.common.errors.ContactInformationStoreException;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountExternalId;
import com.google.gerrit.reviewdb.ContactInformation;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.UrlEncoded;
import com.google.gerrit.server.account.AccountExternalIdCache;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralData;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;
import org.eclipse.jgit.util.IO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;

/** Encrypts {@link ContactInformation} instances and saves them. */
@Singleton
class EncryptedContactStore implements ContactStore {
  private static final Logger log =
      LoggerFactory.getLogger(EncryptedContactStore.class);
  private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

  private final SchemaFactory<ReviewDb> schema;
  private final PGPPublicKey dest;
  private final SecureRandom prng;
  private final URL storeUrl;
  private final String storeAPPSEC;
  private final AccountExternalIdCache accountExternalIdCache;

  EncryptedContactStore(final URL storeUrl, final String storeAPPSEC,
      final File pubKey, final SchemaFactory<ReviewDb> schema,
      final AccountExternalIdCache accountExternalIdCache) {
    this.storeUrl = storeUrl;
    this.storeAPPSEC = storeAPPSEC;
    this.schema = schema;
    this.dest = selectKey(readPubRing(pubKey));
    this.accountExternalIdCache = accountExternalIdCache;

    final String prngName = "SHA1PRNG";
    try {
      prng = SecureRandom.getInstance(prngName);
    } catch (NoSuchAlgorithmException e) {
      throw new ProvisionException("Cannot create " + prngName, e);
    }

    // Run a test encryption to verify the proper algorithms exist in
    // our JVM and we are able to invoke them. This helps to identify
    // a system configuration problem early at startup, rather than a
    // lot later during runtime.
    //
    try {
      encrypt("test", new Date(0), "test".getBytes("UTF-8"));
    } catch (NoSuchProviderException e) {
      throw new ProvisionException("PGP encryption not available", e);
    } catch (PGPException e) {
      throw new ProvisionException("PGP encryption not available", e);
    } catch (IOException e) {
      throw new ProvisionException("PGP encryption not available", e);
    }
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  private static PGPPublicKeyRingCollection readPubRing(final File pub) {
    try {
      InputStream in = new FileInputStream(pub);
      try {
        in = PGPUtil.getDecoderStream(in);
        return new PGPPublicKeyRingCollection(in);
      } finally {
        in.close();
      }
    } catch (IOException e) {
      throw new ProvisionException("Cannot read " + pub, e);
    } catch (PGPException e) {
      throw new ProvisionException("Cannot read " + pub, e);
    }
  }

  private static PGPPublicKey selectKey(final PGPPublicKeyRingCollection rings) {
    for (final Iterator<?> ri = rings.getKeyRings(); ri.hasNext();) {
      final PGPPublicKeyRing currRing = (PGPPublicKeyRing) ri.next();
      for (final Iterator<?> ki = currRing.getPublicKeys(); ki.hasNext();) {
        final PGPPublicKey k = (PGPPublicKey) ki.next();
        if (k.isEncryptionKey()) {
          return k;
        }
      }
    }
    return null;
  }

  public void store(final Account account, final ContactInformation info)
      throws ContactInformationStoreException {
    try {
      final byte[] plainText = format(account, info).getBytes("UTF-8");
      final String fileName = "account-" + account.getId();
      final Date fileDate = account.getContactFiledOn();
      final byte[] encText = encrypt(fileName, fileDate, plainText);
      final String encStr = new String(encText, "UTF-8");

      final Timestamp filedOn = account.getContactFiledOn();
      final UrlEncoded u = new UrlEncoded();
      if (storeAPPSEC != null) {
        u.put("APPSEC", storeAPPSEC);
      }
      if (account.getPreferredEmail() != null) {
        u.put("email", account.getPreferredEmail());
      }
      if (filedOn != null) {
        u.put("filed", String.valueOf(filedOn.getTime() / 1000L));
      }
      u.put("account_id", String.valueOf(account.getId().get()));
      u.put("data", encStr);
      final byte[] body = u.toString().getBytes("UTF-8");

      final HttpURLConnection c = (HttpURLConnection) storeUrl.openConnection();
      c.setRequestMethod("POST");
      c.setRequestProperty("Content-Type",
          "application/x-www-form-urlencoded; charset=UTF-8");
      c.setDoOutput(true);
      c.setFixedLengthStreamingMode(body.length);
      final OutputStream out = c.getOutputStream();
      out.write(body);
      out.close();

      if (c.getResponseCode() == 200) {
        final byte[] dst = new byte[2];
        final InputStream in = c.getInputStream();
        try {
          IO.readFully(in, dst, 0, 2);
        } finally {
          in.close();
        }
        if (dst[0] != 'O' || dst[1] != 'K') {
          throw new IOException("Store failed: " + c.getResponseCode());
        }
      } else {
        throw new IOException("Store failed: " + c.getResponseCode());
      }

    } catch (IOException e) {
      log.error("Cannot store encrypted contact information", e);
      throw new ContactInformationStoreException(e);
    } catch (PGPException e) {
      log.error("Cannot store encrypted contact information", e);
      throw new ContactInformationStoreException(e);
    } catch (NoSuchProviderException e) {
      log.error("Cannot store encrypted contact information", e);
      throw new ContactInformationStoreException(e);
    }
  }

  private byte[] encrypt(final String name, final Date date,
      final byte[] rawText) throws NoSuchProviderException, PGPException,
      IOException {
    final byte[] zText = compress(name, date, rawText);
    final PGPEncryptedDataGenerator cpk =
        new PGPEncryptedDataGenerator(PGPEncryptedData.CAST5, true, prng, "BC");
    cpk.addMethod(dest);

    final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    final ArmoredOutputStream aout = new ArmoredOutputStream(buf);
    final OutputStream cout = cpk.open(aout, zText.length);
    cout.write(zText);
    cout.close();
    aout.close();

    return buf.toByteArray();
  }

  private static byte[] compress(final String fileName, Date fileDate,
      final byte[] plainText) throws IOException {
    final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    final PGPCompressedDataGenerator comdg;
    final int len = plainText.length;
    if (fileDate == null) {
      fileDate = PGPLiteralData.NOW;
    }

    comdg = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
    final OutputStream out =
        new PGPLiteralDataGenerator().open(comdg.open(buf),
            PGPLiteralData.BINARY, fileName, len, fileDate);
    out.write(plainText);
    out.close();
    comdg.close();
    return buf.toByteArray();
  }

  private String format(final Account account, final ContactInformation info)
      throws ContactInformationStoreException {
    Timestamp on = account.getContactFiledOn();
    if (on == null) {
      on = new Timestamp(System.currentTimeMillis());
    }

    final SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    df.setTimeZone(UTC);

    final StringBuilder b = new StringBuilder();
    field(b, "Account-Id", account.getId().toString());
    field(b, "Date", df.format(on) + " " + UTC.getID());
    field(b, "Full-Name", account.getFullName());
    field(b, "Preferred-Email", account.getPreferredEmail());

    try {
      final ReviewDb db = schema.open();
      try {
        for (final AccountExternalId e : accountExternalIdCache.byAccount(
            account.getId())) {
          final StringBuilder oistr = new StringBuilder();
          if (e.getEmailAddress() != null && e.getEmailAddress().length() > 0) {
            if (oistr.length() > 0) {
              oistr.append(' ');
            }
            oistr.append(e.getEmailAddress());
          }
          if (e.isScheme(AccountExternalId.SCHEME_MAILTO)) {
            if (oistr.length() > 0) {
              oistr.append(' ');
            }
            oistr.append('<');
            oistr.append(e.getExternalId());
            oistr.append('>');
          }
          field(b, "Identity", oistr.toString());
        }
      } finally {
        db.close();
      }
    } catch (OrmException e) {
      throw new ContactInformationStoreException(e);
    }

    field(b, "Address", info.getAddress());
    field(b, "Country", info.getCountry());
    field(b, "Phone-Number", info.getPhoneNumber());
    field(b, "Fax-Number", info.getFaxNumber());
    return b.toString();
  }

  private static void field(final StringBuilder b, final String name,
      String value) {
    if (value == null) {
      return;
    }
    value = value.trim();
    if (value.length() == 0) {
      return;
    }

    b.append(name);
    b.append(':');
    if (value.indexOf('\n') == -1) {
      b.append(' ');
      b.append(value);
    } else {
      value = value.replaceAll("\r\n", "\n");
      value = value.replaceAll("\n", "\n\t");
      b.append("\n\t");
      b.append(value);
    }
    b.append('\n');
  }
}
