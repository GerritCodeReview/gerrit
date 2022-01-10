package com.google.gerrit.acceptance.server.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.Project.NameKey;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import com.google.gerrit.testing.InMemoryRepositoryManager.Repo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.junit.Test;

/** Tests for {@link CodeReviewCommit}. */
public class CodeReviewCommitTest {

  @Test
  public void checkSerializable_withStatusMessage() throws Exception {
    CodeReviewCommit commit = new CodeReviewCommit(createCommit());
    commit.setStatusMessage("Status");
    CodeReviewCommit deserializedCommit = serializeAndReadBack(commit);
    assertThat(deserializedCommit).isEqualTo(commit);
    assertThat(deserializedCommit.getStatusMessage().get()).isEqualTo("Status");
  }

  @Test
  public void checkSerializable_emptyStatusMessage() throws Exception {
    CodeReviewCommit commit = new CodeReviewCommit(createCommit());
    CodeReviewCommit deserializedCommit = serializeAndReadBack(commit);
    assertThat(deserializedCommit).isEqualTo(commit);
    assertThat(deserializedCommit.getStatusMessage().isPresent()).isFalse();
  }

  private CodeReviewCommit serializeAndReadBack(CodeReviewCommit codeReviewCommit)
      throws Exception {
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos)) {
      out.writeObject(codeReviewCommit);
      out.flush();
      try (ByteArrayInputStream fileIn = new ByteArrayInputStream(bos.toByteArray());
          ObjectInputStream in = new ObjectInputStream(fileIn); ) {
        return (CodeReviewCommit) in.readObject();
      }
    }
  }

  private ObjectId createCommit() throws IOException {
    InMemoryRepositoryManager repoManager = new InMemoryRepositoryManager();
    NameKey project = Project.nameKey("test");
    Repo repo = repoManager.createRepository(project);
    try (ObjectInserter oi = repo.newObjectInserter()) {
      PersonIdent ident = new PersonIdent(new PersonIdent("Test Ident", "test@test.com"));
      CommitBuilder cb = new CommitBuilder();
      cb.setTreeId(oi.insert(Constants.OBJ_TREE, new byte[] {}));
      cb.setCommitter(ident);
      cb.setAuthor(ident);
      cb.setMessage("Test commit");
      ObjectId commit = oi.insert(cb);
      oi.flush();
      return commit;
    }
  }
}
