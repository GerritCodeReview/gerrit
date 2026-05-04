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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.server.cancellation.RequestStateProvider;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.http.HttpServletRequest;
import org.eclipse.jetty.io.ChannelEndPoint;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.HttpChannel;
import org.junit.Test;

public class RestApiServletClientDisconnectTest {
  @Test
  public void doesNotCancelWhenRequestIsStillConnected() {
    HttpServletRequest req = mock(HttpServletRequest.class);

    RequestStateProvider provider = RestApiServlet.createClientClosedRequestStateProvider(req);

    AtomicReference<RequestStateProvider.Reason> reason = new AtomicReference<>();
    provider.checkIfCancelled((r, m) -> reason.set(r));
    assertThat(reason.get()).isNull();
  }

  @Test
  public void cancelsWhenEndpointIsClosed() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpChannel channel = mock(HttpChannel.class);
    EndPoint endPoint = mock(EndPoint.class);
    when(req.getAttribute(HttpChannel.class.getName())).thenReturn(channel);
    when(channel.getEndPoint()).thenReturn(endPoint);
    when(endPoint.isOpen()).thenReturn(false);

    RequestStateProvider provider = RestApiServlet.createClientClosedRequestStateProvider(req);

    AtomicReference<RequestStateProvider.Reason> reason = new AtomicReference<>();
    provider.checkIfCancelled((r, m) -> reason.set(r));
    assertThat(reason.get()).isEqualTo(RequestStateProvider.Reason.CLIENT_CLOSED_REQUEST);
  }

  @Test
  public void doesNotCancelWhenEndpointIsOpenAndNotChannelEndPoint() {
    HttpServletRequest req = mock(HttpServletRequest.class);
    HttpChannel channel = mock(HttpChannel.class);
    EndPoint endPoint = mock(EndPoint.class);
    when(req.getAttribute(HttpChannel.class.getName())).thenReturn(channel);
    when(channel.getEndPoint()).thenReturn(endPoint);
    when(endPoint.isOpen()).thenReturn(true);

    RequestStateProvider provider = RestApiServlet.createClientClosedRequestStateProvider(req);

    AtomicReference<RequestStateProvider.Reason> reason = new AtomicReference<>();
    provider.checkIfCancelled((r, m) -> reason.set(r));
    assertThat(reason.get()).isNull();
  }

  @Test
  public void cancelsWhenSocketChannelReturnsEof() throws IOException {
    try (ServerSocketChannel server = ServerSocketChannel.open()) {
      server.bind(new InetSocketAddress("localhost", 0));
      try (SocketChannel client = SocketChannel.open(server.socket().getLocalSocketAddress())) {
        client.configureBlocking(false);
        try (SocketChannel serverSide = server.accept()) {
          serverSide.configureBlocking(false);
          client.close();

          HttpChannel channel = mock(HttpChannel.class);
          ChannelEndPoint channelEndPoint = mock(ChannelEndPoint.class);
          when(channel.getEndPoint()).thenReturn(channelEndPoint);
          when(channelEndPoint.isOpen()).thenReturn(true);
          when(channelEndPoint.getChannel()).thenReturn(serverSide);

          assertThat(RestApiServlet.isClientDisconnected(channel)).isTrue();
        }
      }
    }
  }

  @Test
  public void doesNotCancelWhenSocketChannelReturnsNoData() throws IOException {
    try (ServerSocketChannel server = ServerSocketChannel.open()) {
      server.bind(new InetSocketAddress("localhost", 0));
      try (SocketChannel client = SocketChannel.open(server.socket().getLocalSocketAddress())) {
        client.configureBlocking(false);
        try (SocketChannel serverSide = server.accept()) {
          serverSide.configureBlocking(false);

          HttpChannel channel = mock(HttpChannel.class);
          ChannelEndPoint channelEndPoint = mock(ChannelEndPoint.class);
          when(channel.getEndPoint()).thenReturn(channelEndPoint);
          when(channelEndPoint.isOpen()).thenReturn(true);
          when(channelEndPoint.getChannel()).thenReturn(serverSide);

          assertThat(RestApiServlet.isClientDisconnected(channel)).isFalse();
        }
      }
    }
  }

  @Test
  public void throttlesActiveProbe() throws IOException {
    try (ServerSocketChannel server = ServerSocketChannel.open()) {
      server.bind(new InetSocketAddress("localhost", 0));
      try (SocketChannel client = SocketChannel.open(server.socket().getLocalSocketAddress())) {
        client.configureBlocking(false);
        try (SocketChannel serverSide = server.accept()) {
          serverSide.configureBlocking(false);

          HttpServletRequest req = mock(HttpServletRequest.class);
          HttpChannel channel = mock(HttpChannel.class);
          ChannelEndPoint channelEndPoint = mock(ChannelEndPoint.class);
          when(req.getAttribute(HttpChannel.class.getName())).thenReturn(channel);
          when(channel.getEndPoint()).thenReturn(channelEndPoint);
          when(channelEndPoint.isOpen()).thenReturn(true);
          when(channelEndPoint.getChannel()).thenReturn(serverSide);

          RequestStateProvider provider =
              RestApiServlet.createClientClosedRequestStateProvider(req);

          AtomicReference<RequestStateProvider.Reason> reason = new AtomicReference<>();

          // First check: live connection, no cancel.
          provider.checkIfCancelled((r, m) -> reason.set(r));
          assertThat(reason.get()).isNull();

          // Client disconnects, but within the throttle window: cached result (not disconnected).
          client.close();
          provider.checkIfCancelled((r, m) -> reason.set(r));
          assertThat(reason.get()).isNull();
        }
      }
    }
  }

  @Test
  public void cancelsWhenCurrentThreadIsInterrupted() {
    HttpServletRequest req = mock(HttpServletRequest.class);

    RequestStateProvider provider = RestApiServlet.createClientClosedRequestStateProvider(req);

    AtomicReference<RequestStateProvider.Reason> reason = new AtomicReference<>();
    try {
      Thread.currentThread().interrupt();
      provider.checkIfCancelled((r, m) -> reason.set(r));
    } finally {
      Thread.interrupted(); // clear interrupt flag
    }
    assertThat(reason.get()).isEqualTo(RequestStateProvider.Reason.CLIENT_CLOSED_REQUEST);
  }
}
