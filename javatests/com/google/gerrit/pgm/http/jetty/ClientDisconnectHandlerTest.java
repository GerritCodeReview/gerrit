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

package com.google.gerrit.pgm.http.jetty;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.base.Ascii;
import com.google.common.base.Splitter;
import com.google.gerrit.server.cancellation.ClientDisconnectedRequestState;
import com.google.gerrit.server.cancellation.RequestStateProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.SocketChannelEndPoint;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.Callback;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ClientDisconnectHandlerTest {
  private static final String WAIT_FOR_DISCONNECT_PATH = "/wait-for-disconnect";
  private static final String WAIT_PATH = "/wait";
  private static final String LINGER_PATH = "/linger";
  private static final long TIMEOUT_SECONDS = 30;

  /** Placeholder in {@link #handledStates} for requests without state attribute. */
  private static final String UNWATCHED = "unwatched";

  /** For each handled request, its state attribute, or {@link #UNWATCHED}. */
  private final LinkedBlockingQueue<Object> handledStates = new LinkedBlockingQueue<>();

  private final CountDownLatch requestStarted = new CountDownLatch(1);
  private final CountDownLatch nextRequestSent = new CountDownLatch(1);

  private Server server;
  private int port;

  @Before
  public void setUp() throws Exception {
    server = new Server();
    ServerConnector connector = new ServerConnector(server);
    connector.setPort(0);
    server.addConnector(connector);
    server.setHandler(new ClientDisconnectHandler(new TestHandler()));
    server.start();
    port = connector.getLocalPort();
  }

  @After
  public void tearDown() throws Exception {
    server.stop();
  }

  @Test
  public void disconnectIsDetectedWhileHandlingGet() throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      send(socket, "GET", WAIT_FOR_DISCONNECT_PATH);
      assertThat(requestStarted.await(TIMEOUT_SECONDS, SECONDS)).isTrue();
    }
    assertThat(isCancelled(nextState())).isTrue();
  }

  @Test
  public void persistentConnectionCanBeReused() throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      for (int i = 0; i < 3; i++) {
        send(socket, "GET", "/request" + i);
        assertThat(readResponse(socket)).isEqualTo("/request" + i);
        assertThat(isCancelled(nextState())).isFalse();
      }
    }
  }

  /**
   * The client may send its next request as soon as it got the response, even if the handler is
   * still running. That request must be left for Jetty to read.
   */
  @Test
  public void nextRequestSentBeforeHandlerFinishesIsServed() throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      send(socket, "GET", LINGER_PATH);
      assertThat(readResponse(socket)).isEqualTo(LINGER_PATH);
      send(socket, "GET", "/next");
      nextRequestSent.countDown();
      assertThat(readResponse(socket)).isEqualTo("/next");
    }
  }

  @Test
  public void pipelinedRequestClosesConnectionAfterResponse() throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      send(socket, "GET", WAIT_PATH);
      assertThat(requestStarted.await(TIMEOUT_SECONDS, SECONDS)).isTrue();
      send(socket, "GET", "/pipelined");
      assertThat(readResponse(socket)).isEqualTo(WAIT_PATH);
      assertThat(isCancelled(nextState())).isFalse();
      assertThat(socket.getInputStream().read()).isEqualTo(-1);
    }
  }

  @Test
  public void requestWithBodyIsNotWatched() throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      OutputStream out = socket.getOutputStream();
      out.write(
          "GET /get-with-body HTTP/1.1\r\nHost: localhost\r\nContent-Length: 2\r\n\r\n{}"
              .getBytes(UTF_8));
      out.flush();
      assertThat(readResponse(socket)).isEqualTo("/get-with-body");
    }
    assertThat(handledStates.poll(TIMEOUT_SECONDS, SECONDS)).isEqualTo(UNWATCHED);
  }

  @Test
  public void postIsNotWatched() throws Exception {
    try (Socket socket = new Socket("localhost", port)) {
      send(socket, "POST", "/post");
      assertThat(readResponse(socket)).isEqualTo("/post");
    }
    assertThat(handledStates.poll(TIMEOUT_SECONDS, SECONDS)).isEqualTo(UNWATCHED);
  }

  private ClientDisconnectedRequestState nextState() throws InterruptedException {
    Object state = handledStates.poll(TIMEOUT_SECONDS, SECONDS);
    assertThat(state).isInstanceOf(ClientDisconnectedRequestState.class);
    return (ClientDisconnectedRequestState) state;
  }

  private static boolean isCancelled(ClientDisconnectedRequestState state) {
    AtomicReference<RequestStateProvider.Reason> reason = new AtomicReference<>();
    state.checkIfCancelled((r, message) -> reason.set(r));
    return reason.get() == RequestStateProvider.Reason.CLIENT_CLOSED_REQUEST;
  }

  /** Bytes received by the server socket of {@code endPoint} that nobody has read yet. */
  private static int bytesReceived(EndPoint endPoint) {
    try {
      return ((SocketChannelEndPoint) endPoint).getChannel().socket().getInputStream().available();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Waits until {@code condition} holds or the timeout expires. Polls because the condition is
   * changed by a Jetty I/O thread that the test can't synchronize with.
   */
  private static boolean await(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + SECONDS.toNanos(TIMEOUT_SECONDS);
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        return false;
      }
      Thread.sleep(10);
    }
    return true;
  }

  private static void send(Socket socket, String method, String path) throws IOException {
    OutputStream out = socket.getOutputStream();
    out.write((method + " " + path + " HTTP/1.1\r\nHost: localhost\r\n\r\n").getBytes(UTF_8));
    out.flush();
  }

  /** Reads one response and returns its body, which is the path of the request. */
  private static String readResponse(Socket socket) throws IOException {
    socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS));
    InputStream in = socket.getInputStream();
    ByteArrayOutputStream head = new ByteArrayOutputStream();
    while (!head.toString(UTF_8).endsWith("\r\n\r\n")) {
      int b = in.read();
      assertThat(b).isNotEqualTo(-1);
      head.write(b);
    }
    int contentLength = 0;
    for (String line : Splitter.on("\r\n").split(head.toString(UTF_8))) {
      if (Ascii.toLowerCase(line).startsWith("content-length:")) {
        contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
      }
    }
    return new String(in.readNBytes(contentLength), UTF_8);
  }

  private class TestHandler extends Handler.Abstract {
    @Override
    public boolean handle(Request request, Response response, Callback callback) throws Exception {
      Object state = request.getAttribute(ClientDisconnectedRequestState.REQUEST_ATTRIBUTE);
      String path = request.getHttpURI().getPath();
      if (path.equals(WAIT_FOR_DISCONNECT_PATH)) {
        requestStarted.countDown();
        assertThat(await(() -> isCancelled((ClientDisconnectedRequestState) state))).isTrue();
      } else if (path.equals(WAIT_PATH)) {
        requestStarted.countDown();
        // The read interest is gone once the selector has read the first byte of the pipelined
        // request.
        EndPoint endPoint = request.getConnectionMetaData().getConnection().getEndPoint();
        assertThat(await(() -> !endPoint.isFillInterested())).isTrue();
      }
      if (request.getLength() > 0) {
        Request.asInputStream(request).readAllBytes();
      }
      byte[] body = path.getBytes(UTF_8);
      response.getHeaders().put("Content-Length", body.length);
      if (path.startsWith(LINGER_PATH)) {
        // Send the complete response, but keep handling so that the client can send the next
        // request before the stream completes.
        Callback.Completable written = new Callback.Completable();
        response.write(false, ByteBuffer.wrap(body), written);
        written.get();
        // The client sends the next request once it got the response. Keep handling until the
        // request has arrived.
        assertThat(nextRequestSent.await(TIMEOUT_SECONDS, SECONDS)).isTrue();
        // Wait until the next request reached the server. A read interest that is still
        // registered would consume its first byte as soon as it arrives.
        EndPoint endPoint = request.getConnectionMetaData().getConnection().getEndPoint();
        assertThat(await(() -> bytesReceived(endPoint) > 0 || !endPoint.isFillInterested()))
            .isTrue();
        handledStates.add(state);
        response.write(true, null, callback);
        return true;
      }
      handledStates.add(state != null ? state : UNWATCHED);
      response.write(true, ByteBuffer.wrap(body), callback);
      return true;
    }
  }
}
