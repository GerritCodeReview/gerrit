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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharStreams;
import com.google.gerrit.server.mail.Address;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.MessageBuilder;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.joda.time.DateTime;

/** Parses raw email content received through POP3 or IMAP into an internal {@link MailMessage}. */
public class RawMailParser {
  private static final ImmutableSet<String> MAIN_HEADERS =
      ImmutableSet.of("to", "from", "cc", "date", "message-id", "subject", "content-type");

  private RawMailParser() {}

  /**
   * Parses a MailMessage from a string.
   *
   * @param raw {@link String} payload as received over the wire
   * @return Parsed {@link MailMessage}
   * @throws MailParsingException in case parsing fails
   */
  public static MailMessage parse(String raw) throws MailParsingException {
    MailMessage.Builder messageBuilder = MailMessage.builder();
    Message mimeMessage;
    try {
      MessageBuilder builder = new DefaultMessageBuilder();
      mimeMessage = builder.parseMessage(new ByteArrayInputStream(raw.getBytes()));
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
    mimeMessage
        .getHeader()
        .getFields()
        .stream()
        .filter(f -> !MAIN_HEADERS.contains(f.getName().toLowerCase()))
        .forEach(f -> messageBuilder.addAdditionalHeader(f.getName() + ": " + f.getBody()));

    // Add text and html body parts
    StringBuilder textBuilder = new StringBuilder();
    StringBuilder htmlBuilder = new StringBuilder();
    try {
      handleMimePart(mimeMessage, textBuilder, htmlBuilder);
    } catch (IOException e) {
      throw new MailParsingException("Can't parse email", e);
    }
    messageBuilder.textContent(Strings.emptyToNull(textBuilder.toString()));
    messageBuilder.htmlContent(Strings.emptyToNull(htmlBuilder.toString()));

    try {
      // build() will only succeed if all required attributes were set. We wrap
      // the IllegalStateException in a MailParsingException indicating that
      // required attributes are missing, so that the caller doesn't fall over.
      return messageBuilder.build();
    } catch (IllegalStateException e) {
      throw new MailParsingException("Missing required attributes after email was parsed", e);
    }
  }

  /**
   * Parses a MailMessage from an array of characters. Note that the character array is int-typed.
   * This method is only used by POP3, which specifies that all transferred characters are US-ASCII
   * (RFC 6856). When reading the input in Java, io.Reader yields ints. These can be safely
   * converted to chars as all US-ASCII characters fit in a char. If emails contain non-ASCII
   * characters, such as UTF runes, these will be encoded in ASCII using either Base64 or
   * quoted-printable encoding.
   *
   * @param chars Array as received over the wire
   * @return Parsed {@link MailMessage}
   * @throws MailParsingException in case parsing fails
   */
  public static MailMessage parse(int[] chars) throws MailParsingException {
    StringBuilder b = new StringBuilder(chars.length);
    for (int c : chars) {
      b.append((char) c);
    }
    return parse(b.toString());
  }

  /**
   * Traverses a mime tree and parses out text and html parts. All other parts will be dropped.
   *
   * @param part {@code MimePart} to parse
   * @param textBuilder {@link StringBuilder) to append all plaintext parts
   * @param htmlBuilder {@link StringBuilder) to append all html parts
   * @throws IOException in case if a failure while transforming the input to a {@link String}
   */
  private static void handleMimePart(
      Entity part, StringBuilder textBuilder, StringBuilder htmlBuilder) throws IOException {
    if (isPlainOrHtml(part.getMimeType()) && !isAttachment(part.getDispositionType())) {
      TextBody tb = (TextBody) part.getBody();
      String result =
          CharStreams.toString(new InputStreamReader(tb.getInputStream(), tb.getMimeCharset()));
      if (part.getMimeType().equals("text/plain")) {
        textBuilder.append(result);
      } else if (part.getMimeType().equals("text/html")) {
        htmlBuilder.append(result);
      }
    } else if (isMixedOrAlternative(part.getMimeType())) {
      Multipart multipart = (Multipart) part.getBody();
      for (Entity e : multipart.getBodyParts()) {
        handleMimePart(e, textBuilder, htmlBuilder);
      }
    }
  }

  private static boolean isPlainOrHtml(String mimeType) {
    return (mimeType.equals("text/plain") || mimeType.equals("text/html"));
  }

  private static boolean isMixedOrAlternative(String mimeType) {
    return mimeType.equals("multipart/alternative") || mimeType.equals("multipart/mixed");
  }

  private static boolean isAttachment(String dispositionType) {
    return dispositionType != null && dispositionType.equals("attachment");
  }
}
