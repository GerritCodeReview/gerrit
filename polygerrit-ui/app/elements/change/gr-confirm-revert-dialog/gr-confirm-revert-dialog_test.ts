/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {fixture, html, assert} from '@open-wc/testing';
import '../../../test/common-test-setup';
import {createChange} from '../../../test/test-data-generators';
import {CommitId} from '../../../types/common';
import {EventType} from '../../../types/events';
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
              <iron-autogrow-textarea
                id="messageInput"
                class="message"
                aria-disabled="false"
              ></iron-autogrow-textarea>
            </gr-endpoint-decorator>
          </div>
        </gr-dialog>
      `
    );
  });

  test('no match', () => {
    assert.isNotOk(element.message);
    const alertStub = sinon.stub();
    element.addEventListener(EventType.SHOW_ALERT, alertStub);
    element.populateRevertSingleChangeMessage(
      createChange(),
      'not a commitHash in sight',
      undefined
    );
    assert.isTrue(alertStub.calledOnce);
  });

  test('single line', () => {
    assert.isNotOk(element.message);
    element.populateRevertSingleChangeMessage(
      createChange(),
      'one line commit\n\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    const expected =
      'Revert "one line commit"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <INSERT REASONING HERE>\n';
    assert.equal(element.message, expected);
  });

  test('multi line', () => {
    assert.isNotOk(element.message);
    element.populateRevertSingleChangeMessage(
      createChange(),
      'many lines\ncommit\n\nmessage\n\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    const expected =
      'Revert "many lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <INSERT REASONING HERE>\n';
    assert.equal(element.message, expected);
  });

  test('issue above change id', () => {
    assert.isNotOk(element.message);
    element.populateRevertSingleChangeMessage(
      createChange(),
      'much lines\nvery\n\ncommit\n\nBug: Issue 42\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    const expected =
      'Revert "much lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <INSERT REASONING HERE>\n';
    assert.equal(element.message, expected);
  });

  test('revert a revert', () => {
    assert.isNotOk(element.message);
    element.populateRevertSingleChangeMessage(
      createChange(),
      'Revert "one line commit"\n\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    const expected =
      'Revert "Revert "one line commit""\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <INSERT REASONING HERE>\n';
    assert.equal(element.message, expected);
  });
});
