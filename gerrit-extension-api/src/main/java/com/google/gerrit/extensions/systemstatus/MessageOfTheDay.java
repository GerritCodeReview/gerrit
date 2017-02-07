// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.extensions.systemstatus;

import com.google.gerrit.extensions.annotations.ExtensionPoint;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Supplies a message of the day when the page is first loaded.
 *
 * <pre>
 * DynamicSet.bind(binder(), MessageOfTheDay.class).to(MyMessage.class);
 * </pre>
 */
@ExtensionPoint
public abstract class MessageOfTheDay {
  /**
   * Retrieve the message of the day as an HTML fragment.
   *
   * @return message as an HTML fragment; null if no message is available.
   */
  public abstract String getHtmlMessage();

  /**
   * Unique identifier for this message.
   *
   * <p>Messages with the same identifier will be hidden from the user until redisplay has occurred.
   *
   * @return unique message identifier. This identifier should be unique within the server.
   */
  public abstract String getMessageId();

  /**
   * When should the message be displayed?
   *
   * <p>Default implementation returns {@code tomorrow at 00:00:00 GMT}.
   *
   * @return a future date after which the message should be redisplayed.
   */
  public Date getRedisplay() {
    Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
    cal.set(Calendar.HOUR_OF_DAY, 0);
    cal.set(Calendar.MINUTE, 0);
    cal.set(Calendar.SECOND, 0);
    cal.set(Calendar.MILLISECOND, 0);
    cal.add(Calendar.DAY_OF_MONTH, 1);
    return cal.getTime();
  }
}
