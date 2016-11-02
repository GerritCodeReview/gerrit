// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server.mail.receive;

import com.google.common.io.CharStreams;
import com.google.gerrit.server.mail.Address;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.MessageBuilder;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.joda.time.DateTime;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * RawMailParser parses raw email content received through POP3 or IMAP into
 * an internal {@link MailMessage}.
 */
public class RawMailParser {

  /**
   * Parses a MailMessage from a string.
   * @param raw String as received over the wire
   * @return Parsed MailMessage
   * @throws MailParsingException
   */
  public static MailMessage parse(String raw) throws MailParsingException {
    MailMessage.Builder messageBuilder = MailMessage.builder();
    Message mimeMessage;
    try {
      MessageBuilder builder = new DefaultMessageBuilder();
      mimeMessage =
          builder.parseMessage(new ByteArrayInputStream(raw.getBytes()));
    } catch (IOException | MimeException e) {
      throw new MailParsingException("Can't parse email", e);
    }
    // Add general headers
    messageBuilder.id(mimeMessage.getMessageId());
    messageBuilder.subject(mimeMessage.getSubject());
    messageBuilder.dateReceived(new DateTime(mimeMessage.getDate()));

    // Add From, To and Cc
    if (mimeMessage.getFrom() != null && mimeMessage.getFrom().size() > 0) {
      Mailbox from = mimeMessage.getFrom().get(0);
      messageBuilder.from(new Address(from.getName(), from.getAddress()));
    }
    if (mimeMessage.getTo() != null) {
      for (Mailbox m : mimeMessage.getTo().flatten()) {
        messageBuilder.addTo(new Address(m.getName(), m.getAddress()));
      }
    }
    if (mimeMessage.getCc() != null) {
      for (Mailbox m : mimeMessage.getCc().flatten()) {
        messageBuilder.addCc(new Address(m.getName(), m.getAddress()));
      }
    }

    // Add additional headers
    mimeMessage.getHeader().getFields().stream()
        .filter(f -> !"to from cc date message-id subject content-type"
            .contains(f.getName().toLowerCase()))
        .forEach(f -> messageBuilder.addAdditionalHeader(
            f.getName() + ": " + f.getBody()));

    // Add text and html body parts
    StringBuilder textBuilder = new StringBuilder();
    StringBuilder htmlBuilder = new StringBuilder();
    try {
      handleMimePart(mimeMessage, textBuilder, htmlBuilder);
    } catch (IOException e) {
      throw new MailParsingException("Can't parse email", e);
    }
    messageBuilder.textContent(
        textBuilder.length() > 0 ? textBuilder.toString() : null);
    messageBuilder.htmlContent(
        htmlBuilder.length() > 0 ? htmlBuilder.toString() : null);

    return messageBuilder.build();
  }

  /**
   * Parses a MailMessage from an array of characters. Note that the character
   * array is int-typed. This method is only used by POP3, which specifies that
   * all transferred characters are US-ASCII (RFC 6856). When reading the input
   * in Java, io.Reader yields ints. These can be safely converted to chars
   * as all US-ASCII characters fit in a char. If emails contain non-ASCII
   * characters, such as UTF runes, these will be encoded in ASCII using either
   * Base64 or quoted-printable encoding.
   * @param chars Array as received over the wire
   * @return Parsed MailMessage
   * @throws MailParsingException
   */
  public static MailMessage parse(int[] chars) throws MailParsingException {
    StringBuilder b = new StringBuilder(chars.length);
    for (int c : chars) {
      b.append((char) c);
    }
    return parse(b.toString());
  }

  /**
   * Traverses a mime tree and parses out text and html parts. All other parts
   * will be dropped.
   * @param part MimePart to parse
   * @param textBuilder StringBuilder to append all plaintext parts
   * @param htmlBuilder StringBuilder to append all html parts
   * @throws IOException
   */
  private static void handleMimePart(Entity part, StringBuilder textBuilder,
      StringBuilder htmlBuilder) throws IOException {
    if (part.getMimeType().startsWith("text/") &&
        (part.getDispositionType() == null ||
            !part.getDispositionType().equals("attachment"))) {
      TextBody tb = (TextBody) part.getBody();
      String result = CharStreams.toString(new InputStreamReader(
          tb.getInputStream(), tb.getMimeCharset()));
      if (part.getMimeType().equals("text/plain")) {
        textBuilder.append(result);
      } else if (part.getMimeType().equals("text/html")) {
        htmlBuilder.append(result);
      }
    } else if (part.getMimeType().equals("multipart/alternative") ||
        part.getMimeType().equals("multipart/mixed")) {
      Multipart multipart = (Multipart) part.getBody();
      for (Entity e : multipart.getBodyParts()) {
        handleMimePart(e, textBuilder, htmlBuilder);
      }
    }
  }
}
