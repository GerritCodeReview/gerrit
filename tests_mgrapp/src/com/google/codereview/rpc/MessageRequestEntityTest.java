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

package com.google.codereview.rpc;

import com.google.codereview.internal.SubmitChange.SubmitChangeResponse;
import com.google.protobuf.Message;

import junit.framework.TestCase;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;

public class MessageRequestEntityTest extends TestCase {
  public void testCompressedEntity() throws Exception {
    final Message msg = buildMessage();
    final byte[] bin = compress(msg);
    final String contentType =
        "application/x-google-protobuf"
            + "; name=codereview.internal.SubmitChangeResponse"
            + "; compress=deflate";

    final MessageRequestEntity mre = new MessageRequestEntity(msg);
    assertTrue(mre.isRepeatable());
    assertEquals(bin.length, mre.getContentLength());
    assertEquals(contentType, mre.getContentType());

    for (int i = 0; i < 5; i++) {
      final ByteArrayOutputStream compressed = new ByteArrayOutputStream();
      mre.writeRequest(compressed);
      assertTrue(Arrays.equals(bin, compressed.toByteArray()));
    }
  }

  private static Message buildMessage() {
    final SubmitChangeResponse.Builder r = SubmitChangeResponse.newBuilder();
    r.setStatusCode(SubmitChangeResponse.CodeType.PATCHSET_EXISTS);
    return r.build();
  }

  private static byte[] compress(final Message m) throws IOException {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final DeflaterOutputStream dos = new DeflaterOutputStream(out);
    m.writeTo(dos);
    dos.close();
    return out.toByteArray();
  }
}
