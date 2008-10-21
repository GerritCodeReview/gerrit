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
import com.google.protobuf.RpcCallback;
import com.google.protobuf.RpcChannel;
import com.google.protobuf.RpcController;
import com.google.protobuf.Descriptors.MethodDescriptor;

import junit.framework.TestCase;

import java.util.LinkedList;

public class MockRpcChannel implements RpcChannel {
  private LinkedList<RpcChannel> calls = new LinkedList<RpcChannel>();

  public void add(final RpcChannel c) {
    calls.add(c);
  }

  public void callMethod(final MethodDescriptor method,
      final RpcController controller, final Message request,
      final Message responsePrototype, final RpcCallback<Message> done) {
    if (calls.isEmpty()) {
      TestCase.fail("Incorrect call for " + method.getFullName());
    }

    final RpcChannel c = calls.removeFirst();
    c.callMethod(method, controller, request, responsePrototype, done);
  }

  public void assertAllCallsMade() {
    TestCase.assertTrue(calls.isEmpty());
  }
}
