package com.google.gerrit.acceptance.api.config;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupMembership;
import com.google.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Test;
import org.kohsuke.args4j.Option;

/** Ensure that we don't persist dangerous data members in @Singleton classes */
@NoHttpd
public class SingletonIT extends AbstractDaemonTest {

  public static boolean isSingleton(Object obj) {
    Annotation[] annotations = obj.getClass().getAnnotations();
    for (Annotation annotation : annotations) {
      if (annotation.annotationType().equals(Singleton.class)) return true;
    }
    return false;
  }

  static void singletonCheck(
      Object obj,
      Optional<Class> maybeForbiddenClass,
      Optional<Class> maybeForbiddenAnnotation,
      Set<Class> done,
      List<String> messages)
      throws Exception {

    Class klazz = obj.getClass();
    if (done.contains(klazz)) {
      return;
    }
    done.add(klazz);

    boolean singleton = isSingleton(obj);

    for (Field f : klazz.getDeclaredFields()) {
      try {
        f.setAccessible(true);
      } catch (InaccessibleObjectException e) {
        // NOSUBMIT - requires languagelevel 9.
        continue;
      }
      Object val = f.get(obj);
      f.setAccessible(false);

      if (Modifier.isStatic(f.getModifiers())) continue;

      for (Annotation a : f.getAnnotations()) {
        if (singleton
            && maybeForbiddenAnnotation.isPresent()
            && a.annotationType().equals(maybeForbiddenAnnotation.get())) {
          messages.add(
              String.format(
                  "singleton class %s has field %s with annotation %s",
                  klazz, f.getName(), maybeForbiddenAnnotation.get()));
        }
      }

      if (val == null) continue;

      if (singleton
          && maybeForbiddenClass.isPresent()
          && maybeForbiddenClass.get().isInstance(val)) {
        messages.add(
            String.format(
                "singleton class %s has field %s with forbidden class %s",
                klazz, f.getName(), maybeForbiddenClass.get()));
      } else {
        singletonCheck(val, maybeForbiddenClass, maybeForbiddenAnnotation, done, messages);
      }
    }
  }

  /** Recursively walk {@code obj} to check for Singletons persisting forbidden classes. */
  public void singletonCheckClass(Object obj, Class forbidden) throws Exception {
    List<String> messages = new ArrayList<>();
    singletonCheck(obj, Optional.of(forbidden), Optional.empty(), new HashSet<>(), messages);
    assertThat(messages).isEmpty();
  }

  /** Recursively walk {@code obj} to check for Singletons persisting forbidden classes. */
  public void singletonCheckAnnotation(Object obj, Class forbidden) throws Exception {
    List<String> messages = new ArrayList<>();
    singletonCheck(obj, Optional.empty(), Optional.of(forbidden), new HashSet<>(), messages);
    assertThat(messages).isEmpty();
  }

  // TODO(hanwen): what about plugin endpoints?
  @Test
  public void CurrentUser() throws Exception {
    singletonCheckClass(gApi, CurrentUser.class);
  }

  @Test
  public void PersonIdent() throws Exception {
    singletonCheckClass(gApi, PersonIdent.class);
  }

  @Test
  public void Option() throws Exception {
    singletonCheckAnnotation(gApi, PersonIdent.class);
  }

  static class OuterClass {
    InnerClass inner;
  }

  abstract static class InnerClass {}

  @Singleton
  static class InnerSingletonBoom extends InnerClass {
    CurrentUser user;

    @Option(name = "abc")
    String wrong;
  }

  @Singleton
  static class InnerSingletonOk extends InnerClass {
    String right;
  }

  static class InnerOk extends InnerClass {
    CurrentUser user;

    @Option(name = "abc")
    String right;
  }

  @Test
  public void selfTest() throws Exception {
    OuterClass outer = new OuterClass();
    outer.inner = new InnerOk();
    singletonCheckClass(outer, CurrentUser.class);
    singletonCheckAnnotation(outer, Option.class);

    outer.inner = new InnerSingletonOk();
    singletonCheckClass(outer, CurrentUser.class);
    singletonCheckAnnotation(outer, Option.class);

    InnerSingletonBoom boom = new InnerSingletonBoom();
    boom.user =
        new CurrentUser() {
          @Override
          public GroupMembership getEffectiveGroups() {
            return null;
          }

          @Override
          public Object getCacheKey() {
            return null;
          }
        };
    outer.inner = boom;

    List<String> messages = new ArrayList<>();
    singletonCheck(
        outer, Optional.of(CurrentUser.class), Optional.empty(), new HashSet<>(), messages);
    assertThat(messages).isNotEmpty();

    messages = new ArrayList<>();
    singletonCheck(outer, Optional.empty(), Optional.of(Option.class), new HashSet<>(), messages);
    assertThat(messages).isNotEmpty();
  }
}
