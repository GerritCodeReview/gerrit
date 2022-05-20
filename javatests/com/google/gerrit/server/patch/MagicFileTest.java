// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.testing.InMemoryRepositoryManager;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

public class MagicFileTest {

  private final GitRepositoryManager repositoryManager = new InMemoryRepositoryManager();

  @Test
  public void magicFileContentIsBuiltCorrectly() {
    MagicFile magicFile =
        MagicFile.builder()
            .generatedContent("Generated 1\n")
            .modifiableContent("Modifiable 1\n")
            .build();

    assertThat(magicFile.getFileContent()).isEqualTo("Generated 1\nModifiable 1\n");
  }

  @Test
  public void generatedContentMayBeEmpty() {
    MagicFile magicFile = MagicFile.builder().modifiableContent("Modifiable 1\n").build();

    assertThat(magicFile.getFileContent()).isEqualTo("Modifiable 1\n");
  }

  @Test
  public void modifiableContentMayBeEmpty() {
    MagicFile magicFile = MagicFile.builder().generatedContent("Generated 1\n").build();

    assertThat(magicFile.getFileContent()).isEqualTo("Generated 1\n");
  }

  @Test
  public void generatedContentAlwaysHasNewlineAtEnd() {
    MagicFile magicFile = MagicFile.builder().generatedContent("Generated 1").build();

    assertThat(magicFile.generatedContent()).isEqualTo("Generated 1\n");
  }

  @Test
  public void modifiableContentAlwaysHasNewlineAtEnd() {
    MagicFile magicFile = MagicFile.builder().modifiableContent("Modifiable 1").build();

    assertThat(magicFile.modifiableContent()).isEqualTo("Modifiable 1\n");
  }

  @Test
  public void startOfModifiableContentIsIndicatedCorrectlyWhenGeneratedContentIsPresent() {
    MagicFile magicFile =
        MagicFile.builder()
            .generatedContent("Line 1\nLine2\n")
            .modifiableContent("Line 3\n")
            .build();

    // Generated content. -> Modifiable content starts in line 3.
    assertThat(magicFile.getStartLineOfModifiableContent()).isEqualTo(3);
  }

  @Test
  public void startOfModifiableContentIsIndicatedCorrectlyWhenGeneratedContentIsEmpty() {
    MagicFile magicFile = MagicFile.builder().modifiableContent("Line 1\n").build();

    assertThat(magicFile.getStartLineOfModifiableContent()).isEqualTo(1);
  }

  @Test
  public void commitMessageFileOfRootCommitContainsCorrectContent() throws Exception {
    try (Repository repository = repositoryManager.createRepository(Project.nameKey("repo1"));
        TestRepository<Repository> testRepo = new TestRepository<>(repository);
        ObjectReader objectReader = repository.newObjectReader()) {

      Instant authorTime =
          LocalDateTime.of(2020, Month.APRIL, 23, 19, 30, 27).atZone(ZoneOffset.UTC).toInstant();
      PersonIdent author =
          new PersonIdent("Alfred", "alfred@example.com", authorTime, ZoneId.of("UTC"));

      Instant committerTime =
          LocalDateTime.of(2021, Month.JANUARY, 6, 5, 12, 55).atZone(ZoneOffset.UTC).toInstant();
      PersonIdent committer =
          new PersonIdent("Luise", "luise@example.com", committerTime, ZoneId.of("UTC"));

      ObjectId commit =
          testRepo
              .commit()
              .message("Subject line\n\nFurther explanations.\n")
              .author(author)
              .committer(committer)
              .noParents()
              .create();

      MagicFile commitMessageFile = MagicFile.forCommitMessage(objectReader, commit);

      // The content of the commit message file must not change over time as existing comments
      // would otherwise refer to different content than when they were originally left.
      // -> Keep this format stable over time.
      assertThat(commitMessageFile.getFileContent())
          .isEqualTo(
              "Author:     Alfred <alfred@example.com>\n"
                  + "AuthorDate: 2020-04-23 19:30:27 +0000\n"
                  + "Commit:     Luise <luise@example.com>\n"
                  + "CommitDate: 2021-01-06 05:12:55 +0000\n"
                  + "\n"
                  + "Subject line\n"
                  + "\n"
                  + "Further explanations.\n");
    }
  }

  @Test
  public void commitMessageFileOfNonMergeCommitContainsCorrectContent() throws Exception {
    try (Repository repository = repositoryManager.createRepository(Project.nameKey("repo1"));
        TestRepository<Repository> testRepo = new TestRepository<>(repository);
        ObjectReader objectReader = repository.newObjectReader()) {

      Instant authorTime =
          LocalDateTime.of(2020, Month.APRIL, 23, 19, 30, 27).atZone(ZoneOffset.UTC).toInstant();
      PersonIdent author =
          new PersonIdent("Alfred", "alfred@example.com", authorTime, ZoneId.of("UTC"));

      Instant committerTime =
          LocalDateTime.of(2021, Month.JANUARY, 6, 5, 12, 55).atZone(ZoneOffset.UTC).toInstant();
      PersonIdent committer =
          new PersonIdent("Luise", "luise@example.com", committerTime, ZoneId.of("UTC"));

      RevCommit parent =
          testRepo.commit().message("Parent subject\n\nParent further details.").create();
      ObjectId commit =
          testRepo
              .commit()
              .message("Subject line\n\nFurther explanations.\n")
              .author(author)
              .committer(committer)
              .parent(parent)
              .create();

      MagicFile commitMessageFile = MagicFile.forCommitMessage(objectReader, commit);

      // The content of the commit message file must not change over time as existing comments
      // would otherwise refer to different content than when they were originally left.
      // -> Keep this format stable over time.
      assertThat(commitMessageFile.getFileContent())
          .isEqualTo(
              String.format(
                  "Parent:     %s (Parent subject)\n"
                      + "Author:     Alfred <alfred@example.com>\n"
                      + "AuthorDate: 2020-04-23 19:30:27 +0000\n"
                      + "Commit:     Luise <luise@example.com>\n"
                      + "CommitDate: 2021-01-06 05:12:55 +0000\n"
                      + "\n"
                      + "Subject line\n"
                      + "\n"
                      + "Further explanations.\n",
                  parent.name().substring(0, 8)));
    }
  }

  @Test
  public void commitMessageFileOfMergeCommitContainsCorrectContent() throws Exception {
    try (Repository repository = repositoryManager.createRepository(Project.nameKey("repo1"));
        TestRepository<Repository> testRepo = new TestRepository<>(repository);
        ObjectReader objectReader = repository.newObjectReader()) {

      Instant authorTime =
          LocalDateTime.of(2020, Month.APRIL, 23, 19, 30, 27).atZone(ZoneOffset.UTC).toInstant();
      PersonIdent author =
          new PersonIdent("Alfred", "alfred@example.com", authorTime, ZoneId.of("UTC"));

      Instant committerTime =
          LocalDateTime.of(2021, Month.JANUARY, 6, 5, 12, 55).atZone(ZoneOffset.UTC).toInstant();
      PersonIdent committer =
          new PersonIdent("Luise", "luise@example.com", committerTime, ZoneId.of("UTC"));

      RevCommit parent1 = testRepo.commit().message("Parent 1\n\nExplanation 1.").create();
      RevCommit parent2 = testRepo.commit().message("Parent 2\n\nExplanation 2.").create();
      ObjectId commit =
          testRepo
              .commit()
              .message("Subject line\n\nFurther explanations.\n")
              .author(author)
              .committer(committer)
              .parent(parent1)
              .parent(parent2)
              .create();

      MagicFile commitMessageFile = MagicFile.forCommitMessage(objectReader, commit);

      // The content of the commit message file must not change over time as existing comments
      // would otherwise refer to different content than when they were originally left.
      // -> Keep this format stable over time.
      String expectedContent =
          String.format(
              "Merge Of:   %s (Parent 1)\n"
                  + "            %s (Parent 2)\n"
                  + "Author:     Alfred <alfred@example.com>\n"
                  + "AuthorDate: 2020-04-23 19:30:27 +0000\n"
                  + "Commit:     Luise <luise@example.com>\n"
                  + "CommitDate: 2021-01-06 05:12:55 +0000\n"
                  + "\n"
                  + "Subject line\n"
                  + "\n"
                  + "Further explanations.\n",
              parent1.name().substring(0, 8), parent2.name().substring(0, 8));
      assertThat(commitMessageFile.getFileContent()).isEqualTo(expectedContent);
    }
  }

  @Test
  public void commitMessageFileEndsWithEmptyLineIfCommitMessageIsEmpty() throws Exception {
    try (Repository repository = repositoryManager.createRepository(Project.nameKey("myRepo"));
        TestRepository<Repository> testRepo = new TestRepository<>(repository);
        ObjectReader objectReader = repository.newObjectReader()) {
      RevCommit commit = testRepo.commit().message("").create();

      MagicFile commitMessageFile = MagicFile.forCommitMessage(objectReader, commit);
      assertThat(commitMessageFile.getFileContent()).endsWith("\n\n");
    }
  }

  @Test
  public void commitMessageFileContainsFullCommitMessageAsModifiablePart() throws Exception {
    try (Repository repository = repositoryManager.createRepository(Project.nameKey("myRepo"));
        TestRepository<Repository> testRepo = new TestRepository<>(repository);
        ObjectReader objectReader = repository.newObjectReader()) {
      RevCommit commit =
          testRepo.commit().message("Subject line\n\nFurther explanations.\n").create();

      MagicFile commitMessageFile = MagicFile.forCommitMessage(objectReader, commit);
      assertThat(commitMessageFile.modifiableContent())
          .isEqualTo("Subject line\n\nFurther explanations.\n");
    }
  }

  @Test
  public void mergeListFileContainsCorrectContentForDiffAgainstFirstParent() throws Exception {
    try (Repository repository = repositoryManager.createRepository(Project.nameKey("myRepo"));
        TestRepository<Repository> testRepo = new TestRepository<>(repository);
        ObjectReader objectReader = repository.newObjectReader()) {
      RevCommit parent1 = testRepo.commit().message("Parent 1\n\nExplanation 1.").create();
      RevCommit parent2 = testRepo.commit().message("Parent 2\n\nExplanation 2.").create();
      ObjectId commit = testRepo.commit().parent(parent1).parent(parent2).create();

      MagicFile mergeListFile =
          MagicFile.forMergeList(ComparisonType.againstParent(1), objectReader, commit);

      // The content of the merge list file must not change over time as existing comments
      // would otherwise refer to different content than when they were originally left.
      // -> Keep this format stable over time.
      String expectedContent =
          String.format("Merge List:\n\n* %s Parent 2\n", parent2.name().substring(0, 8));
      assertThat(mergeListFile.getFileContent()).isEqualTo(expectedContent);
    }
  }

  @Test
  public void mergeListFileContainsCorrectContentForDiffAgainstSecondParent() throws Exception {
    try (Repository repository = repositoryManager.createRepository(Project.nameKey("myRepo"));
        TestRepository<Repository> testRepo = new TestRepository<>(repository);
        ObjectReader objectReader = repository.newObjectReader()) {
      RevCommit parent1 = testRepo.commit().message("Parent 1\n\nExplanation 1.").create();
      RevCommit parent2 = testRepo.commit().message("Parent 2\n\nExplanation 2.").create();
      ObjectId commit = testRepo.commit().parent(parent1).parent(parent2).create();

      MagicFile mergeListFile =
          MagicFile.forMergeList(ComparisonType.againstParent(2), objectReader, commit);

      // The content of the merge list file must not change over time as existing comments
      // would otherwise refer to different content than when they were originally left.
      // -> Keep this format stable over time.
      String expectedContent =
          String.format("Merge List:\n\n* %s Parent 1\n", parent1.name().substring(0, 8));
      assertThat(mergeListFile.getFileContent()).isEqualTo(expectedContent);
    }
  }

  @Test
  public void mergeListFileContainsCorrectContentForDiffAgainstAutoMerge() throws Exception {
    try (Repository repository = repositoryManager.createRepository(Project.nameKey("myRepo"));
        TestRepository<Repository> testRepo = new TestRepository<>(repository);
        ObjectReader objectReader = repository.newObjectReader()) {
      RevCommit parent1 = testRepo.commit().message("Parent 1\n\nExplanation 1.").create();
      RevCommit parent2 = testRepo.commit().message("Parent 2\n\nExplanation 2.").create();
      ObjectId commit = testRepo.commit().parent(parent1).parent(parent2).create();

      MagicFile mergeListFile =
          MagicFile.forMergeList(ComparisonType.againstAutoMerge(), objectReader, commit);

      // When auto-merge is chosen, we fall back to the diff against the first parent.
      String expectedContent =
          String.format("Merge List:\n\n* %s Parent 2\n", parent2.name().substring(0, 8));
      assertThat(mergeListFile.getFileContent()).isEqualTo(expectedContent);
    }
  }

  @Test
  public void mergeListFileIsEmptyForRootCommit() throws Exception {
    try (Repository repository = repositoryManager.createRepository(Project.nameKey("myRepo"));
        TestRepository<Repository> testRepo = new TestRepository<>(repository);
        ObjectReader objectReader = repository.newObjectReader()) {
      ObjectId commit = testRepo.commit().noParents().create();

      MagicFile mergeListFile =
          MagicFile.forMergeList(ComparisonType.againstParent(1), objectReader, commit);

      assertThat(mergeListFile.getFileContent()).isEmpty();
    }
  }

  @Test
  public void mergeListFileIsEmptyForNonMergeCommit() throws Exception {
    try (Repository repository = repositoryManager.createRepository(Project.nameKey("myRepo"));
        TestRepository<Repository> testRepo = new TestRepository<>(repository);
        ObjectReader objectReader = repository.newObjectReader()) {
      RevCommit parent = testRepo.commit().message("Parent 1\n").create();
      ObjectId commit = testRepo.commit().parent(parent).create();

      MagicFile mergeListFile =
          MagicFile.forMergeList(ComparisonType.againstParent(1), objectReader, commit);

      assertThat(mergeListFile.getFileContent()).isEmpty();
    }
  }

  @Test
  public void mergeListFileDoesNotHaveAModifiablePart() throws Exception {
    try (Repository repository = repositoryManager.createRepository(Project.nameKey("myRepo"));
        TestRepository<Repository> testRepo = new TestRepository<>(repository);
        ObjectReader objectReader = repository.newObjectReader()) {
      RevCommit parent1 = testRepo.commit().message("Parent 1\n").create();
      RevCommit parent2 = testRepo.commit().message("Parent 2\n").create();
      ObjectId commit = testRepo.commit().parent(parent1).parent(parent2).create();

      MagicFile mergeListFile =
          MagicFile.forMergeList(ComparisonType.againstParent(1), objectReader, commit);

      // Nothing in the merge list file represents something users may modify.
      assertThat(mergeListFile.modifiableContent()).isEmpty();
    }
  }
}
