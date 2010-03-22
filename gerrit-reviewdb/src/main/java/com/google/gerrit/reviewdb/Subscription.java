package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.client.IntKey;

/** Project subscribing to a Branch of a Subproject */
public final class Subscription {

  /** Subscription key */
  public static class Id extends IntKey<com.google.gwtorm.client.Key<?>> {
    private static final long serialVersionUID = 1L;

    @Column(id = 1)
    protected int id;

    protected Id() {
    }

    public Id(final int id) {
      this.id = id;
    }

    @Override
    public int get() {
      return id;
    }

    @Override
    protected void set(int newValue) {
      id = newValue;
    }
  }

  /** unique identifier of the subscription */
  @Column(id = 1)
  protected Id id;
  @Column(id = 2)
  protected Branch.NameKey target;
  @Column(id = 3)
  protected Branch.NameKey source;

  protected Subscription() {
  }

  public Subscription(final Subscription.Id newId,
      final Branch.NameKey newTarget, final Branch.NameKey newSource) {
    id = newId;
    target = newTarget;
    source = newSource;
  }

  @Override
  public String toString() {
    return id.get() + " " + target.getParentKey().get() + " " + target.get()+ " " + source.getParentKey().get() + " " + source.get();
  }

  public Branch.NameKey getSubscriber() {
    return target;
  }
}
