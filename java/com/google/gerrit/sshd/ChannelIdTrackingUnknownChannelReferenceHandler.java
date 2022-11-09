/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/**
 * This file is based on sshd-contrib Apache SSHD Mina project. Original commit:
 * https://github.com/apache/mina-sshd/commit/11b33dee37b5b9c71a40a8a98a42007e3687131e
 */
package com.google.gerrit.sshd;

import com.google.common.flogger.FluentLogger;
import java.io.IOException;
import org.apache.sshd.common.AttributeRepository.AttributeKey;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.ChannelListener;
import org.apache.sshd.common.channel.exception.SshChannelNotFoundException;
import org.apache.sshd.common.session.ConnectionService;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.helpers.DefaultUnknownChannelReferenceHandler;
import org.apache.sshd.common.util.buffer.Buffer;

/**
 * Makes sure that the referenced &quot;unknown&quot; channel identifier is one that was assigned in
 * the past. <B>Note:</B> it relies on the fact that the default {@code ConnectionService}
 * implementation assigns channels identifiers in ascending order.
 *
 * @author <a href="mailto:dev@mina.apache.org">Apache MINA SSHD Project</a>
 */
public class ChannelIdTrackingUnknownChannelReferenceHandler
    extends DefaultUnknownChannelReferenceHandler implements ChannelListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  public static final AttributeKey<Long> LAST_CHANNEL_ID_KEY = new AttributeKey<>();

  public static final ChannelIdTrackingUnknownChannelReferenceHandler TRACKER =
      new ChannelIdTrackingUnknownChannelReferenceHandler();

  public ChannelIdTrackingUnknownChannelReferenceHandler() {
    super();
  }

  @Override
  public void channelInitialized(Channel channel) {
    long channelId = channel.getChannelId();
    Session session = channel.getSession();
    Long lastTracked = session.setAttribute(LAST_CHANNEL_ID_KEY, channelId);
    logger.atFine().log(
        "channelInitialized(%s) updated last tracked channel ID %s => %s",
        channel, lastTracked, channelId);
  }

  @Override
  public Channel handleUnknownChannelCommand(
      ConnectionService service, byte cmd, long channelId, Buffer buffer) throws IOException {
    Session session = service.getSession();
    Long lastTracked = session.getAttribute(LAST_CHANNEL_ID_KEY);
    if ((lastTracked != null) && (channelId <= lastTracked.intValue())) {
      // Use TRACE level in order to avoid messages flooding
      logger.atFinest().log(
          "handleUnknownChannelCommand(%s) apply default handling for %s on channel=%s (lastTracked=%s)",
          session, SshConstants.getCommandMessageName(cmd), channelId, lastTracked);
      return super.handleUnknownChannelCommand(service, cmd, channelId, buffer);
    }

    throw new SshChannelNotFoundException(
        channelId,
        "Received "
            + SshConstants.getCommandMessageName(cmd)
            + " on unassigned channel "
            + channelId
            + " (last assigned="
            + lastTracked
            + ")");
  }
}
