// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.httpd.restapi;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

import com.google.common.io.BaseEncoding;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.util.http.testutil.FakeHttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Random;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import org.junit.Test;

public class RestApiServletBase64Test {
  private static final int[] SIZES = {0, 1, 2, 3, 4, 8191, 8192, 8193, 100_000, (7 << 20) + 1};

  @Test
  public void base64OfResultWithKnownLength() throws Exception {
    for (int size : SIZES) {
      byte[] data = randomBytes(size);
      assertBase64Body(BinaryResult.create(data).base64(), data);
    }
  }

  @Test
  public void base64OfStreamedResultWithUnknownLength() throws Exception {
    for (int size : SIZES) {
      byte[] data = randomBytes(size);
      BinaryResult bin =
          new BinaryResult() {
            @Override
            public void writeTo(OutputStream os) throws IOException {
              // Mix single-byte and array writes, like DiffFormatter does.
              int i = 0;
              for (; i < data.length && i < 10; i++) {
                os.write(data[i]);
              }
              os.write(data, i, data.length - i);
            }
          }.base64();
      assertBase64Body(bin, data);
    }
  }

  @Test
  public void base64DoesNotCloseResponseStreamTwice() throws Exception {
    byte[] data = {0, 1, 2, 3};
    TrackingResponse res = new TrackingResponse();

    // Unknown length, so that the response is encoded while streaming it to the response.
    BinaryResult bin =
        new BinaryResult() {
          @Override
          public void writeTo(OutputStream out) throws IOException {
            out.write(data);
          }
        }.base64();

    RestApiServlet.replyBinaryResult(null, res, bin);

    assertThat(res.closeCount).isEqualTo(1);
    assertThat(res.body.toByteArray())
        .isEqualTo(BaseEncoding.base64().encode(data).getBytes(ISO_8859_1));
  }

  private static void assertBase64Body(BinaryResult bin, byte[] data) throws Exception {
    FakeHttpServletResponse res = new FakeHttpServletResponse();
    RestApiServlet.replyBinaryResult(null, res, bin);
    assertThat(res.getHeader("X-FYI-Content-Encoding")).isEqualTo("base64");
    assertThat(new String(res.getActualBody(), ISO_8859_1))
        .isEqualTo(BaseEncoding.base64().encode(data));
  }

  private static byte[] randomBytes(int size) {
    byte[] data = new byte[size];
    new Random(size).nextBytes(data);
    return data;
  }

  private static final class TrackingResponse extends FakeHttpServletResponse {
    final ByteArrayOutputStream body = new ByteArrayOutputStream();
    int closeCount;

    private final ServletOutputStream output =
        new ServletOutputStream() {
          @Override
          public void write(int b) {
            body.write(b);
          }

          @Override
          public void close() {
            closeCount++;
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

    @Override
    public synchronized ServletOutputStream getOutputStream() {
      return output;
    }
  }
}
