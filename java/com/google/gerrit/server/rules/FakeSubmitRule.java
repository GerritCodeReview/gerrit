package com.google.gerrit.server.rules;

import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.entities.SubmitRecord.Status;
import com.google.gerrit.extensions.annotations.Exports;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import java.util.Optional;

/** Fake submit rule that returns OK if the change contains one or more hashtags. */
@Singleton
public class FakeSubmitRule implements SubmitRule {
  public static class Module extends AbstractModule {
    @Override
    public void configure() {
      bind(SubmitRule.class).annotatedWith(Exports.named("Fake")).to(FakeSubmitRule.class);
    }
  }

  @Override
  public Optional<SubmitRecord> evaluate(ChangeData cd) {
    SubmitRecord record = new SubmitRecord();
    record.status = cd.hashtags().isEmpty() ? Status.NOT_READY : Status.OK;
    record.ruleName = FakeSubmitRule.class.getSimpleName();
    return Optional.of(record);
  }
}
