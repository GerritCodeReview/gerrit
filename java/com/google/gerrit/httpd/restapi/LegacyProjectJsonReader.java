package com.google.gerrit.httpd.restapi;

import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.Reader;

/** Replaces {@code project} with {@code repository}. */
public class LegacyProjectJsonReader extends JsonReader {

  LegacyProjectJsonReader(Reader reader) {
    super(reader);
  }

  @Override
  public String nextName() throws IOException {
    String nextName = super.nextName();
    if ("project".equals(nextName)) {
      return "repository";
    }
    if ("projects".equals(nextName)) {
      return "repositories";
    }
    return nextName;
  }
}
