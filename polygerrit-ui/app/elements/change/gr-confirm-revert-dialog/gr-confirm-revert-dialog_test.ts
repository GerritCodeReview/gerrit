/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import {assert, fixture, html} from '@open-wc/testing';
import '../../../test/common-test-setup';
import {createParsedChange} from '../../../test/test-data-generators';
import {ChangeSubmissionId, CommitId} from '../../../types/common';
import './gr-confirm-revert-dialog';
import {GrConfirmRevertDialog} from './gr-confirm-revert-dialog';

suite('gr-confirm-revert-dialog tests', () => {
  let element: GrConfirmRevertDialog;

  setup(async () => {
    element = await fixture(
      html`<gr-confirm-revert-dialog></gr-confirm-revert-dialog>`
    );
  });

  test('renders', () => {
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <gr-dialog role="dialog">
          <div class="header" slot="header">Revert Merged Change</div>
          <div class="main" slot="main">
            <div class="error" hidden="">
              <span> A reason is required </span>
            </div>
            <gr-endpoint-decorator name="confirm-revert-change">
              <label for="messageInput"> Revert Commit Message </label>
              <gr-autogrow-textarea
                id="messageInput"
                class="message"
              ></gr-autogrow-textarea>
            </gr-endpoint-decorator>
            <gr-validation-options></gr-validation-options>
          </div>
        </gr-dialog>
      `
    );
  });

  test('no match', () => {
    assert.isNotOk(element.message);
    const alertStub = sinon.stub();
    element.addEventListener('show-alert', alertStub);
    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'not a commitHash in sight',
      undefined
    );
    assert.isTrue(alertStub.calledOnce);
  });

  test('single line', () => {
    assert.isNotOk(element.message);
    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'one line commit\n\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    const expected =
      'Revert "one line commit"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n';
    assert.equal(element.message, expected);
  });

  test('multi line', () => {
    assert.isNotOk(element.message);
    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'many lines\ncommit\n\nmessage\n\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    const expected =
      'Revert "many lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n';
    assert.equal(element.message, expected);
  });

  test('populateRevertSingleChangeMessage parses Issues footer from commit message', () => {
    assert.isNotOk(element.message);
    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'much lines\nvery\n\ncommit\nIssue: 1234567\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    let expected =
      'Revert "much lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n\n' +
      'Issue: 1234567';
    assert.equal(element.message, expected);

    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'much lines\nvery\n\ncommit\nIssue= 1234567\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    expected =
      'Revert "much lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n\n' +
      'Issue= 1234567';
    assert.equal(element.message, expected);

    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'much lines\nvery\n\ncommit\nISSUE= 1234567\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    expected =
      'Revert "much lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n\n' +
      'ISSUE= 1234567';
    assert.equal(element.message, expected);

    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'much lines\nvery\n\ncommit\nISSUE: 1234567\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    expected =
      'Revert "much lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n\n' +
      'ISSUE: 1234567';
    assert.equal(element.message, expected);
  });

  test('populateRevertSingleChangeMessage does not parse Issue: from commit message body', () => {
    assert.isNotOk(element.message);
    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'much lines\n\nIssue: 1234567very\n\ncommit\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    const expected =
      'Revert "much lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n';
    assert.equal(element.message, expected);
  });

  test('populateRevertSingleChangeMessage parses Bug footer from commit message', () => {
    assert.isNotOk(element.message);
    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'much lines\nvery\n\ncommit\nBug: 1234567\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    let expected =
      'Revert "much lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n\n' +
      'Bug: 1234567';
    assert.equal(element.message, expected);

    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'much lines\nvery\n\ncommit\nBug= 1234567\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    expected =
      'Revert "much lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n\n' +
      'Bug= 1234567';
    assert.equal(element.message, expected);

    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'much lines\nvery\n\ncommit\nBUG= 1234567\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    expected =
      'Revert "much lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n\n' +
      'BUG= 1234567';
    assert.equal(element.message, expected);

    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'much lines\nvery\n\ncommit\nBUG: 1234567\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    expected =
      'Revert "much lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n\n' +
      'BUG: 1234567';
    assert.equal(element.message, expected);
  });

  test('populateRevertSingleChangeMessage does not parse Bug: from commit message body', () => {
    assert.isNotOk(element.message);
    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'much lines\n\nBug: 1234567very\n\ncommit\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    const expected =
      'Revert "much lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n';
    assert.equal(element.message, expected);
  });

  test('issue above change id', () => {
    assert.isNotOk(element.message);
    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'much lines\nvery\n\ncommit\n\nBug: Issue 42\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    const expected =
      'Revert "much lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n\n' +
      'Bug: Issue 42';
    assert.equal(element.message, expected);
  });

  test('revert a revert', () => {
    assert.isNotOk(element.message);
    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'Revert "one line commit"\n\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    const expected =
      'Revert^2 "one line commit"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n';
    assert.equal(element.message, expected);
  });

  test('revert a revert of a revert', () => {
    assert.isNotOk(element.message);
    element.populateRevertSingleChangeMessage(
      createParsedChange(),
      'Revert^2 "one line commit"\n\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    const expected =
      'Revert^3 "one line commit"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n';
    assert.equal(element.message, expected);
  });

  test('revert submission', () => {
    element.changesCount = 3;
    element.populateRevertSubmissionMessage(
      {
        ...createParsedChange(),
        submission_id: '5545' as ChangeSubmissionId,
        current_revision: 'abcd123' as CommitId,
      },
      'one line commit\n\nChange-Id: abcdefg\n'
    );

    const expected =
      'Revert submission 5545\n\n' +
      'Reason for revert: <MUST SPECIFY REASON HERE>\n\n' +
      'Reverted changes: /q/submissionid:5545\n';
    assert.equal(element.message, expected);
  });
});
