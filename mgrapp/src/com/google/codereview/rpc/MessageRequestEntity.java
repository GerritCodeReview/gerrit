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

import com.google.protobuf.Message;

import org.apache.commons.httpclient.methods.RequestEntity;
import org.spearce.jgit.lib.NullProgressMonitor;
import org.spearce.jgit.util.TemporaryBuffer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;

/**
 * Formats a protocol buffer Message into an HTTP request body.
 * <p>
 * The compressed binary serialized form is always used.
 */
public class MessageRequestEntity implements RequestEntity {
  /** Content-Type for a protocol buffer. */
  public static final String TYPE = "application/x-google-protobuf";

  private final Message msg;
  private final String name;
  private final TemporaryBuffer temp;

  /**
   * Create a new request for a single message.
   * 
   * @param m the message to serialize and transmit.
   * @throws IOException the message could not be compressed into temporary
   *         storage. The local file system may be full, or the message is
   *         unable to compress itself.
   */
  public MessageRequestEntity(final Message m) throws IOException {
    msg = m;
    name = msg.getDescriptorForType().getFullName();
    temp = new TemporaryBuffer();
    final DeflaterOutputStream dos = new DeflaterOutputStream(temp);
    try {
      msg.writeTo(dos);
    } finally {
      dos.close();
    }
  }

  public long getContentLength() {
    return (int) temp.length();
  }

  public String getContentType() {
    return TYPE + "; name=" + name + "; compress=deflate";
  }

  public boolean isRepeatable() {
    return true;
  }

  public void writeRequest(final OutputStream out) throws IOException {
    temp.writeTo(out, NullProgressMonitor.INSTANCE);
  }

  public void destroy() {
    temp.destroy();
  }

  @Override
  public String toString() {
    final StringBuilder r = new StringBuilder();
    r.append(name);
    r.append('\n');
    r.append(msg.toString());
    return r.toString();
  }
}
