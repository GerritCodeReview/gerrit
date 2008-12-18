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

package com.google.gerrit.client.rpc;

import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;

/** Support for services which require a {@link ReviewDb} instance. */
public class BaseServiceImplementation {
  protected final SchemaFactory<ReviewDb> schema;

  protected BaseServiceImplementation(final SchemaFactory<ReviewDb> rdf) {
    schema = rdf;
  }

  /**
   * Executes <code>action.run</code> with an active ReviewDb connection.
   * <p>
   * A database handle is automatically opened and closed around the action's
   * {@link Action#run(ReviewDb)} method. OrmExceptions are caught and passed
   * into the onFailure method of the callback.
   * 
   * @param <T> type of result the callback expects.
   * @param callback the callback that will receive the result.
   * @param action the action logic to perform.
   */
  protected <T> void run(final AsyncCallback<T> callback, final Action<T> action) {
    try {
      final ReviewDb db = schema.open();
      final T r;
      try {
        r = action.run(db);
      } finally {
        db.close();
      }
      if (r != null) {
        callback.onSuccess(r);
      }
    } catch (OrmException e) {
      callback.onFailure(e);
    } catch (Failure e) {
      callback.onFailure(e.getCause());
    }
  }

  /** Exception whose cause is passed into onFailure. */
  public static class Failure extends Exception {
    public Failure(final Throwable why) {
      super(why);
    }
  }

  /** Arbitrary action to run with a database connection. */
  public static interface Action<T> {
    /**
     * Perform this action, returning the onSuccess value.
     * 
     * @param db an open database handle to be used by this connection.
     * @return he value to pass to {@link AsyncCallback#onSuccess(Object)}.
     * @throws OrmException any schema based action failed.
     * @throws Failure cause is given to
     *         {@link AsyncCallback#onFailure(Throwable)}.
     */
    T run(ReviewDb db) throws OrmException, Failure;
  }
}
