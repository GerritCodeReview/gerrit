// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.testing;

import static java.util.stream.Collectors.toList;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.mail.Address;
import com.google.gerrit.mail.EmailHeader;
import com.google.gerrit.mail.MailHeader;
import com.google.gerrit.server.git.WorkQueue;
import com.google.gerrit.server.mail.send.EmailSender;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Email sender implementation that records messages in memory.
 *
 * <p>This class is mostly threadsafe. The only exception is that not all {@link EmailHeader}
 * subclasses are immutable. In particular, if a caller holds a reference to an {@code AddressList}
 * and mutates it after sending, the message returned by {@link #getMessages()} may or may not
 * reflect mutations.
 */
@Singleton
public class FakeEmailSender implements EmailSender {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Module extends AbstractModule {
    @Override
    public void configure() {
      bind(EmailSender.class).to(FakeEmailSender.class);
    }
  }

  @AutoValue
  public abstract static class Message {
    private static Message create(
        Address from,
        Collection<Address> rcpt,
        Map<String, EmailHeader> headers,
        String body,
        String htmlBody) {
      return new AutoValue_FakeEmailSender_Message(
          from, ImmutableList.copyOf(rcpt), ImmutableMap.copyOf(headers), body, htmlBody);
    }

    public abstract Address from();

    public abstract ImmutableList<Address> rcpt();

    public abstract ImmutableMap<String, EmailHeader> headers();

    public abstract String body();

    @Nullable
    public abstract String htmlBody();
  }

  private final WorkQueue workQueue;
  private final List<Message> messages;
  private int messagesRead;

  @Inject
  FakeEmailSender(WorkQueue workQueue) {
    this.workQueue = workQueue;
    messages = Collections.synchronizedList(new ArrayList<>());
    messagesRead = 0;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public boolean canEmail(String address) {
    return true;
  }

  @Override
  public void send(
      Address from, Collection<Address> rcpt, Map<String, EmailHeader> headers, String body)
      throws EmailException {
    send(from, rcpt, headers, body, null);
  }

  @Override
  public void send(
      Address from,
      Collection<Address> rcpt,
      Map<String, EmailHeader> headers,
      String body,
      String htmlBody)
      throws EmailException {
    messages.add(Message.create(from, rcpt, headers, body, htmlBody));
  }

  public void clear() {
    waitForEmails();
    synchronized (messages) {
      messages.clear();
      messagesRead = 0;
    }
  }

  public synchronized @Nullable Message peekMessage() {
    if (messagesRead >= messages.size()) {
      return null;
    }
    return messages.get(messagesRead);
  }

  public synchronized @Nullable Message nextMessage() {
    Message msg = peekMessage();
    messagesRead++;
    return msg;
  }

  public ImmutableList<Message> getMessages() {
    waitForEmails();
    synchronized (messages) {
      return ImmutableList.copyOf(messages);
    }
  }

  public List<Message> getMessages(String changeId, String type) {
    final String idFooter = "\n" + MailHeader.CHANGE_ID.withDelimiter() + changeId + "\n";
    final String typeFooter = "\n" + MailHeader.MESSAGE_TYPE.withDelimiter() + type + "\n";
    return getMessages().stream()
        .filter(in -> in.body().contains(idFooter) && in.body().contains(typeFooter))
        .collect(toList());
  }

  private void waitForEmails() {
    // TODO(dborowitz): This is brittle; consider forcing emails to use
    // a single thread in tests (tricky because most callers just use the
    // default executor).
    for (WorkQueue.Task<?> task : workQueue.getTasks()) {
      if (task.toString().contains("send-email")) {
        try {
          task.get();
        } catch (ExecutionException | InterruptedException e) {
          logger.atWarning().withCause(e).log("error finishing email task");
        }
      }
    }
  }
}
