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

package com.google.gerrit.server.rpc;

import com.google.gerrit.client.rpc.CorruptEntityException;
import com.google.gerrit.client.rpc.NoSuchEntityException;
import com.google.gerrit.server.BaseServiceImplementation;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtjsonrpc.client.VoidResult;
import com.google.gwtorm.client.OrmException;

import java.util.concurrent.Callable;

/**
 * Base class for RPC service implementations.
 * <p>
 * Typically an RPC service implementation will extend this class and use Guice
 * injection to manage its state. For example:
 *
 * <pre>
 *   class Foo extends Handler&lt;Result&gt; {
 *     interface Factory {
 *       Foo create(... args ...);
 *     }
 *     &#064;Inject
 *     Foo(state, @Assisted args) { ... }
 *     Result get() throws Exception { ... }
 *   }
 * </pre>
 *
 * @param <T> type of result for {@link AsyncCallback#onSuccess(Object)} if the
 *        operation completed successfully.
 */
public abstract class Handler<T> implements Callable<T> {
  /**
   * Run the operation and pass the result to the callback.
   *
   * @param callback callback to receive the result of {@link #call()}.
   */
  public final void to(final AsyncCallback<T> callback) {
    try {
      final T r = call();
      if (r != null) {
        callback.onSuccess(r);
      }
    } catch (NoSuchProjectException e) {
      callback.onFailure(new NoSuchEntityException());

    } catch (NoSuchChangeException e) {
      callback.onFailure(new NoSuchEntityException());

    } catch (OrmException e) {
      if (e.getCause() instanceof BaseServiceImplementation.Failure) {
        callback.onFailure(e.getCause().getCause());

      } else if (e.getCause() instanceof CorruptEntityException) {
        callback.onFailure(e.getCause());

      } else if (e.getCause() instanceof NoSuchEntityException) {
        callback.onFailure(e.getCause());

      } else {
        callback.onFailure(e);
      }
    } catch (BaseServiceImplementation.Failure e) {
      callback.onFailure(e.getCause());
    } catch (Exception e) {
      callback.onFailure(e);
    }
  }

  /**
   * Compute the operation result.
   *
   * @return the result of the operation. Return {@link VoidResult#INSTANCE} if
   *         there is no meaningful return value for the operation.
   * @throws Exception the operation failed. The caller will log the exception
   *         and the stack trace, if it is worth logging on the server side.
   */
  public abstract T call() throws Exception;
}
