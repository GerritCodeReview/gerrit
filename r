diff --git a/java/com/google/gerrit/server/query/change/ChangeQueryBuilder.java b/java/com/google/gerrit/server/query/change/ChangeQueryBuilder.java
index a64b68dcf1..ae8a8588d4 100644
--- a/java/com/google/gerrit/server/query/change/ChangeQueryBuilder.java
+++ b/java/com/google/gerrit/server/query/change/ChangeQueryBuilder.java
@@ -1545,69 +1545,15 @@ public class ChangeQueryBuilder extends QueryBuilder<ChangeData, ChangeQueryBuil
     return ChangePredicates.reviewedBy(parseAccountIgnoreVisibility(who));
   }
 
+  /**
+   * Parses the {@code destination} predicate.
+   *
+   * @param value the value of the {@code destination} predicate in the format {@code
+   *     [name=]<name>[,user=<user>|,group=<group>] || [group=<group>,|user=<user>,][name=]<name>}
+   */
   @Operator
   public Predicate<ChangeData> destination(String value) throws QueryParseException {
-    // [name=]<name>[,user=<user>|,group=<group>] || [group=<group>,|user=<user>,][name=]<name>
-    PredicateArgs inputArgs = new PredicateArgs(value);
-    String name = null;
-    Account.Id account = null;
-    GroupDescription.Internal group = null;
-
-    if (inputArgs.keyValue.containsKey(ARG_ID_USER)
-        && inputArgs.keyValue.containsKey(ARG_ID_GROUP)) {
-      throw new QueryParseException("User and group arguments are mutually exclusive");
-    }
-    // [name=]<name>
-    if (inputArgs.keyValue.containsKey(ARG_ID_NAME)) {
-      name = inputArgs.keyValue.get(ARG_ID_NAME).value();
-    } else if (inputArgs.positional.size() == 1) {
-      name = Iterables.getOnlyElement(inputArgs.positional);
-    } else if (inputArgs.positional.size() > 1) {
-      throw new QueryParseException("Error parsing named destination: " + value);
-    }
-
-    try {
-      // [,user=<user>]
-      if (inputArgs.keyValue.containsKey(ARG_ID_USER)) {
-        ImmutableSet<Account.Id> accounts =
-            parseAccount(inputArgs.keyValue.get(ARG_ID_USER).value());
-        if (accounts != null && accounts.size() > 1) {
-          throw error(
-              String.format(
-                  "\"%s\" resolves to multiple accounts", inputArgs.keyValue.get(ARG_ID_USER)));
-        }
-        account = (accounts == null ? self() : Iterables.getOnlyElement(accounts));
-      } else {
-        account = self();
-      }
-
-      // [,group=<group>]
-      if (inputArgs.keyValue.containsKey(ARG_ID_GROUP)) {
-        AccountGroup.UUID groupId =
-            parseGroup(inputArgs.keyValue.get(ARG_ID_GROUP).value()).getUUID();
-        GroupDescription.Basic backendGroup = args.groupBackend.get(groupId);
-        if (!(backendGroup instanceof GroupDescription.Internal)) {
-          throw error(backendGroup.getName() + " is not an Internal group");
-        }
-        group = (GroupDescription.Internal) backendGroup;
-      }
-
-      BranchNameKey branch = BranchNameKey.create(args.allUsersName, RefNames.refsUsers(account));
-      if (group != null) {
-        branch = BranchNameKey.create(args.allUsersName, RefNames.refsGroups(group.getGroupUUID()));
-      }
-      Set<BranchNameKey> destinations = getDestinationList(branch).getDestinations(name);
-
-      if (destinations != null && !destinations.isEmpty()) {
-        return new BranchSetIndexPredicate(FIELD_DESTINATION + ":" + value, destinations);
-      }
-    } catch (RepositoryNotFoundException e) {
-      throw new QueryParseException(
-          "Unknown named destination (no " + args.allUsersName + " repo): " + name, e);
-    } catch (IOException | ConfigInvalidException e) {
-      throw new QueryParseException("Error parsing named destination: " + value, e);
-    }
-    throw new QueryParseException("Unknown named destination: " + name);
+    throw new QueryParseException("named destinations are disabled");
   }
 
   protected DestinationList getDestinationList(BranchNameKey branch)
