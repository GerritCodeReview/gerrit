package com.google.gerrit.server.mail.send;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.mail.EmailTaskConverter;
import com.google.gerrit.server.mail.EmailTaskDispatcher;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Singleton
public class InMemoryEmailTaskDispatcher implements EmailTaskDispatcher {
  private final Consumer emailTaskConsumer;

  @Inject
  public InMemoryEmailTaskDispatcher(Consumer emailTaskConsumer) {
    this.emailTaskConsumer = emailTaskConsumer;
  }

  @Override
  public void dispatch(EmailTask emailTask) {
    emailTaskConsumer.consumeEvents(emailTask);
  }

  public static class Module extends FactoryModule {
    @Override
    public void configure() {
      bind(EmailTaskDispatcher.class).to(InMemoryEmailTaskDispatcher.class);
    }
  }

  /** Handles email task events. */
  @Singleton
  public static class Consumer {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private final EmailTaskConverter.Args args;
    private final ExecutorService sendEmailsExecutor;

    @Inject
    public Consumer(
        EmailTaskConverter.Args args, @SendEmailExecutor ExecutorService sendEmailExecutor) {
      this.args = args;
      this.sendEmailsExecutor = sendEmailExecutor;
    }

    public void consumeEvents(EmailTask emailTask) {
      @SuppressWarnings("unused")
      Future<?> possiblyIgnoredError =
          sendEmailsExecutor.submit(
              new Runnable() {
                @Override
                public void run() {
                  try {
                    EmailTaskConverter.getInstance(emailTask.getEventType(), args)
                        .convert(emailTask)
                        .send();
                  } catch (EmailException e) {
                    logger.atSevere().withCause(e).log("Failed to send the email");
                  }
                }

                @Override
                public String toString() {
                  // The name is needed in testing because the main thread waits on the execution
                  // of the email threads before fetching the results.
                  return "send-email " + emailTask.getEventType().name();
                }
              });
    }
  }
}
