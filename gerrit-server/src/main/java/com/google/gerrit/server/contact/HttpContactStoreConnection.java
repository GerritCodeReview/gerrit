// Copyright 2011 Google Inc. All Rights Reserved.

package com.google.gerrit.server.contact;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.FactoryModuleBuilder;

import org.eclipse.jgit.util.IO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/** {@link ContactStoreConnection} with an underlying {@HttpURLConnection}. */
public class HttpContactStoreConnection implements ContactStoreConnection {
  public static Module module() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        install(new FactoryModuleBuilder()
            .implement(ContactStoreConnection.class, HttpContactStoreConnection.class)
            .build(ContactStoreConnection.Factory.class));
      }
    };
  }

  private final HttpURLConnection conn;

  @Inject
  HttpContactStoreConnection(@Assisted final URL url) throws IOException {
    final URLConnection urlConn = url.openConnection();
    if (!(urlConn instanceof HttpURLConnection)) {
      throw new IllegalArgumentException("Non-HTTP URL not supported: " + urlConn);
    }
    conn = (HttpURLConnection) urlConn;
  }

  @Override
  public void store(final byte[] body) throws IOException {
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type",
        "application/x-www-form-urlencoded; charset=UTF-8");
    conn.setDoOutput(true);
    conn.setFixedLengthStreamingMode(body.length);
    final OutputStream out = conn.getOutputStream();
    out.write(body);
    out.close();
    if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
      throw new IOException("Connection failed: " + conn.getResponseCode());
    }
    final byte[] dst = new byte[2];
    final InputStream in = conn.getInputStream();
    try {
      IO.readFully(in, dst, 0, 2);
    } finally {
      in.close();
    }
    if (dst[0] != 'O' || dst[1] != 'K') {
      throw new IOException("Store failed: " + dst[0] + dst[1]);
    }
  }
}
