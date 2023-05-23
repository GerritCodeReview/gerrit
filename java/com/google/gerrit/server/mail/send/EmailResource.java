package com.google.gerrit.server.mail.send;

import com.google.auto.value.AutoValue;
import com.google.protobuf.ByteString;

/**
 * Email resource that can be attached to an email.
 *
 * <p>Can be used for images included in html body of the email.
 */
@AutoValue
public abstract class EmailResource {
  public static EmailResource create(String contentId, String contentType, ByteString content) {
    return new AutoValue_EmailResource(contentId, contentType, content);
  }

  /** Value of Content-ID header used for referring to the resource from html body of the email. */
  public abstract String contentId();

  /** MIME type of the resource. */
  public abstract String contentType();

  /** Unencoded data that should be added to the email */
  public abstract ByteString content();
}
