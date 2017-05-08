[1mdiff --git a/gerrit-server/src/main/java/com/google/gerrit/server/index/change/ChangeSchemaDefinitions.java b/gerrit-server/src/main/java/com/google/gerrit/server/index/change/ChangeSchemaDefinitions.java[m
[1mindex ec507f42ec..09b2467a5d 100644[m
[1m--- a/gerrit-server/src/main/java/com/google/gerrit/server/index/change/ChangeSchemaDefinitions.java[m
[1m+++ b/gerrit-server/src/main/java/com/google/gerrit/server/index/change/ChangeSchemaDefinitions.java[m
[36m@@ -73,7 +73,7 @@[m [mpublic class ChangeSchemaDefinitions extends SchemaDefinitions<ChangeData> {[m
   @Deprecated static final Schema<ChangeData> V40 = schema(V39, ChangeField.PRIVATE);[m
   @Deprecated static final Schema<ChangeData> V41 = schema(V40, ChangeField.REVIEWER_BY_EMAIL);[m
 [m
[31m-  static final Schema<ChangeData> V42 = schema(V41, ChangeField.WIP);[m
[32m+[m[32m  public static final Schema<ChangeData> V42 = schema(V41, ChangeField.WIP);[m
 [m
   public static final String NAME = "changes";[m
   public static final ChangeSchemaDefinitions INSTANCE = new ChangeSchemaDefinitions();[m
[1mdiff --git a/gerrit-server/src/test/java/com/google/gerrit/server/query/account/AbstractQueryAccountsTest.java b/gerrit-server/src/test/java/com/google/gerrit/server/query/account/AbstractQueryAccountsTest.java[m
[1mindex 3d13536510..f7f3672aa4 100644[m
[1m--- a/gerrit-server/src/test/java/com/google/gerrit/server/query/account/AbstractQueryAccountsTest.java[m
[1m+++ b/gerrit-server/src/test/java/com/google/gerrit/server/query/account/AbstractQueryAccountsTest.java[m
[36m@@ -39,6 +39,9 @@[m [mimport com.google.gerrit.server.account.AccountManager;[m
 import com.google.gerrit.server.account.AccountState;[m
 import com.google.gerrit.server.account.AuthRequest;[m
 import com.google.gerrit.server.config.AllProjectsName;[m
[32m+[m[32mimport com.google.gerrit.server.index.Schema;[m
[32m+[m[32mimport com.google.gerrit.server.index.SchemaUtil;[m
[32m+[m[32mimport com.google.gerrit.server.index.account.AccountSchemaDefinitions;[m
 import com.google.gerrit.server.schema.SchemaCreator;[m
 import com.google.gerrit.server.util.ManualRequestContext;[m
 import com.google.gerrit.server.util.OneOffRequestContext;[m
[36m@@ -56,6 +59,7 @@[m [mimport java.util.Arrays;[m
 import java.util.Collections;[m
 import java.util.Iterator;[m
 import java.util.List;[m
[32m+[m[32mimport java.util.SortedMap;[m
 import org.eclipse.jgit.lib.Config;[m
 import org.junit.After;[m
 import org.junit.Before;[m
[36m@@ -73,6 +77,18 @@[m [mpublic abstract class AbstractQueryAccountsTest extends GerritServerTests {[m
     return cfg;[m
   }[m
 [m
[32m+[m[32m  @ConfigSuite.Config[m
[32m+[m[32m  public static Config againstPreviousIndexVersion() {[m
[32m+[m[32m    Config cfg = defaultConfig();[m
[32m+[m[32m    SortedMap<Integer, Schema<AccountState>> schemas =[m
[32m+[m[32m        SchemaUtil.schemasFromClass(AccountSchemaDefinitions.class, AccountState.class);[m
[32m+[m[32m    if (schemas.size() > 1) {[m
[32m+[m[32m      int prevVersion = new ArrayList<>(schemas.keySet()).get(schemas.size() - 2);[m
[32m+[m[32m      cfg.setInt("index", AccountSchemaDefinitions.INSTANCE.getName(), "testVersion", prevVersion);[m
[32m+[m[32m    }[m
[32m+[m[32m    return cfg;[m
[32m+[m[32m  }[m
[32m+[m
   @Rule public final TestName testName = new TestName();[m
 [m
   @Inject protected AccountCache accountCache;[m
[36m@@ -410,7 +426,7 @@[m [mpublic abstract class AbstractQueryAccountsTest extends GerritServerTests {[m
   protected AccountInfo newAccount(String username, String fullName, String email, boolean active)[m
       throws Exception {[m
     String uniqueName = name(username);[m
[31m-[m
[32m+[m[41m    [m
     try {[m
       gApi.accounts().id(uniqueName).get();[m
       fail("user " + uniqueName + " already exists");[m
[36m@@ -448,7 +464,13 @@[m [mpublic abstract class AbstractQueryAccountsTest extends GerritServerTests {[m
     if (name == null) {[m
       return null;[m
     }[m
[32m+[m[41m    [m
     String suffix = testName.getMethodName().toLowerCase();[m
[32m+[m[32m    suffix = suffix.replaceAll("[\\[\\]]", "_");[m
[32m+[m[32m    if (suffix.endsWith("_")) {[m
[32m+[m[32m      suffix = suffix.substring(0, suffix.length() - 1);[m
[32m+[m[32m    }[m
[32m+[m[41m    [m
     if (name.contains("@")) {[m
       return name + "." + suffix;[m
     }[m
[1mdiff --git a/gerrit-server/src/test/java/com/google/gerrit/server/query/change/AbstractQueryChangesTest.java b/gerrit-server/src/test/java/com/google/gerrit/server/query/change/AbstractQueryChangesTest.java[m
[1mindex 95e84945ac..466f1fe16d 100644[m
[1m--- a/gerrit-server/src/test/java/com/google/gerrit/server/query/change/AbstractQueryChangesTest.java[m
[1m+++ b/gerrit-server/src/test/java/com/google/gerrit/server/query/change/AbstractQueryChangesTest.java[m
[36m@@ -76,6 +76,7 @@[m [mimport com.google.gerrit.server.config.AllUsersName;[m
 import com.google.gerrit.server.git.MetaDataUpdate;[m
 import com.google.gerrit.server.index.IndexConfig;[m
 import com.google.gerrit.server.index.QueryOptions;[m
[32m+[m[32mimport com.google.gerrit.server.index.Schema;[m
 import com.google.gerrit.server.index.SchemaUtil;[m
 import com.google.gerrit.server.index.change.ChangeField;[m
 import com.google.gerrit.server.index.change.ChangeIndexCollection;[m
[36m@@ -115,6 +116,7 @@[m [mimport java.util.LinkedHashMap;[m
 import java.util.List;[m
 import java.util.Map;[m
 import java.util.Optional;[m
[32m+[m[32mimport java.util.SortedMap;[m
 import java.util.concurrent.TimeUnit;[m
 import org.eclipse.jgit.junit.TestRepository;[m
 import org.eclipse.jgit.lib.Config;[m
[36m@@ -141,14 +143,14 @@[m [mpublic abstract class AbstractQueryChangesTest extends GerritServerTests {[m
   }[m
 [m
   @ConfigSuite.Config[m
[31m-  public static Config prevIndexVersion() {[m
[32m+[m[32m  public static Config againstPreviousIndexVersion() {[m
     Config cfg = defaultConfig();[m
[31m-    int latestVersion =[m
[31m-        SchemaUtil.schemasFromClass(ChangeSchemaDefinitions.class, ChangeData.class)[m
[31m-            .lastEntry()[m
[31m-            .getValue()[m
[31m-            .getVersion();[m
[31m-    cfg.setInt("index", "lucene", "testVersion", latestVersion - 1);[m
[32m+[m[32m    SortedMap<Integer, Schema<ChangeData>> schemas =[m
[32m+[m[32m        SchemaUtil.schemasFromClass(ChangeSchemaDefinitions.class, ChangeData.class);[m
[32m+[m[32m    if (schemas.size() > 1) {[m
[32m+[m[32m      int prevVersion = new ArrayList<>(schemas.keySet()).get(schemas.size() - 2);[m
[32m+[m[32m      cfg.setInt("index", ChangeSchemaDefinitions.INSTANCE.getName(), "testVersion", prevVersion);[m
[32m+[m[32m    }[m
     return cfg;[m
   }[m
 [m
[36m@@ -427,6 +429,11 @@[m [mpublic abstract class AbstractQueryChangesTest extends GerritServerTests {[m
 [m
   @Test[m
   public void byWip() throws Exception {[m
[32m+[m[32m    if (getSchemaVersion() < ChangeSchemaDefinitions.V42.getVersion()) {[m
[32m+[m[32m      assertThat(getSchema().hasField(ChangeField.WIP)).isFalse();[m
[32m+[m[32m      return;[m
[32m+[m[32m    }[m
[32m+[m
     TestRepository<Repo> repo = createProject("repo");[m
     Change change1 = insert(repo, newChange(repo), userId);[m
 [m
[36m@@ -444,6 +451,11 @@[m [mpublic abstract class AbstractQueryChangesTest extends GerritServerTests {[m
 [m
   @Test[m
   public void excludeWipChangeFromReviewersDashboards() throws Exception {[m
[32m+[m[32m    if (getSchemaVersion() < ChangeSchemaDefinitions.V42.getVersion()) {[m
[32m+[m[32m      assertThat(getSchema().hasField(ChangeField.WIP)).isFalse();[m
[32m+[m[32m      return;[m
[32m+[m[32m    }[m
[32m+[m
     Account.Id user1 = createAccount("user1");[m
     TestRepository<Repo> repo = createProject("repo");[m
     Change change1 = insert(repo, newChange(repo), userId);[m
[36m@@ -2205,4 +2217,12 @@[m [mpublic abstract class AbstractQueryChangesTest extends GerritServerTests {[m
             Patch.COMMIT_MSG, ImmutableList.<ReviewInput.CommentInput>of(comment));[m
     gApi.changes().id(changeId).current().review(input);[m
   }[m
[32m+[m
[32m+[m[32m  protected int getSchemaVersion() {[m
[32m+[m[32m    return getSchema().getVersion();[m
[32m+[m[32m  }[m
[32m+[m
[32m+[m[32m  protected Schema<ChangeData> getSchema() {[m
[32m+[m[32m    return indexes.getSearchIndex().getSchema();[m
[32m+[m[32m  }[m
 }[m
[1mdiff --git a/gerrit-server/src/test/java/com/google/gerrit/server/query/group/AbstractQueryGroupsTest.java b/gerrit-server/src/test/java/com/google/gerrit/server/query/group/AbstractQueryGroupsTest.java[m
[1mindex a0e5ee09a3..8ed14cf1e1 100644[m
[1m--- a/gerrit-server/src/test/java/com/google/gerrit/server/query/group/AbstractQueryGroupsTest.java[m
[1m+++ b/gerrit-server/src/test/java/com/google/gerrit/server/query/group/AbstractQueryGroupsTest.java[m
[36m@@ -36,6 +36,9 @@[m [mimport com.google.gerrit.server.account.AccountManager;[m
 import com.google.gerrit.server.account.AuthRequest;[m
 import com.google.gerrit.server.account.GroupCache;[m
 import com.google.gerrit.server.config.AllProjectsName;[m
[32m+[m[32mimport com.google.gerrit.server.index.Schema;[m
[32m+[m[32mimport com.google.gerrit.server.index.SchemaUtil;[m
[32m+[m[32mimport com.google.gerrit.server.index.group.GroupSchemaDefinitions;[m
 import com.google.gerrit.server.query.account.InternalAccountQuery;[m
 import com.google.gerrit.server.schema.SchemaCreator;[m
 import com.google.gerrit.server.util.ManualRequestContext;[m
[36m@@ -49,11 +52,13 @@[m [mimport com.google.inject.Inject;[m
 import com.google.inject.Injector;[m
 import com.google.inject.Provider;[m
 import com.google.inject.util.Providers;[m
[32m+[m[32mimport java.util.ArrayList;[m
 import java.util.Arrays;[m
 import java.util.Collections;[m
 import java.util.Iterator;[m
 import java.util.List;[m
 import java.util.Locale;[m
[32m+[m[32mimport java.util.SortedMap;[m
 import org.eclipse.jgit.lib.Config;[m
 import org.junit.After;[m
 import org.junit.Before;[m
[36m@@ -71,6 +76,18 @@[m [mpublic abstract class AbstractQueryGroupsTest extends GerritServerTests {[m
     return cfg;[m
   }[m
 [m
[32m+[m[32m  @ConfigSuite.Config[m
[32m+[m[32m  public static Config againstPreviousIndexVersion() {[m
[32m+[m[32m    Config cfg = defaultConfig();[m
[32m+[m[32m    SortedMap<Integer, Schema<AccountGroup>> schemas =[m
[32m+[m[32m        SchemaUtil.schemasFromClass(GroupSchemaDefinitions.class, AccountGroup.class);[m
[32m+[m[32m    if (schemas.size() > 1) {[m
[32m+[m[32m      int prevVersion = new ArrayList<>(schemas.keySet()).get(schemas.size() - 2);[m
[32m+[m[32m      cfg.setInt("index", GroupSchemaDefinitions.INSTANCE.getName(), "testVersion", prevVersion);[m
[32m+[m[32m    }[m
[32m+[m[32m    return cfg;[m
[32m+[m[32m  }[m
[32m+[m
   @Rule public final TestName testName = new TestName();[m
 [m
   @Inject protected AccountCache accountCache;[m
[36m@@ -432,11 +449,4 @@[m [mpublic abstract class AbstractQueryGroupsTest extends GerritServerTests {[m
   protected static Iterable<String> uuids(List<GroupInfo> groups) {[m
     return groups.stream().map(g -> g.id).collect(toList());[m
   }[m
[31m-[m
[31m-  protected String name(String name) {[m
[31m-    if (name == null) {[m
[31m-      return null;[m
[31m-    }[m
[31m-    return name + "_" + testName.getMethodName().toLowerCase();[m
[31m-  }[m
 }[m
