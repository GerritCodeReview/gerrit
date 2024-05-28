package com.google.gerrit.extensions.registration;

import com.google.gerrit.common.Nullable;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.util.Types;

public class StaticItem<T> {
  public static <T> void itemOf(Binder binder, Class<T> member, String implPluginName) {
    itemOf(binder, TypeLiteral.get(member), implPluginName);
  }

  public static <T> void itemOf(Binder binder, TypeLiteral<T> member, String implPluginName) {
    binder.bind(keyFor(member)).toInstance(new StaticItem<>(implPluginName));
  }

  public static <T> LinkedBindingBuilder<T> bind(Binder binder, Class<T> item) {
    return bind(binder, TypeLiteral.get(item));
  }

  public static <T> LinkedBindingBuilder<T> bind(Binder binder, TypeLiteral<T> item) {
    return binder.bind(item);
  }

  @SuppressWarnings("unchecked")
  private static <T> Key<StaticItem<T>> keyFor(TypeLiteral<T> member) {
    return (Key<StaticItem<T>>)
        Key.get(Types.newParameterizedType(StaticItem.class, member.getType()));
  }

  final String implPluginName;
  private Provider<T> provider;

  StaticItem(String implPluginName) {
    this.implPluginName = implPluginName;
  }

  public T get() {
    if (provider == null) {
      throw new RuntimeException("NOT INITIALIZED");
    }

    return provider.get();
  }

  void setProvider(Provider<T> provider) {
//    if (this.provider != null) {
//      throw new RuntimeException("StaticItem already set");
//    }
    this.provider = provider;
  }

  static class StaticItemProvider<T> implements Provider<T> {
    @Inject Injector injector;

    private final Key<StaticItem<T>> type;
    private final Class<? extends T> impl;

    StaticItemProvider(Key<StaticItem<T>> type, Class<? extends T> impl) {
      this.type = type;
      this.impl = impl;
    }

    @Override
    @Nullable
    public T get() {
      StaticItem<T> staticItem = injector.getInstance(type);
      System.out.println("StaticItemProvider.get");
      if (staticItem == null) {
        return null;
      }

      return injector.getInstance(impl);
    }
  }
}
