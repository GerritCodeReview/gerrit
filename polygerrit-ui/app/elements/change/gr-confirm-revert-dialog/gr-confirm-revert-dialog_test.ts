/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {fixture, html} from '@open-wc/testing-helpers';
import '../../../test/common-test-setup-karma';
import {createChange} from '../../../test/test-data-generators';
import {CommitId} from '../../../types/common';
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
    expect(element).shadowDom.to.equal(/* HTML */ `
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
    `);
  });

  test('no match', () => {
    assert.isNotOk(element._message);
    const alertStub = sinon.stub();
    element.addEventListener('show-alert', alertStub);
    element._populateRevertSingleChangeMessage(
      createChange(),
      'not a commitHash in sight',
      undefined
    );
    assert.isTrue(alertStub.calledOnce);
  });

  test('single line', () => {
    assert.isNotOk(element._message);
    element._populateRevertSingleChangeMessage(
      createChange(),
      'one line commit\n\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    const expected =
      'Revert "one line commit"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <INSERT REASONING HERE>\n';
    assert.equal(element._message, expected);
  });

  test('multi line', () => {
    assert.isNotOk(element._message);
    element._populateRevertSingleChangeMessage(
      createChange(),
      'many lines\ncommit\n\nmessage\n\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    const expected =
      'Revert "many lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <INSERT REASONING HERE>\n';
    assert.equal(element._message, expected);
  });

  test('issue above change id', () => {
    assert.isNotOk(element._message);
    element._populateRevertSingleChangeMessage(
      createChange(),
      'much lines\nvery\n\ncommit\n\nBug: Issue 42\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    const expected =
      'Revert "much lines"\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <INSERT REASONING HERE>\n';
    assert.equal(element._message, expected);
  });

  test('revert a revert', () => {
    assert.isNotOk(element._message);
    element._populateRevertSingleChangeMessage(
      createChange(),
      'Revert "one line commit"\n\nChange-Id: abcdefg\n',
      'abcd123' as CommitId
    );
    const expected =
      'Revert "Revert "one line commit""\n\n' +
      'This reverts commit abcd123.\n\n' +
      'Reason for revert: <INSERT REASONING HERE>\n';
    assert.equal(element._message, expected);
  });
});
