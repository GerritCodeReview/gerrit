// Copyright (C) 2026 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.gpg.testing.TestCommitSignatures.sign;
import static com.google.gerrit.gpg.testing.TestKeys.validKeyWithExpiration;
import static com.google.gerrit.gpg.testing.TestKeys.validKeyWithoutExpiration;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.OBJ_COMMIT;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.account.AccountOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.CommitSignatureInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.git.CommitSignatureVerifier;
import com.google.gerrit.server.git.ContentCommitInserter;
import com.google.gerrit.server.git.ServerCommitSigner;
import com.google.gerrit.server.git.SignedCommitPolicy;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gson.JsonObject;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.ProvisionListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.bc.BcPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Test;

public class ServerSignedCommitIT extends AbstractDaemonTest {
  private static final String FINGERPRINT = "04AEA7ED2F821133E5B128D1ED0625DC46328A8C";
  private static final String CLIENT_FINGERPRINT = "C378369ACBCD34CC138D90B145311A6F378A0AED";

  private static Path keyDirectory;

  @Inject private ContentCommitInserter contentCommitInserter;
  @Inject private CommitSignatureVerifier verifier;
  @Inject private ServerCommitSigner signer;
  @Inject private SignedCommitPolicy policy;
  @Inject private AccountOperations accountOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @ConfigSuite.Default
  public static Config config() throws Exception {
    keyDirectory = Files.createTempDirectory("server-signing-test-");
    Files.writeString(
        keyDirectory.resolve("public.asc"), validKeyWithoutExpiration().getPublicKeyArmored());
    Files.writeString(
        keyDirectory.resolve("secret.asc"), validKeyWithoutExpiration().getSecretKeyArmored());
    Files.writeString(
        keyDirectory.resolve("public-b.asc"), validKeyWithExpiration().getPublicKeyArmored());
    Files.writeString(
        keyDirectory.resolve("secret-b.asc"), validKeyWithExpiration().getSecretKeyArmored());
    Config cfg = new Config();
    cfg.setBoolean("gpg", null, "enableCommitSignatureVerification", true);
    cfg.setString("gpg", null, "serverSigningKey", FINGERPRINT);
    cfg.setString(
        "gpg",
        "server-key " + FINGERPRINT,
        "publicKeyFile",
        keyDirectory.resolve("public.asc").toString());
    return cfg;
  }

  @Override
  public Module createModule() {
    return new AbstractModule() {
      @Override
      protected void configure() {
        var provisioning = new SigningKeyProvisioning();
        requestInjection(provisioning);
        bindListener(Matchers.any(), provisioning);
      }
    };
  }

  /** Provision through the real secure store before the startup key snapshot is constructed. */
  private static class SigningKeyProvisioning implements ProvisionListener {
    @Inject private Provider<SitePaths> site;
    @Inject @GerritServerConfig private Provider<Config> cfg;

    @Override
    public <T> void onProvision(ProvisionInvocation<T> invocation) {
      if (invocation.getBinding().getKey().getTypeLiteral().getRawType() == SecureStore.class) {
        SecureStore store = (SecureStore) invocation.provision();
        try {
          Files.createDirectories(site.get().etc_dir);
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
        String active = cfg.get().getString("gpg", null, "serverSigningKey");
        if (CLIENT_FINGERPRINT.equals(active)) {
          cfg.get()
              .setString(
                  "gpg",
                  "server-key " + CLIENT_FINGERPRINT,
                  "publicKeyFile",
                  keyDirectory.resolve("public-b.asc").toString());
        }
        store.set(
            "gpg",
            "server-key " + active,
            "secretKeyFile",
            keyDirectory
                .resolve(CLIENT_FINGERPRINT.equals(active) ? "secret-b.asc" : "secret.asc")
                .toString());
      }
    }
  }

  @Test
  public void generatedPayloadIsSignedWithoutReserializingItsMetadata() throws Exception {
    requireSignatures();
    byte[] unsigned = payload();
    try (Repository repo = repoManager.openRepository(project);
        ObjectInserter inserter = repo.newObjectInserter()) {
      ObjectId id =
          contentCommitInserter.insert(BranchNameKey.create(project, "master"), inserter, unsigned);
      inserter.flush();
      byte[] signed = repo.open(id, OBJ_COMMIT).getBytes();
      assertIndependentSignature(signed, unsigned);
      assertThat(verifier.verify(signed).status).isEqualTo(CommitSignatureInfo.Status.VERIFIED);
    }
  }

  @Test
  public void initialContentIsSignedButProjectConfigurationIsNot() throws Exception {
    ProjectInput input = new ProjectInput();
    input.name = name("required-initial");
    input.createEmptyCommit = true;
    input.requireSignedCommits = InheritableBoolean.TRUE;
    gApi.projects().create(input);
    try (Repository repo = repoManager.openRepository(Project.nameKey(input.name))) {
      byte[] raw =
          repo.open(repo.exactRef("refs/heads/master").getObjectId(), OBJ_COMMIT).getBytes();
      assertThat(verifier.verify(raw).status).isEqualTo(CommitSignatureInfo.Status.VERIFIED);
      byte[] config =
          repo.open(repo.exactRef("refs/meta/config").getObjectId(), OBJ_COMMIT).getBytes();
      assertThat(verifier.verify(config).status).isEqualTo(CommitSignatureInfo.Status.UNSIGNED);
    }
  }

  @Test
  public void activeServerKeyDoesNotRewriteAnUnsignedClientUpload() throws Exception {
    requireSignatures();
    ObjectId oldTip;
    try (Repository repo = repoManager.openRepository(project)) {
      oldTip = repo.exactRef("refs/heads/master").getObjectId();
    }
    pushFactory
        .create(admin.newIdent(), testRepo)
        .to("refs/for/master")
        .assertErrorStatus("requires verified OpenPGP commit signatures");
    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.exactRef("refs/heads/master").getObjectId()).isEqualTo(oldTip);
    }
  }

  @Test
  public void optionalContentAndRequiredProjectMetadataRemainUnsigned() throws Exception {
    byte[] unsigned = payload();
    try (Repository repo = repoManager.openRepository(project);
        ObjectInserter inserter = repo.newObjectInserter()) {
      assertThat(
              contentCommitInserter.insert(
                  BranchNameKey.create(project, "master"), inserter, unsigned))
          .isEqualTo(inserter.idFor(OBJ_COMMIT, unsigned));
      requireSignatures();
      assertThat(
              contentCommitInserter.insert(
                  BranchNameKey.create(project, "refs/meta/config"), inserter, unsigned))
          .isEqualTo(inserter.idFor(OBJ_COMMIT, unsigned));
    }
  }

  @Test
  public void existingVerifiedServerObjectIsReusedByteForByte() throws Exception {
    requireSignatures();
    byte[] signed =
        sign(payload(), validKeyWithoutExpiration(), Instant.parse("2020-01-01T00:00:00Z"));
    try (Repository repo = repoManager.openRepository(project);
        ObjectInserter inserter = repo.newObjectInserter()) {
      assertThat(
              contentCommitInserter.insert(
                  BranchNameKey.create(project, "master"), inserter, signed))
          .isEqualTo(inserter.idFor(OBJ_COMMIT, signed));
    }
  }

  @Test
  public void invalidExistingSignatureIsRejectedInsteadOfResigned() throws Exception {
    requireSignatures();
    byte[] signed =
        sign(payload(), validKeyWithoutExpiration(), Instant.parse("2020-01-01T00:00:00Z"));
    signed[signed.length - 2] ^= 1;
    try (Repository repo = repoManager.openRepository(project);
        ObjectInserter inserter = repo.newObjectInserter()) {
      ObjectId id = inserter.idFor(OBJ_COMMIT, signed);
      assertThrows(
          ResourceConflictException.class,
          () ->
              contentCommitInserter.insert(
                  BranchNameKey.create(project, "master"), inserter, signed));
      inserter.flush();
      assertThat(repo.getObjectDatabase().has(id)).isFalse();
    }
  }

  @Test
  public void submitMergeSignsTheFinalParentsAndPreservesTheClientSignature() throws Exception {
    registerClientKey();
    requireSignatures();
    ConfigInput config = new ConfigInput();
    config.submitType = SubmitType.MERGE_ALWAYS;
    gApi.projects().name(project.get()).config(config);
    ObjectId oldTip = tip();
    RevCommit client = clientCommit(oldTip, "merge.txt");
    String changeId = upload(client);
    assertThat(gApi.changes().id(changeId).current().signature().signerType)
        .isEqualTo(CommitSignatureInfo.SignerType.USER);
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    gApi.changes().id(changeId).current().submit();
    try (Repository repo = repoManager.openRepository(project)) {
      RevCommit merge = RevCommit.parse(repo.open(tip(), OBJ_COMMIT).getBytes());
      assertThat(Arrays.asList(merge.getParents())).containsExactly(oldTip, client).inOrder();
      byte[] signed = repo.open(merge, OBJ_COMMIT).getBytes();
      assertIndependentSignature(signed, unsignedPayload(signed));
      assertThat(verifier.verify(signed).signerType)
          .isEqualTo(CommitSignatureInfo.SignerType.SERVER);
      assertThat(repo.open(client, OBJ_COMMIT).getBytes())
          .isEqualTo(testRepo.getRepository().open(client, OBJ_COMMIT).getBytes());
    }
  }

  @Test
  public void submitRebaseAlwaysSignsTheRewrittenPayload() throws Exception {
    registerClientKey();
    requireSignatures();
    ConfigInput config = new ConfigInput();
    config.submitType = SubmitType.REBASE_ALWAYS;
    gApi.projects().name(project.get()).config(config);
    ObjectId oldTip = tip();
    RevCommit client = clientCommit(oldTip, "rebase.txt");
    String changeId = upload(client);
    gApi.changes().id(changeId).current().review(ReviewInput.approve());
    gApi.changes().id(changeId).current().submit();
    assertThat(tip()).isNotEqualTo(client);
    assertThat(gApi.changes().id(changeId).current().signature().signerType)
        .isEqualTo(CommitSignatureInfo.SignerType.SERVER);
    try (Repository repo = repoManager.openRepository(project)) {
      byte[] raw = repo.open(tip(), OBJ_COMMIT).getBytes();
      RevCommit rebased = RevCommit.parse(raw);
      assertThat(Arrays.asList(rebased.getParents())).containsExactly(oldTip);
      assertIndependentSignature(raw, unsignedPayload(raw));
    }
  }

  @Test
  public void restRebaseSignsANewPatchSetAgainstTheUpdatedBranch() throws Exception {
    registerClientKey();
    requireSignatures();
    ObjectId base = tip();
    String first = upload(clientCommit(base, "first.txt"));
    String second = upload(clientCommit(base, "second.txt"));
    gApi.changes().id(first).current().review(ReviewInput.approve());
    gApi.changes().id(first).current().submit();
    ObjectId updated = tip();
    gApi.changes().id(second).current().rebase();
    assertThat(gApi.changes().id(second).current().signature().signerType)
        .isEqualTo(CommitSignatureInfo.SignerType.SERVER);
    String revision = gApi.changes().id(second).get().currentRevision;
    try (Repository repo = repoManager.openRepository(project)) {
      byte[] raw = repo.open(ObjectId.fromString(revision), OBJ_COMMIT).getBytes();
      assertThat(Arrays.asList(RevCommit.parse(raw).getParents())).containsExactly(updated);
      assertIndependentSignature(raw, unsignedPayload(raw));
    }
  }

  @Test
  public void generatedEditIsSignedAndPublicationReusesItsExactObject() throws Exception {
    String changeId = createChange().getChangeId();
    requireSignatures();
    gApi.changes().id(changeId).edit().modifyFile("edited.txt", RawInputUtil.create("edited"));
    String edit = gApi.changes().id(changeId).edit().get().orElseThrow().commit.commit;
    try (Repository repo = repoManager.openRepository(project)) {
      byte[] raw = repo.open(ObjectId.fromString(edit), OBJ_COMMIT).getBytes();
      assertIndependentSignature(raw, unsignedPayload(raw));
      assertThat(verifier.verify(raw).signerType).isEqualTo(CommitSignatureInfo.SignerType.SERVER);
    }
    gApi.changes().id(changeId).edit().publish();
    assertThat(gApi.changes().id(changeId).get().currentRevision).isEqualTo(edit);
    assertThat(gApi.changes().id(changeId).current().signature().signerType)
        .isEqualTo(CommitSignatureInfo.SignerType.SERVER);
    assertThat(gApi.changes().id(changeId).edit().get()).isEmpty();
  }

  @Test
  public void generatedChangeSignatureResponseIsAServerAttestationNotAnAccount() throws Exception {
    requireSignatures();
    var change = gApi.changes().create(newChange()).get();
    var response = adminRestSession.get("/changes/" + change.id + "/revisions/current/signature");
    response.assertOK();
    JsonObject json = newGson().fromJson(response.getReader(), JsonObject.class);
    assertThat(json.get("status").getAsString()).isEqualTo("VERIFIED");
    assertThat(json.get("signer_type").getAsString()).isEqualTo("SERVER");
    assertThat(json.get("primary_key_fingerprint").getAsString()).isEqualTo(FINGERPRINT);
    assertThat(json.has("signer")).isFalse();
    assertThat(json.get("message").getAsString()).contains("does not identify the committer");
  }

  @Test
  public void restFileCommitSignsFinalizedBranchContent() throws Exception {
    requireSignatures();
    ObjectId oldTip = tip();
    adminRestSession
        .post(
            "/projects/" + project.get() + "/branches/master/commit",
            Map.of(
                "commit_message",
                "server-created file commit",
                "files",
                Map.of("new.txt", Map.of("content", "bmV3IGNvbnRlbnQ="))))
        .assertOK();
    try (Repository repo = repoManager.openRepository(project)) {
      byte[] raw = repo.open(tip(), OBJ_COMMIT).getBytes();
      assertThat(Arrays.asList(RevCommit.parse(raw).getParents())).containsExactly(oldTip);
      assertIndependentSignature(raw, unsignedPayload(raw));
      assertThat(verifier.verify(raw).signerType).isEqualTo(CommitSignatureInfo.SignerType.SERVER);
    }
  }

  @Test
  public void ordinaryClientKeyDoesNotAcquireServerAttestationTrust() throws Exception {
    registerClientKey();
    requireSignatures();
    RevCommit source = clientCommit(tip(), "mismatch.txt");
    CommitBuilder builder = new CommitBuilder();
    builder.setTreeId(source.getTree());
    builder.setParentIds(Arrays.asList(source.getParents()));
    builder.setAuthor(source.getAuthorIdent());
    builder.setCommitter(admin.newIdent());
    builder.setMessage(source.getFullMessage());
    ObjectId id;
    try (ObjectInserter inserter = testRepo.getRepository().newObjectInserter()) {
      id =
          inserter.insert(
              OBJ_COMMIT, sign(builder.build(), validKeyWithExpiration(), Instant.now()));
      inserter.flush();
    }
    testRepo.update("HEAD", id);
    var result = pushHead(testRepo, "refs/for/master").getRemoteUpdate("refs/for/master");
    assertThat(result.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
    assertThat(result.getMessage()).contains("IDENTITY_MISMATCH");
    assertThat(gApi.changes().query("project:" + project.get()).get()).isEmpty();
  }

  @Test
  public void faultyGeneratedSignatureIsRejectedBeforeInsertion() throws Exception {
    requireSignatures();
    var generatedBytes = new AtomicReference<byte[]>();
    var faulty =
        Guice.createInjector(
                new AbstractModule() {
                  @Override
                  protected void configure() {
                    bind(SignedCommitPolicy.class).toInstance(policy);
                    bind(ServerCommitSigner.class)
                        .toInstance(
                            raw -> {
                              byte[] generated = signer.signIfUnsigned(raw);
                              generated[generated.length - 2] ^= 1;
                              generatedBytes.set(generated);
                              return generated;
                            });
                  }
                })
            .getInstance(ContentCommitInserter.class);
    byte[] unsigned = payload();
    try (Repository repo = repoManager.openRepository(project);
        ObjectInserter inserter = repo.newObjectInserter()) {
      assertThrows(
          ResourceConflictException.class,
          () -> faulty.insert(BranchNameKey.create(project, "master"), inserter, unsigned));
      inserter.flush();
      assertThat(repo.getObjectDatabase().has(inserter.idFor(OBJ_COMMIT, generatedBytes.get())))
          .isFalse();
    }
  }

  @Test
  @GerritConfig(name = "gpg.serverSigningKey", value = CLIENT_FINGERPRINT)
  public void keyExpiryAfterStartupPreventsChangesWithoutUnsignedFallback() throws Exception {
    requireSignatures();
    ObjectId oldTip = tip();
    try {
      TimeUtil.setCurrentMillisSupplier(() -> Instant.parse("2066-01-01T00:00:00Z").toEpochMilli());
      RestApiException failure =
          assertThrows(RestApiException.class, () -> gApi.changes().create(newChange()));
      assertThat(failure).hasCauseThat().isInstanceOf(IOException.class);
    } finally {
      TimeUtil.resetCurrentMillisSupplier();
    }
    assertThat(tip()).isEqualTo(oldTip);
    assertThat(gApi.changes().query("project:" + project.get()).get()).isEmpty();
  }

  private ChangeInput newChange() {
    ChangeInput input = new ChangeInput();
    input.project = project.get();
    input.branch = "master";
    input.subject = "generated required change";
    return input;
  }

  private void registerClientKey() throws Exception {
    accountOperations
        .account(admin.id())
        .forUpdate()
        .addSecondaryEmail("test2@example.com")
        .update();
    requestScopeOperations.setApiUser(admin.id());
    gApi.accounts()
        .self()
        .putGpgKeys(List.of(validKeyWithExpiration().getPublicKeyArmored()), List.of());
  }

  private RevCommit clientCommit(ObjectId parent, String file) throws Exception {
    RevCommit source =
        testRepo
            .commit()
            .parent(testRepo.getRevWalk().parseCommit(parent))
            .author(admin.newIdent())
            .committer(admin.newIdent())
            .message(
                "client change\n\nChange-Id: I"
                    + CommitMessageUtil.generateChangeId().name()
                    + "\n")
            .add(file, file)
            .create();
    testRepo.getRevWalk().parseBody(source);
    CommitBuilder builder = new CommitBuilder();
    builder.setTreeId(source.getTree());
    builder.setParentIds(Arrays.asList(source.getParents()));
    builder.setAuthor(source.getAuthorIdent());
    builder.setCommitter(new PersonIdent("Client Signer", "test2@example.com"));
    builder.setMessage(source.getFullMessage());
    try (ObjectInserter inserter = testRepo.getRepository().newObjectInserter()) {
      ObjectId id =
          inserter.insert(
              OBJ_COMMIT, sign(builder.build(), validKeyWithExpiration(), Instant.now()));
      inserter.flush();
      RevCommit signed = testRepo.getRevWalk().parseCommit(id);
      testRepo.getRevWalk().parseBody(signed);
      return signed;
    }
  }

  private String upload(RevCommit commit) throws Exception {
    testRepo.update("HEAD", commit);
    assertThat(pushHead(testRepo, "refs/for/master").getRemoteUpdate("refs/for/master").getStatus())
        .isEqualTo(RemoteRefUpdate.Status.OK);
    return gApi.changes().query("commit:" + commit.name()).get().getFirst().id;
  }

  private ObjectId tip() throws Exception {
    try (Repository repo = repoManager.openRepository(project)) {
      return repo.exactRef("refs/heads/master").getObjectId();
    }
  }

  private static byte[] unsignedPayload(byte[] signed) {
    return new String(signed, ISO_8859_1)
        .replaceFirst("(?m)^gpgsig [^\\n]*\\n(?: [^\\n]*\\n)*", "")
        .getBytes(ISO_8859_1);
  }

  private void requireSignatures() throws Exception {
    ConfigInput input = new ConfigInput();
    input.requireSignedCommits = InheritableBoolean.TRUE;
    gApi.projects().name(project.get()).config(input);
  }

  private static byte[] payload() {
    return ("tree 4b825dc642cb6eb9a060e54bf8d69288fbee4904\n"
            + "parent 1111111111111111111111111111111111111111\n"
            + "parent 2222222222222222222222222222222222222222\n"
            + "author Original Author <author@example.com> 1577836800 -0300\n"
            + "committer Another User <unregistered@example.com> 1577836800 +0200\n"
            + "mergetag object deadbeefdeadbeefdeadbeefdeadbeefdeadbeef\n"
            + " type commit\n tag raw metadata\n"
            + "encoding ISO-8859-1\n\n"
            + "Preserve raw café bytes and trailing spaces  \n\ngpgsig in message\n")
        .getBytes(UTF_8);
  }

  private static void assertIndependentSignature(byte[] signed, byte[] unsigned) throws Exception {
    RevCommit commit = RevCommit.parse(signed);
    byte[] armor = commit.getRawGpgSignature();
    assertThat(armor).isNotNull();
    try (var in = PGPUtil.getDecoderStream(new ByteArrayInputStream(armor))) {
      var signatures = (PGPSignatureList) new BcPGPObjectFactory(in).nextObject();
      PGPSignature signature = signatures.get(0);
      signature.init(
          new BcPGPContentVerifierBuilderProvider(), validKeyWithoutExpiration().getPublicKey());
      signature.update(unsigned);
      assertThat(signature.verify()).isTrue();
    }
    // Independent removal, with a byte-preserving charset, proves every non-signature byte is kept.
    assertThat(unsignedPayload(signed)).isEqualTo(unsigned);
  }
}
