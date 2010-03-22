package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Access;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.PrimaryKey;
import com.google.gwtorm.client.Query;
import com.google.gwtorm.client.ResultSet;

public interface SubscriptionAccess extends
    Access<Subscription, Subscription.Id> {
  @PrimaryKey("id")
  Subscription get(Subscription.Id id) throws OrmException;

  @Query("ORDER BY target.projectName")
  ResultSet<Subscription> all() throws OrmException;

  @Query("WHERE target = ?")
  ResultSet<Subscription> getSubscription(Branch.NameKey target)
      throws OrmException;

  @Query("WHERE source = ?")
  ResultSet<Subscription> getSubscribers(Branch.NameKey source)
      throws OrmException;
}
