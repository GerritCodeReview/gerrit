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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.cancellation.ClientDisconnectedRequestState;
import java.nio.ByteBuffer;
import java.util.concurrent.CancellationException;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.io.AbstractEndPoint;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpStream;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.Callback;

/**
 * Detects when the client of a GET or HEAD request closes the connection while the request is being
 * handled, and records it in a {@link ClientDisconnectedRequestState} request attribute so that the
 * request can be aborted at its next cancellation check.
 *
 * <p>Jetty doesn't read from an HTTP/1.1 connection while a request is being handled, so it doesn't
 * notice the disconnect on its own. This handler registers read interest on the connection for the
 * duration of the handling, so that the selector notifies us when the client sends a FIN or RST.
 * The interest is withdrawn before the response is sent, so that Jetty can resume reading the next
 * request on a persistent connection as usual.
 *
 * <p>Only requests without a body are watched, so that reading from the connection can't steal
 * request content. If the client already sent the next pipelined request, the byte that was read
 * can't be given back to Jetty, so the connection is closed after the response has been sent.
 */
class ClientDisconnectHandler extends Handler.Wrapper {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  ClientDisconnectHandler(Handler handler) {
    super(handler);
  }

  @Override
  public boolean handle(Request request, Response response, Callback callback) throws Exception {
    EndPoint endPoint = request.getConnectionMetaData().getConnection().getEndPoint();
    if (isWatchable(request) && endPoint instanceof AbstractEndPoint abstractEndPoint) {
      ClientDisconnectedRequestState state = new ClientDisconnectedRequestState();
      ReadInterest readInterest = new ReadInterest(abstractEndPoint, state);
      request.setAttribute(ClientDisconnectedRequestState.REQUEST_ATTRIBUTE, state);
      request.addHttpStreamWrapper(stream -> new DisarmingStream(stream, readInterest));
      readInterest.arm();
    }
    return super.handle(request, response, callback);
  }

  private static boolean isWatchable(Request request) {
    // With multiplexed protocols the end point is shared by all streams of the connection.
    HttpVersion version = request.getConnectionMetaData().getHttpVersion();
    return (version == HttpVersion.HTTP_1_1 || version == HttpVersion.HTTP_1_0)
        && (HttpMethod.GET.is(request.getMethod()) || HttpMethod.HEAD.is(request.getMethod()))
        && request.getLength() <= 0
        && !request.getHeaders().contains(HttpHeader.TRANSFER_ENCODING);
  }

  /** Read interest on the connection that is registered while the request is handled. */
  private static class ReadInterest implements Callback {
    private final AbstractEndPoint endPoint;
    private final ClientDisconnectedRequestState state;
    private boolean armed;
    private boolean consumedInput;

    ReadInterest(AbstractEndPoint endPoint, ClientDisconnectedRequestState state) {
      this.endPoint = endPoint;
      this.state = state;
    }

    synchronized void arm() {
      armed = endPoint.tryFillInterested(this);
    }

    synchronized void disarm() {
      if (armed) {
        armed = false;
        endPoint.getFillInterest().onFail(new CancellationException());
      }
    }

    synchronized boolean hasConsumedInput() {
      return consumedInput;
    }

    EndPoint getEndPoint() {
      return endPoint;
    }

    /** Called by the selector when the connection becomes readable. */
    @Override
    public synchronized void succeeded() {
      if (!armed) {
        return;
      }
      armed = false;
      try {
        int read = endPoint.fill(BufferUtil.allocate(1));
        if (read < 0) {
          state.onClientDisconnected();
        } else if (read > 0) {
          consumedInput = true;
        } else {
          arm();
        }
      } catch (Exception e) {
        logger.atFine().withCause(e).log("Failed to read from %s", endPoint);
        state.onClientDisconnected();
      }
    }

    @Override
    public void failed(Throwable t) {
      // Either we withdrew the interest in disarm(), or the connection failed, in which case the
      // response can't be sent anyway.
    }

    @Override
    public InvocationType getInvocationType() {
      return InvocationType.NON_BLOCKING;
    }
  }

  private static class DisarmingStream extends HttpStream.Wrapper {
    private final ReadInterest readInterest;

    DisarmingStream(HttpStream stream, ReadInterest readInterest) {
      super(stream);
      this.readInterest = readInterest;
    }

    @Override
    public void send(
        MetaData.Request request,
        MetaData.Response response,
        boolean last,
        ByteBuffer content,
        Callback callback) {
      // Must happen before any response bytes are written: once the client got the response it may
      // send the next request, which we must not read.
      readInterest.disarm();
      super.send(request, response, last, content, callback);
    }

    @Override
    public void succeeded() {
      super.succeeded();
      if (readInterest.hasConsumedInput()) {
        readInterest.getEndPoint().close();
      }
    }

    @Override
    public void failed(Throwable t) {
      // The stream may fail without a response being sent. Jetty must own the read interest
      // again before the stream completes, otherwise it aborts the connection.
      readInterest.disarm();
      super.failed(t);
    }
  }
}
