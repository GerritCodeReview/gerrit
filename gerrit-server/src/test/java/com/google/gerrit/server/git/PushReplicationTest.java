// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static com.google.gerrit.server.git.PushReplication.ReplicationConfig.*;
import junit.framework.TestCase;

import org.eclipse.jgit.transport.URIish;

import java.net.URISyntaxException;

public class PushReplicationTest extends TestCase {
  public void testNeedsUrlEncoding() throws URISyntaxException {
    assertTrue(needsUrlEncoding(new URIish("http://host/path")));
    assertTrue(needsUrlEncoding(new URIish("https://host/path")));
    assertTrue(needsUrlEncoding(new URIish("amazon-s3://config/bucket/path")));

    assertFalse(needsUrlEncoding(new URIish("host:path")));
    assertFalse(needsUrlEncoding(new URIish("user@host:path")));
    assertFalse(needsUrlEncoding(new URIish("git://host/path")));
    assertFalse(needsUrlEncoding(new URIish("ssh://host/path")));
  }

  public void testUrlEncoding() {
    assertEquals("foo/bar/thing", encode("foo/bar/thing"));
    assertEquals("--%20All%20Projects%20--", encode("-- All Projects --"));
    assertEquals("name/with%20a%20space", encode("name/with a space"));
    assertEquals("name%0Awith-LF", encode("name\nwith-LF"));
  }
}
