package com.google.gerrit.server.notedb;

import com.google.gerrit.entities.SubmitRequirementResult;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

public class SubmitRequirementResultAdapter extends TypeAdapter<SubmitRequirementResult> {

  @Override
  public void write(JsonWriter out, SubmitRequirementResult value) throws IOException {
    // TODO: implement, and add a test?
  }

  @Override
  public SubmitRequirementResult read(JsonReader in) throws IOException {
    // TODO: implement
    return null;
  }
}
