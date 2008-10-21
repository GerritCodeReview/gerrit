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

package com.google.codereview.manager.unpack;

import com.google.codereview.TrashTestCase;
import com.google.codereview.internal.NextReceivedBundle.NextReceivedBundleRequest;
import com.google.codereview.internal.NextReceivedBundle.NextReceivedBundleResponse;
import com.google.codereview.internal.UpdateReceivedBundle.UpdateReceivedBundleRequest;
import com.google.codereview.internal.UpdateReceivedBundle.UpdateReceivedBundleResponse;
import com.google.codereview.manager.Backend;
import com.google.codereview.manager.RepositoryCache;
import com.google.codereview.rpc.MockRpcChannel;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcChannel;
import com.google.protobuf.RpcController;
import com.google.protobuf.Descriptors.MethodDescriptor;

import org.spearce.jgit.lib.Repository;

import java.io.File;

public class ReceivedBundleUnpackerTest extends TrashTestCase {
  private MockRpcChannel rpc;
  private RepositoryCache repoCache;
  private Backend server;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    rpc = new MockRpcChannel();
    repoCache = new RepositoryCache(tempRoot);
    server = new Backend(repoCache, rpc, null, null);
    new Repository(new File(tempRoot, "foo.git")).create();
  }

  public void testNextReceivedBundle_EmptyQueue() throws Exception {
    final ReceivedBundleUnpacker rbu = newRBU();
    rpc.add(new RpcChannel() {
      public void callMethod(final MethodDescriptor method,
          final RpcController controller, final Message request,
          final Message responsePrototype, final RpcCallback<Message> done) {
        assertEquals("NextReceivedBundle", method.getName());
        assertSame(NextReceivedBundleRequest.getDefaultInstance(), request);

        final NextReceivedBundleResponse.Builder r =
            NextReceivedBundleResponse.newBuilder();
        r.setStatusCode(NextReceivedBundleResponse.CodeType.QUEUE_EMPTY);
        done.run(r.build());
      }
    });
    rbu.run();
    rpc.assertAllCallsMade();
  }

  public void testNextReceivedBundle_GetBundle() throws Exception {
    final String bundleKey = "bundle-key-abcd123efg";
    final NextReceivedBundleResponse nrb[] = new NextReceivedBundleResponse[1];
    final UpdateReceivedBundleRequest urbr[] =
        new UpdateReceivedBundleRequest[1];

    final ReceivedBundleUnpacker rbu =
        new ReceivedBundleUnpacker(server) {
          @Override
          protected UpdateReceivedBundleRequest unpackImpl(
              final NextReceivedBundleResponse rsp) {
            assertNotNull("unpackImpl only after bundle available", nrb[0]);
            assertSame(nrb[0], rsp);

            final UpdateReceivedBundleRequest.Builder r =
                UpdateReceivedBundleRequest.newBuilder();
            r.setBundleKey(bundleKey);
            r.setStatusCode(UpdateReceivedBundleRequest.CodeType.UNPACKED_OK);
            urbr[0] = r.build();
            return urbr[0];
          }
        };

    rpc.add(new RpcChannel() {
      public void callMethod(final MethodDescriptor method,
          final RpcController controller, final Message request,
          final Message responsePrototype, final RpcCallback<Message> done) {
        assertEquals("NextReceivedBundle", method.getName());
        assertSame(NextReceivedBundleRequest.getDefaultInstance(), request);

        final NextReceivedBundleResponse.Builder r =
            NextReceivedBundleResponse.newBuilder();
        r.setStatusCode(NextReceivedBundleResponse.CodeType.BUNDLE_AVAILABLE);
        r.setBundleKey(bundleKey);
        r.setBundleData(ByteString.EMPTY);
        r.setDestProject("foo.git");
        r.setDestProjectKey("project:foo.git");
        r.setDestBranchKey("branch:refs/heads/master");
        r.setOwner("author@example.com");
        nrb[0] = r.build();
        done.run(nrb[0]);
      }
    });

    rpc.add(new RpcChannel() {
      public void callMethod(final MethodDescriptor method,
          final RpcController controller, final Message request,
          final Message responsePrototype, final RpcCallback<Message> done) {
        assertEquals("UpdateReceivedBundle", method.getName());
        assertSame(urbr[0], request);

        final UpdateReceivedBundleResponse.Builder r =
            UpdateReceivedBundleResponse.newBuilder();
        r.setStatusCode(UpdateReceivedBundleResponse.CodeType.UPDATED);
        done.run(r.build());
      }
    });

    rpc.add(new RpcChannel() {
      public void callMethod(final MethodDescriptor method,
          final RpcController controller, final Message request,
          final Message responsePrototype, final RpcCallback<Message> done) {
        assertEquals("NextReceivedBundle", method.getName());
        assertSame(NextReceivedBundleRequest.getDefaultInstance(), request);

        final NextReceivedBundleResponse.Builder r =
            NextReceivedBundleResponse.newBuilder();
        r.setStatusCode(NextReceivedBundleResponse.CodeType.QUEUE_EMPTY);
        done.run(r.build());
      }
    });

    rbu.run();
    assertNotNull(nrb[0]);
    assertNotNull(urbr[0]);
    rpc.assertAllCallsMade();
  }

  public void testNextReceivedBundle_RpcFailure() throws Exception {
    final ReceivedBundleUnpacker rbu = newRBU();
    rpc.add(new RpcChannel() {
      public void callMethod(final MethodDescriptor method,
          final RpcController controller, final Message request,
          final Message responsePrototype, final RpcCallback<Message> done) {
        assertEquals("NextReceivedBundle", method.getName());
        controller.setFailed("mock failure");
      }
    });
    rbu.run();
    rpc.assertAllCallsMade();
  }

  public void testNextReceivedBundle_RuntimeException() throws Exception {
    final String msg = "test-a-message-win-a-prize";
    final ReceivedBundleUnpacker rbu = newRBU();
    rpc.add(new RpcChannel() {
      public void callMethod(final MethodDescriptor method,
          final RpcController controller, final Message request,
          final Message responsePrototype, final RpcCallback<Message> done) {
        assertEquals("NextReceivedBundle", method.getName());
        throw new RuntimeException(msg);
      }
    });
    try {
      rbu.run();
      fail("Unpacker did not rethrow an unexpected runtime exception");
    } catch (RuntimeException re) {
      assertEquals(msg, re.getMessage());
    }
    rpc.assertAllCallsMade();
  }

  public void testNextReceivedBundle_RuntimeError() throws Exception {
    final String msg = "test-a-message-win-a-prize";
    final ReceivedBundleUnpacker rbu = newRBU();
    rpc.add(new RpcChannel() {
      public void callMethod(final MethodDescriptor method,
          final RpcController controller, final Message request,
          final Message responsePrototype, final RpcCallback<Message> done) {
        assertEquals("NextReceivedBundle", method.getName());
        throw new OutOfMemoryError(msg);
      }
    });
    try {
      rbu.run();
      fail("Unpacker did not rethrow an unexpected OutOfMemoryError");
    } catch (OutOfMemoryError re) {
      assertEquals(msg, re.getMessage());
    }
    rpc.assertAllCallsMade();
  }

  private ReceivedBundleUnpacker newRBU() {
    return new ReceivedBundleUnpacker(server);
  }
}
