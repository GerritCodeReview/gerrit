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

package com.google.gerrit.test.util;

import com.google.common.base.Function;

import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.support.ui.FluentWait;
import org.slf4j.Logger;

import java.util.concurrent.TimeUnit;

public class WaitUtil {

  public static void wait(final Check check, final int timeout,
      final int interval, final Logger log, final String errorMessage) {
    Function<Object, Boolean> predicate = new Function<Object, Boolean>() {
      @Override
      public Boolean apply(final Object o) {
        return check.hasFinished();
      }
    };
    try {
      new FluentWait<Object>(new Object())
          .pollingEvery(interval, TimeUnit.MILLISECONDS)
          .withTimeout(timeout, TimeUnit.MILLISECONDS).until(predicate);
    } catch (TimeoutException e) {
      final StringBuilder b = new StringBuilder();
      b.append(errorMessage);
      b.append(" Timeout after ").append(timeout).append(" milliseconds.");
      final String exceptionMessage = b.toString();
      log.error(exceptionMessage, e);
      throw new RuntimeException(exceptionMessage, e);
    }
  }
}
