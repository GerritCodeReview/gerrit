package com.google.gerrit.httpd.restapi;

import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.Writer;

public class LegacyProjectWriter extends JsonWriter {

  private String lastName;

  LegacyProjectWriter(Writer out) {
    super(out);
  }

  @Override
  public JsonWriter name(String name) throws IOException {
    super.name(name);
    lastName = name;
    return this;
  }

  @Override
  public JsonWriter value(String value) throws IOException {
    super.value(value);
    if ("repository".equals(lastName)) {
      name("project").value(value);
    }
    return this;
  }
}
