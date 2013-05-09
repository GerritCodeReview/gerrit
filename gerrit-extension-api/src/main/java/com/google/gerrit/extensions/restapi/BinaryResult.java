// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.extensions.restapi;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

/**
 * Wrapper around a non-JSON result from a {@link RestView}.
 * <p>
 * Views may return this type to signal they want the server glue to write raw
 * data to the client, instead of attempting automatic conversion to JSON. The
 * create form is overloaded to handle plain text from a String, or binary data
 * from a {@code byte[]} or {@code InputSteam}.
 */
public abstract class BinaryResult implements Closeable {
  /** Default MIME type for unknown binary data. */
  static final String OCTET_STREAM = "application/octet-stream";

  /** Produce a UTF-8 encoded result from a string. */
  public static BinaryResult create(String data) {
    try {
      return create(data.getBytes("UTF-8"))
        .setContentType("text/plain")
        .setCharacterEncoding("UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("JVM does not support UTF-8", e);
    }
  }

  /** Produce an {@code application/octet-stream} result from a byte array. */
  public static BinaryResult create(byte[] data) {
    return new Array(data);
  }

  /**
   * Produce an {@code application/octet-stream} of unknown length by copying
   * the InputStream until EOF. The server glue will automatically close this
   * stream when copying is complete.
   */
  public static BinaryResult create(InputStream data) {
    return new Stream(data);
  }

  private String contentType = OCTET_STREAM;
  private String characterEncoding;
  private long contentLength = -1;
  private boolean gzip = true;
  private boolean base64 = false;

  /** @return the MIME type of the result, for HTTP clients. */
  public String getContentType() {
    String enc = getCharacterEncoding();
    if (enc != null) {
      return contentType + "; charset=" + enc;
    }
    return contentType;
  }

  /** Set the MIME type of the result, and return {@code this}. */
  public BinaryResult setContentType(String contentType) {
    this.contentType = contentType != null ? contentType : OCTET_STREAM;
    return this;
  }

  /** Get the character encoding; null if not known. */
  public String getCharacterEncoding() {
    return characterEncoding;
  }

  /** Set the character set used to encode text data and return {@code this}. */
  public BinaryResult setCharacterEncoding(String encoding) {
    characterEncoding = encoding;
    return this;
  }

  /** @return length in bytes of the result; -1 if not known. */
  public long getContentLength() {
    return contentLength;
  }

  /** Set the content length of the result; -1 if not known. */
  public BinaryResult setContentLength(long len) {
    this.contentLength = len;
    return this;
  }

  /** @return true if this result can be gzip compressed to clients. */
  public boolean canGzip() {
    return gzip;
  }

  /** Disable gzip compression for already compressed responses. */
  public BinaryResult disableGzip() {
    this.gzip = false;
    return this;
  }

  /** @return true if the result must be base64 encoded. */
  public boolean isBase64() {
    return base64;
  }

  /** Wrap the binary data in base64 encoding. */
  public BinaryResult base64() {
    base64 = true;
    return this;
  }

  /**
   * Write or copy the result onto the specified output stream.
   *
   * @param os stream to write result data onto. This stream will be closed by
   *        the caller after this method returns.
   * @throws IOException if the data cannot be produced, or the OutputStream
   *         {@code os} throws any IOException during a write or flush call.
   */
  public abstract void writeTo(OutputStream os) throws IOException;

  /** Close the result and release any resources it holds. */
  public void close() throws IOException {
  }

  @Override
  public String toString() {
    if (getContentLength() >= 0) {
      return String.format(
          "BinaryResult[Content-Type: %s, Content-Length: %d]",
          getContentType(), getContentLength());
    }
    return String.format(
        "BinaryResult[Content-Type: %s, Content-Length: unknown]",
        getContentType());
  }

  private static class Array extends BinaryResult {
    private final byte[] data;

    Array(byte[] data) {
      this.data = data;
      setContentLength(data.length);
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
      os.write(data);
    }
  }

  private static class Stream extends BinaryResult {
    private final InputStream src;

    Stream(InputStream src) {
      this.src = src;
    }

    @Override
    public void writeTo(OutputStream dst) throws IOException {
      byte[] tmp = new byte[4096];
      int n;
      while (0 < (n = src.read(tmp))) {
        dst.write(tmp, 0, n);
      }
    }

    @Override
    public void close() throws IOException {
      src.close();
    }
  }
}
