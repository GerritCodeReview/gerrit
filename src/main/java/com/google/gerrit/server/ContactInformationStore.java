// Copyright 2009 Google Inc.
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

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.ContactInformation;
import com.google.gerrit.client.rpc.ContactInformationStoreException;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import org.apache.sshd.common.util.SecurityUtils;
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
import org.mortbay.util.UrlEncoded;
import org.spearce.jgit.util.NB;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Iterator;

/** Encrypts {@link ContactInformation} instances and saves them. */
public class ContactInformationStore {
  private static boolean inited;
  private static ContactInformationStore self;

  private static synchronized ContactInformationStore getInstance()
      throws ContactInformationStoreException {
    if (!inited) {
      inited = true;
      self = new ContactInformationStore();
    }
    if (self == null) {
      throw new ContactInformationStoreException();
    }
    return self;
  }

  public static void store(final Account account, final ContactInformation info)
      throws ContactInformationStoreException {
    getInstance().storeImpl(account, info);
  }

  private PGPPublicKey dest;
  private SecureRandom prng;
  private URL storeUrl;
  private String storeAPPSEC;

  private ContactInformationStore() throws ContactInformationStoreException {
    final GerritServer gs;
    try {
      gs = GerritServer.getInstance();
    } catch (OrmException e) {
      throw new ContactInformationStoreException(e);
    } catch (XsrfException e) {
      throw new ContactInformationStoreException(e);
    }

    if (gs.getContactStoreURL() == null) {
      throw new ContactInformationStoreException(new IllegalStateException(
          "No contactStoreURL configured"));
    }
    try {
      storeUrl = new URL(gs.getContactStoreURL());
    } catch (MalformedURLException e) {
      throw new ContactInformationStoreException(e);
    }
    storeAPPSEC = gs.getContactStoreAPPSEC();

    if (!SecurityUtils.isBouncyCastleRegistered()) {
      throw new ContactInformationStoreException(new NoSuchProviderException(
          "BC (aka BouncyCastle)"));
    }

    try {
      prng = SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new ContactInformationStoreException(e);
    }

    dest = selectKey(readPubRing(gs));
  }

  private PGPPublicKeyRingCollection readPubRing(final GerritServer gs)
      throws ContactInformationStoreException {
    final File pub = new File(gs.getSitePath(), "contact_information.pub");
    try {
      InputStream in = new FileInputStream(pub);
      try {
        in = PGPUtil.getDecoderStream(in);
        return new PGPPublicKeyRingCollection(in);
      } finally {
        in.close();
      }
    } catch (IOException e) {
      throw new ContactInformationStoreException(e);
    } catch (PGPException e) {
      throw new ContactInformationStoreException(e);
    }
  }

  private PGPPublicKey selectKey(final PGPPublicKeyRingCollection rings) {
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

  private void storeImpl(final Account account, final ContactInformation info)
      throws ContactInformationStoreException {
    if (storeUrl == null) {
      // No data store configure? Don't perform the store operation.
      //
      return;
    }
    if (account == null || account.getPreferredEmail() == null) {
      // The backing data store always wants the preferred email to
      // help index the records for easier lookup later on.
      //
      return;
    }

    try {
      final byte[] plainText = format(account, info).getBytes("UTF-8");
      final byte[] encText = encrypt(dest, compress(account, plainText));
      final String encStr = new String(encText, "UTF-8");

      final UrlEncoded u = new UrlEncoded();
      u.add("APPSEC", storeAPPSEC);
      u.add("account_id", String.valueOf(account.getId().get()));
      u.add("email", account.getPreferredEmail());
      u.add("data", encStr);
      final byte[] body = u.encode().getBytes("UTF-8");

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
          NB.readFully(in, dst, 0, 2);
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
      throw new ContactInformationStoreException(e);
    } catch (PGPException e) {
      throw new ContactInformationStoreException(e);
    } catch (NoSuchProviderException e) {
      throw new ContactInformationStoreException(e);
    }
  }

  private byte[] encrypt(final PGPPublicKey dst, final byte[] zText)
      throws NoSuchProviderException, PGPException, IOException {
    final PGPEncryptedDataGenerator cpk =
        new PGPEncryptedDataGenerator(PGPEncryptedData.CAST5, true, prng, "BC");
    cpk.addMethod(dst);

    final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    final ArmoredOutputStream aout = new ArmoredOutputStream(buf);
    final OutputStream cout = cpk.open(aout, zText.length);
    cout.write(zText);
    cout.close();
    aout.close();

    return buf.toByteArray();
  }

  private static byte[] compress(final Account account, final byte[] plainText)
      throws IOException {
    final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    final PGPCompressedDataGenerator comdg;
    final String name = "account-" + account.getId();
    final int len = plainText.length;
    Date date = account.getContactFiledOn();
    if (date == null) {
      date = PGPLiteralData.NOW;
    }

    comdg = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
    final OutputStream out =
        new PGPLiteralDataGenerator().open(comdg.open(buf),
            PGPLiteralData.BINARY, name, len, date);
    out.write(plainText);
    out.close();
    comdg.close();
    return buf.toByteArray();
  }

  private static String format(final Account account,
      final ContactInformation info) {
    Timestamp on = account.getContactFiledOn();
    if (on == null) {
      on = new java.sql.Timestamp(System.currentTimeMillis());
    }

    final StringBuilder b = new StringBuilder();
    field(b, "Account-Id", account.getId().toString());
    field(b, "Date", on.toString());
    field(b, "Full-Name", account.getFullName());
    field(b, "Preferred-Email", account.getPreferredEmail());
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
