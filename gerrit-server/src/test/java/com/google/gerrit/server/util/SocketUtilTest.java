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

package com.google.gerrit.server.util;

import static com.google.gerrit.server.util.SocketUtil.format;
import static com.google.gerrit.server.util.SocketUtil.hostname;
import static com.google.gerrit.server.util.SocketUtil.isIPv6;
import static com.google.gerrit.server.util.SocketUtil.parse;
import static com.google.gerrit.server.util.SocketUtil.resolve;
import static java.net.InetAddress.getByName;
import static java.net.InetSocketAddress.createUnresolved;

import junit.framework.TestCase;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

public class SocketUtilTest extends TestCase {
  public void testIsIPv6() throws UnknownHostException {
    final InetAddress ipv6 = getByName("1:2:3:4:5:6:7:8");
    assertTrue(ipv6 instanceof Inet6Address);
    assertTrue(isIPv6(ipv6));

    final InetAddress ipv4 = getByName("127.0.0.1");
    assertTrue(ipv4 instanceof Inet4Address);
    assertFalse(isIPv6(ipv4));
  }

  public void testHostname() {
    assertEquals("*", hostname(new InetSocketAddress(80)));
    assertEquals("localhost", hostname(new InetSocketAddress("localhost", 80)));
    assertEquals("foo", hostname(createUnresolved("foo", 80)));
  }

  public void testFormat() throws UnknownHostException {
    assertEquals("*:1234", format(new InetSocketAddress(1234), 80));
    assertEquals("*", format(new InetSocketAddress(80), 80));

    assertEquals("foo:1234", format(createUnresolved("foo", 1234), 80));
    assertEquals("foo", format(createUnresolved("foo", 80), 80));

    assertEquals("[1:2:3:4:5:6:7:8]:1234",//
        format(new InetSocketAddress(getByName("1:2:3:4:5:6:7:8"), 1234), 80));
    assertEquals("[1:2:3:4:5:6:7:8]",//
        format(new InetSocketAddress(getByName("1:2:3:4:5:6:7:8"), 80), 80));

    assertEquals("localhost:1234",//
        format(new InetSocketAddress("localhost", 1234), 80));
    assertEquals("localhost",//
        format(new InetSocketAddress("localhost", 80), 80));
  }

  public void testParse() {
    assertEquals(new InetSocketAddress(1234), parse("*:1234", 80));
    assertEquals(new InetSocketAddress(80), parse("*", 80));
    assertEquals(new InetSocketAddress(1234), parse(":1234", 80));
    assertEquals(new InetSocketAddress(80), parse("", 80));

    assertEquals(createUnresolved("1:2:3:4:5:6:7:8", 1234), //
        parse("[1:2:3:4:5:6:7:8]:1234", 80));
    assertEquals(createUnresolved("1:2:3:4:5:6:7:8", 80), //
        parse("[1:2:3:4:5:6:7:8]", 80));

    assertEquals(createUnresolved("localhost", 1234), //
        parse("[localhost]:1234", 80));
    assertEquals(createUnresolved("localhost", 80), //
        parse("[localhost]", 80));

    assertEquals(createUnresolved("foo.bar.example.com", 1234), //
        parse("[foo.bar.example.com]:1234", 80));
    assertEquals(createUnresolved("foo.bar.example.com", 80), //
        parse("[foo.bar.example.com]", 80));

    try {
      parse("[:3", 80);
      fail("did not throw exception");
    } catch (IllegalArgumentException e) {
      assertEquals("invalid IPv6: [:3", e.getMessage());
    }

    try {
      parse("localhost:A", 80);
      fail("did not throw exception");
    } catch (IllegalArgumentException e) {
      assertEquals("invalid port: localhost:A", e.getMessage());
    }
  }

  public void testResolve() throws UnknownHostException {
    assertEquals(new InetSocketAddress(1234), resolve("*:1234", 80));
    assertEquals(new InetSocketAddress(80), resolve("*", 80));
    assertEquals(new InetSocketAddress(1234), resolve(":1234", 80));
    assertEquals(new InetSocketAddress(80), resolve("", 80));

    assertEquals(new InetSocketAddress(getByName("1:2:3:4:5:6:7:8"), 1234), //
        resolve("[1:2:3:4:5:6:7:8]:1234", 80));
    assertEquals(new InetSocketAddress(getByName("1:2:3:4:5:6:7:8"), 80), //
        resolve("[1:2:3:4:5:6:7:8]", 80));

    assertEquals(new InetSocketAddress(getByName("localhost"), 1234), //
        resolve("[localhost]:1234", 80));
    assertEquals(new InetSocketAddress(getByName("localhost"), 80), //
        resolve("[localhost]", 80));
  }
}
