// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.util.http.testutil;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.net.HttpHeaders;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Locale;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.util.RawParseUtils;

/** Simple fake implementation of {@link HttpServletResponse}. */
public class FakeHttpServletResponse implements HttpServletResponse {
  private final ByteArrayOutputStream actualBody = new ByteArrayOutputStream();
  private final ListMultimap<String, String> headers = LinkedListMultimap.create();

  private int status = SC_OK;
  private boolean committed;
  private ServletOutputStream outputStream;
  private PrintWriter writer;

  public FakeHttpServletResponse() {}

  @Override
  public synchronized void flushBuffer() throws IOException {
    if (outputStream != null) {
      outputStream.flush();
    }
    if (writer != null) {
      writer.flush();
    }
  }

  @Override
  public int getBufferSize() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getCharacterEncoding() {
    return UTF_8.name();
  }

  @Override
  public String getContentType() {
    return null;
  }

  @Override
  public Locale getLocale() {
    return Locale.US;
  }

  @Override
  public synchronized ServletOutputStream getOutputStream() {
    checkState(writer == null, "getWriter() already called");
    if (outputStream == null) {
      outputStream =
          new ServletOutputStream() {
            @Override
            public void write(int c) throws IOException {
              actualBody.write(c);
            }

            @Override
            public boolean isReady() {
              return true;
            }

            @Override
            public void setWriteListener(WriteListener listener) {
              throw new UnsupportedOperationException();
            }
          };
    }
    return outputStream;
  }

  @Override
  public synchronized PrintWriter getWriter() {
    checkState(outputStream == null, "getOutputStream() already called");
    if (writer == null) {
      writer = new PrintWriter(actualBody);
    }
    return writer;
  }

  @Override
  public synchronized boolean isCommitted() {
    return committed;
  }

  @Override
  public void reset() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void resetBuffer() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setBufferSize(int sz) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setCharacterEncoding(String name) {
    checkArgument(UTF_8.equals(Charset.forName(name)), "unsupported charset: %s", name);
  }

  @Override
  public void setContentLength(int length) {
    setContentLengthLong(length);
  }

  @Override
  public void setContentLengthLong(long length) {
    headers.removeAll(HttpHeaders.CONTENT_LENGTH);
    addHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(length));
  }

  @Override
  public void setContentType(String type) {
    headers.removeAll(HttpHeaders.CONTENT_TYPE);
    addHeader(HttpHeaders.CONTENT_TYPE, type);
  }

  @Override
  public void setLocale(Locale locale) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addCookie(Cookie cookie) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addDateHeader(String name, long value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addHeader(String name, String value) {
    headers.put(name.toLowerCase(), value);
  }

  @Override
  public void addIntHeader(String name, int value) {
    addHeader(name, Integer.toString(value));
  }

  @Override
  public boolean containsHeader(String name) {
    return headers.containsKey(name.toLowerCase());
  }

  @Override
  public String encodeRedirectURL(String url) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public String encodeRedirectUrl(String url) {
    throw new UnsupportedOperationException();
  }

  @Override
  public String encodeURL(String url) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Deprecated
  public String encodeUrl(String url) {
    throw new UnsupportedOperationException();
  }

  @Override
  public synchronized void sendError(int sc) {
    status = sc;
    committed = true;
  }

  @Override
  public synchronized void sendError(int sc, String msg) {
    status = sc;
    committed = true;
  }

  @Override
  public synchronized void sendRedirect(String loc) {
    status = SC_FOUND;
    setHeader(HttpHeaders.LOCATION, loc);
    committed = true;
  }

  @Override
  public void setDateHeader(String name, long value) {
    setHeader(name, Long.toString(value));
  }

  @Override
  public void setHeader(String name, String value) {
    headers.removeAll(name.toLowerCase());
    addHeader(name, value);
  }

  @Override
  public void setIntHeader(String name, int value) {
    headers.removeAll(name.toLowerCase());
    addIntHeader(name, value);
  }

  @Override
  public synchronized void setStatus(int sc) {
    status = sc;
    committed = true;
  }

  @Override
  @Deprecated
  public synchronized void setStatus(int sc, String msg) {
    status = sc;
    committed = true;
  }

  @Override
  public synchronized int getStatus() {
    return status;
  }

  @Override
  public String getHeader(String name) {
    return Iterables.getFirst(headers.get(checkNotNull(name.toLowerCase())), null);
  }

  @Override
  public Collection<String> getHeaderNames() {
    return headers.keySet();
  }

  @Override
  public Collection<String> getHeaders(String name) {
    return headers.get(checkNotNull(name.toLowerCase()));
  }

  public byte[] getActualBody() {
    return actualBody.toByteArray();
  }

  public String getActualBodyString() {
    return RawParseUtils.decode(getActualBody());
  }
}
