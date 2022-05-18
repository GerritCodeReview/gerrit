package com.google.gerrit.acceptance.api.revision;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.extensions.common.testing.DiffInfoSubject.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.extensions.api.changes.PublishChangeEditInput;
import com.google.gerrit.extensions.client.Comment;
import com.google.gerrit.extensions.common.ChangeType;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DiffInfo.IntraLineStatus;
import com.google.gerrit.extensions.common.DirectFixInput;
import com.google.gerrit.extensions.common.FixReplacementInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class DirectFixPreviewIT extends AbstractDaemonTest {
  private String changeId;

  private static final String PLAIN_TEXT_CONTENT_TYPE = "text/plain";
  private static final String GERRIT_COMMIT_MESSAGE_TYPE = "text/x-gerrit-commit-message";

  private static final String FILE_NAME = "file_to_fix.txt";
  private static final String FILE_NAME2 = "another_file_to_fix.txt";
  private static final String FILE_NAME3 = "file_without_newline_at_end.txt";
  private static final String FILE_CONTENT =
      "First line\nSecond line\nThird line\nFourth line\nFifth line\nSixth line"
          + "\nSeventh line\nEighth line\nNinth line\nTenth line\n";
  private static final String FILE_CONTENT2 = "1st line\n2nd line\n3rd line\n";
  private static final String FILE_CONTENT3 = "1st line\n2nd line";

  private String commitId;

  @Before
  public void setUp() throws Exception {
    PushOneCommit push =
        pushFactory.create(
            admin.newIdent(),
            testRepo,
            "Provide files which can be used for fixes",
            ImmutableMap.of(
                FILE_NAME, FILE_CONTENT, FILE_NAME2, FILE_CONTENT2, FILE_NAME3, FILE_CONTENT3));
    PushOneCommit.Result changeResult = push.to("refs/for/master");
    changeId = changeResult.getChangeId();
    commitId = changeResult.getCommit().getName();
  }

  @Test
  public void getDirectFixPreviewForCommitMsg() throws Exception {
    String footer = "Change-Id: " + changeId;
    updateCommitMessage(
        changeId,
        "Commit title\n\nCommit message line 1\nLine 2\nLine 3\nLast line\n\n" + footer + "\n");
    // The test assumes that the first 5 lines is a header.
    // Line 10 has content "Line 2"
    DirectFixInput directFixInput =
        createDirectFixInput(Patch.COMMIT_MSG, "New content\n", 10, 0, 11, 0);
    Map<String, DiffInfo> fixPreview =
        gApi.changes().id(changeId).current().directFixPreview(directFixInput);
    assertThat(fixPreview).hasSize(1);
    assertThat(fixPreview).containsKey(Patch.COMMIT_MSG);

    DiffInfo diff = fixPreview.get(Patch.COMMIT_MSG);
    assertThat(diff).metaA().name().isEqualTo(Patch.COMMIT_MSG);
    assertThat(diff).metaA().contentType().isEqualTo(GERRIT_COMMIT_MESSAGE_TYPE);
    assertThat(diff).metaB().name().isEqualTo(Patch.COMMIT_MSG);
    assertThat(diff).metaB().contentType().isEqualTo(GERRIT_COMMIT_MESSAGE_TYPE);

    assertThat(diff).content().element(0).commonLines().hasSize(9);
    // Header has a dynamic content, do not check it
    assertThat(diff).content().element(0).commonLines().element(6).isEqualTo("Commit title");
    assertThat(diff).content().element(0).commonLines().element(7).isEqualTo("");
    assertThat(diff)
        .content()
        .element(0)
        .commonLines()
        .element(8)
        .isEqualTo("Commit message line 1");
    assertThat(diff).content().element(1).linesOfA().containsExactly("Line 2");
    assertThat(diff).content().element(1).linesOfB().containsExactly("New content");
    assertThat(diff)
        .content()
        .element(2)
        .commonLines()
        .containsExactly("Line 3", "Last line", "", footer, "");
  }

  @Test
  public void getDirectFixPreviewForNonExistingFile() throws Exception {
    DirectFixInput directFixInput =
        createDirectFixInput("a_non_existent_file.txt", "Modified content\n", 1, 0, 2, 0);

    assertThrows(
        ResourceNotFoundException.class,
        () -> gApi.changes().id(changeId).current().directFixPreview(directFixInput));
  }

  @Test
  public void getDirectFixPreview() throws Exception {
    FixReplacementInfo fixReplacementInfo1 = new FixReplacementInfo();
    fixReplacementInfo1.path = FILE_NAME;
    fixReplacementInfo1.replacement = "some replacement code";
    fixReplacementInfo1.range = createRange(3, 9, 8, 4);

    FixReplacementInfo fixReplacementInfo2 = new FixReplacementInfo();
    fixReplacementInfo2.path = FILE_NAME2;
    fixReplacementInfo2.replacement = "New line\n";
    fixReplacementInfo2.range = createRange(2, 0, 2, 0);

    List<FixReplacementInfo> fixReplacementInfoList =
        Arrays.asList(fixReplacementInfo1, fixReplacementInfo2);
    DirectFixInput directFixInput = new DirectFixInput();
    directFixInput.fixReplacementInfos = fixReplacementInfoList;

    Map<String, DiffInfo> fixPreview =
        gApi.changes().id(changeId).current().directFixPreview(directFixInput);

    DiffInfo diff = fixPreview.get(FILE_NAME);
    assertThat(diff).intralineStatus().isEqualTo(IntraLineStatus.OK);
    assertThat(diff).webLinks().isNull();
    assertThat(diff).binary().isNull();
    assertThat(diff).diffHeader().isNull();
    assertThat(diff).changeType().isEqualTo(ChangeType.MODIFIED);
    assertThat(diff).metaA().totalLineCount().isEqualTo(11);
    assertThat(diff).metaA().name().isEqualTo(FILE_NAME);
    assertThat(diff).metaA().commitId().isEqualTo(commitId);
    assertThat(diff).metaA().contentType().isEqualTo(PLAIN_TEXT_CONTENT_TYPE);
    assertThat(diff).metaA().webLinks().isNull();
    assertThat(diff).metaB().totalLineCount().isEqualTo(6);
    assertThat(diff).metaB().name().isEqualTo(FILE_NAME);
    assertThat(diff).metaB().commitId().isNull();
    assertThat(diff).metaB().contentType().isEqualTo(PLAIN_TEXT_CONTENT_TYPE);
    assertThat(diff).metaB().webLinks().isNull();

    assertThat(diff).content().hasSize(3);
    assertThat(diff)
        .content()
        .element(0)
        .commonLines()
        .containsExactly("First line", "Second line");
    assertThat(diff).content().element(0).linesOfA().isNull();
    assertThat(diff).content().element(0).linesOfB().isNull();

    assertThat(diff).content().element(1).commonLines().isNull();
    assertThat(diff)
        .content()
        .element(1)
        .linesOfA()
        .containsExactly(
            "Third line", "Fourth line", "Fifth line", "Sixth line", "Seventh line", "Eighth line");
    assertThat(diff)
        .content()
        .element(1)
        .linesOfB()
        .containsExactly("Third linsome replacement codeth line");

    assertThat(diff)
        .content()
        .element(2)
        .commonLines()
        .containsExactly("Ninth line", "Tenth line", "");
    assertThat(diff).content().element(2).linesOfA().isNull();
    assertThat(diff).content().element(2).linesOfB().isNull();

    DiffInfo diff2 = fixPreview.get(FILE_NAME2);
    assertThat(diff2).intralineStatus().isEqualTo(IntraLineStatus.OK);
    assertThat(diff2).webLinks().isNull();
    assertThat(diff2).binary().isNull();
    assertThat(diff2).diffHeader().isNull();
    assertThat(diff2).changeType().isEqualTo(ChangeType.MODIFIED);
    assertThat(diff2).metaA().totalLineCount().isEqualTo(4);
    assertThat(diff2).metaA().name().isEqualTo(FILE_NAME2);
    assertThat(diff2).metaA().commitId().isEqualTo(commitId);
    assertThat(diff2).metaA().contentType().isEqualTo(PLAIN_TEXT_CONTENT_TYPE);
    assertThat(diff2).metaA().webLinks().isNull();
    assertThat(diff2).metaB().totalLineCount().isEqualTo(5);
    assertThat(diff2).metaB().name().isEqualTo(FILE_NAME2);
    assertThat(diff2).metaB().commitId().isNull();
    assertThat(diff2).metaA().contentType().isEqualTo(PLAIN_TEXT_CONTENT_TYPE);
    assertThat(diff2).metaB().webLinks().isNull();

    assertThat(diff2).content().hasSize(3);
    assertThat(diff2).content().element(0).commonLines().containsExactly("1st line");
    assertThat(diff2).content().element(0).linesOfA().isNull();
    assertThat(diff2).content().element(0).linesOfB().isNull();

    assertThat(diff2).content().element(1).commonLines().isNull();
    assertThat(diff2).content().element(1).linesOfA().isNull();
    assertThat(diff2).content().element(1).linesOfB().containsExactly("New line");

    assertThat(diff2)
        .content()
        .element(2)
        .commonLines()
        .containsExactly("2nd line", "3rd line", "");
    assertThat(diff2).content().element(2).linesOfA().isNull();
    assertThat(diff2).content().element(2).linesOfB().isNull();
  }

  @Test
  public void getDirectFixPreviewAddNewLineAtEnd() throws Exception {
    DirectFixInput directFixInput = createDirectFixInput(FILE_NAME3, "\n", 2, 8, 2, 8);
    Map<String, DiffInfo> fixPreview =
        gApi.changes().id(changeId).current().directFixPreview(directFixInput);
    assertThat(fixPreview).hasSize(1);
    assertThat(fixPreview).containsKey(FILE_NAME3);

    DiffInfo diff = fixPreview.get(FILE_NAME3);
    assertThat(diff).metaA().totalLineCount().isEqualTo(2);
    // Original file doesn't have EOL marker at the end of file.
    // Due to the additional EOL mark diff has one additional line
    // This behavior is in line with ordinary get diff API.
    assertThat(diff).metaB().totalLineCount().isEqualTo(3);

    assertThat(diff).content().hasSize(2);
    assertThat(diff).content().element(0).commonLines().containsExactly("1st line");
    assertThat(diff).content().element(1).linesOfA().containsExactly("2nd line");
    assertThat(diff).content().element(1).linesOfB().containsExactly("2nd line", "");
  }

  private DirectFixInput createDirectFixInput(
      String file_name,
      String replacement,
      int startLine,
      int startCharacter,
      int endLine,
      int endCharacter) {
    FixReplacementInfo fixReplacementInfo = new FixReplacementInfo();
    fixReplacementInfo.path = file_name;
    fixReplacementInfo.replacement = replacement;
    fixReplacementInfo.range = createRange(startLine, startCharacter, endLine, endCharacter);

    List<FixReplacementInfo> fixReplacementInfoList = Arrays.asList(fixReplacementInfo);
    DirectFixInput directFixInput = new DirectFixInput();
    directFixInput.fixReplacementInfos = fixReplacementInfoList;

    return directFixInput;
  }

  private void updateCommitMessage(String changeId, String newCommitMessage) throws Exception {
    gApi.changes().id(changeId).edit().create();
    gApi.changes().id(changeId).edit().modifyCommitMessage(newCommitMessage);
    PublishChangeEditInput publishInput = new PublishChangeEditInput();
    gApi.changes().id(changeId).edit().publish(publishInput);
  }

  private static Comment.Range createRange(
      int startLine, int startCharacter, int endLine, int endCharacter) {
    Comment.Range range = new Comment.Range();
    range.startLine = startLine;
    range.startCharacter = startCharacter;
    range.endLine = endLine;
    range.endCharacter = endCharacter;
    return range;
  }
}
