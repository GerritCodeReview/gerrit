// Copyright 2010 Google Inc. All Rights Reserved.

package com.google.gerrit.server.util;

/** Exception thrown by {@link FutureUtil#get(java.util.concurrent.Future)}. */
public class FutureException extends RuntimeException {
  FutureException(Throwable why) {
    super("Future computation failed", why);
  }
}
