package com.google.gerrit.server.mail.send;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.converter.AccountIdProtoConverter;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.proto.Entities.EmailTask;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountResolver.UnresolvableAccountException;
import com.google.gerrit.server.config.SendEmailExecutor;
import com.google.gerrit.server.mail.EmailTaskConverter;
import com.google.gerrit.server.mail.EmailTaskDispatcher;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.eclipse.jgit.errors.ConfigInvalidException;

@Singleton
public class InMemoryEmailTaskDispatcher implements EmailTaskDispatcher {
  private final Consumer.Factory emailTaskConsumerFactory;
  private final ThreadLocalRequestContext threadLocalRequestContext;

  @Inject
  public InMemoryEmailTaskDispatcher(
      Consumer.Factory emailTaskConsumerFactory,
      ThreadLocalRequestContext threadLocalRequestContext) {
    this.emailTaskConsumerFactory = emailTaskConsumerFactory;
    this.threadLocalRequestContext = threadLocalRequestContext;
  }

  @Override
  public void dispatch(EmailTask emailTask) {
    emailTaskConsumerFactory.create(threadLocalRequestContext).consumeEvents(emailTask);
  }

  public static class Module extends FactoryModule {
    @Override
    public void configure() {
      bind(EmailTaskDispatcher.class).to(InMemoryEmailTaskDispatcher.class);
    }
  }

  /** Handles email task events. */
  public static class Consumer {
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    private final EmailTaskConverter.Args args;
    private final ExecutorService sendEmailsExecutor;
    private final ThreadLocalRequestContext requestContext;
    private final AccountResolver accountResolver;
    private final IdentifiedUser.GenericFactory identifiedUserFactory;

    public interface Factory {
      Consumer create(ThreadLocalRequestContext threadLocalRequestContext);
    }

    @Inject
    public Consumer(
        EmailTaskConverter.Args args,
        @SendEmailExecutor ExecutorService sendEmailExecutor,
        AccountResolver accountResolver,
        IdentifiedUser.GenericFactory identifiedUserFactory,
        @Assisted ThreadLocalRequestContext requestContext) {
      this.args = args;
      this.sendEmailsExecutor = sendEmailExecutor;
      this.requestContext = requestContext;
      this.accountResolver = accountResolver;
      this.identifiedUserFactory = identifiedUserFactory;
    }

    public void consumeEvents(EmailTask emailTask) {
      @SuppressWarnings("unused")
      Future<?> possiblyIgnoredError =
          sendEmailsExecutor.submit(
              new Runnable() {
                @Override
                public void run() {
                  try {
                    RequestContext oldContext = null;
                    if (emailTask.hasOnBehalfOf()) {
                      final CurrentUser impersonatingUser = getImpersonatingUser();
                      oldContext = requestContext.setContext(() -> impersonatingUser);
                    }
                    EmailTaskConverter.getInstance(emailTask.getEventType(), args)
                        .convert(emailTask)
                        .send();
                    if (oldContext != null) {
                      requestContext.setContext(oldContext);
                    }
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

                private CurrentUser getImpersonatingUser() {
                  CurrentUser impersonatingUser;
                  CurrentUser onBehalfOf =
                      identifiedUserFactory.create(
                          AccountIdProtoConverter.INSTANCE.fromProto(emailTask.getOnBehalfOf()));
                  String caller =
                      AccountIdProtoConverter.INSTANCE.fromProto(emailTask.getCaller()).toString();
                  try {
                    impersonatingUser =
                        accountResolver.resolve(caller).asUniqueUserOnBehalfOf(onBehalfOf);
                  } catch (UnresolvableAccountException | ConfigInvalidException | IOException e) {
                    impersonatingUser = onBehalfOf;
                    logger.atWarning().withCause(e).log("Failed to switch context");
                  }
                  return impersonatingUser;
                }
              });
    }
  }
}
