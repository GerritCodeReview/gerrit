/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import * as sinon from 'sinon';
import {assert, fixture, html} from '@open-wc/testing';
import '../../../test/common-test-setup';
import {
  createChange,
  createParsedChange,
} from '../../../test/test-data-generators';
import {
  ChangeId,
  ChangeSubmissionId,
  CommitId,
  TopicName,
  ValidationOptionsInfo,
} from '../../../types/common';
import './gr-confirm-revert-dialog';
import {GrConfirmRevertDialog} from './gr-confirm-revert-dialog';
import {stubRestApi} from '../../../test/test-utils';
import {ParsedChangeInfo} from '../../../types/types';

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

  suite('populate tests', () => {
    let change: ParsedChangeInfo;

    setup(async () => {
      change = {
        ...createParsedChange(),
        submission_id: '5545' as ChangeSubmissionId,
        current_revision: 'abcd123' as CommitId,
      };
      stubRestApi('getChanges').returns(
        Promise.resolve([
          {
            ...createChange(),
            change_id: '12345678901234' as ChangeId,
            topic: 'T' as TopicName,
            subject: 'random',
          },
          {
            ...createChange(),
            change_id: '23456' as ChangeId,
            topic: 'T' as TopicName,
            subject: 'a'.repeat(100),
          },
        ])
      );
      stubRestApi('getValidationOptions').returns(
        Promise.resolve({
          validation_options: [{name: 'o1', description: 'option 1'}],
        } as ValidationOptionsInfo)
      );
    });

    test('validation options are fetched when populating revert dialog', async () => {
      await element.populate(change, 'commit message');
      assert.deepEqual(element.validationOptions, {
        validation_options: [{name: 'o1', description: 'option 1'}],
      });
    });
  });
});
