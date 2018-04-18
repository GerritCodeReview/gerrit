package com.google.gerrit.server.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.function.Supplier;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PerThreadCacheTest {
  @Rule public ExpectedException exception = ExpectedException.none();

  @Test
  public void key_respectsClass() {
    assertEquals(PerThreadCache.Key.create(String.class), PerThreadCache.Key.create(String.class));
    assertNotEquals(
        PerThreadCache.Key.create(String.class), PerThreadCache.Key.create(Integer.class));
  }

  @Test
  public void key_respectsIdentifiers() {
    assertEquals(
        PerThreadCache.Key.create(String.class, "id1"),
        PerThreadCache.Key.create(String.class, "id1"));
    assertNotEquals(
        PerThreadCache.Key.create(String.class, "id1"),
        PerThreadCache.Key.create(String.class, "id2"));
  }

  @Test
  public void endToEndCache() {
    try (PerThreadCache ignored = PerThreadCache.create()) {
      PerThreadCache cache = PerThreadCache.get();
      PerThreadCache.Key<String> key1 = PerThreadCache.Key.create(String.class);

      String value1 = cache.get(key1, () -> "value1");
      assertEquals(value1, "value1");

      Supplier<String> neverCalled =
          () -> {
            throw new IllegalStateException("this method must not be called");
          };
      assertEquals(cache.get(key1, neverCalled), "value1");
    }
  }

  @Test
  public void doubleInstantiationFails() {
    try (PerThreadCache ignored = PerThreadCache.create()) {
      exception.expect(IllegalStateException.class);
      exception.expectMessage("called create() twice on the same request");
      PerThreadCache.create();
    }
  }
}
