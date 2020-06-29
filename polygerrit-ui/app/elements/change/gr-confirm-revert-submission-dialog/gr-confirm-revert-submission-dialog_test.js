/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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

import '../../../test/common-test-setup-karma.js';
import './gr-confirm-revert-submission-dialog.js';

const basicFixture = fixtureFromElement('gr-confirm-revert-submission-dialog');

suite('gr-confirm-revert-submission-dialog tests', () => {
  let element;

  setup(() => {
    element = basicFixture.instantiate();
  });

  test('no match', () => {
    assert.isNotOk(element.message);
    const alertStub = sinon.stub();
    element.addEventListener('show-alert', alertStub);
    element._populateRevertSubmissionMessage(
        'not a commitHash in sight', {}
    );
    assert.isTrue(alertStub.calledOnce);
  });

  test('single line', () => {
    assert.isNotOk(element.message);
    element._populateRevertSubmissionMessage(
        'one line commit\n\nChange-Id: abcdefg\n',
        {current_revision: 'abcd123', submission_id: '111'});
    const expected = 'Revert submission 111\n\n' +
      'Reason for revert: <INSERT REASONING HERE>\n';
    assert.equal(element.message, expected);
  });

  test('multi line', () => {
    assert.isNotOk(element.message);
    element._populateRevertSubmissionMessage(
        'many lines\ncommit\n\nmessage\n\nChange-Id: abcdefg\n',
        {current_revision: 'abcd123', submission_id: '111'});
    const expected = 'Revert submission 111\n\n' +
      'Reason for revert: <INSERT REASONING HERE>\n';
    assert.equal(element.message, expected);
  });

  test('issue above change id', () => {
    assert.isNotOk(element.message);
    element._populateRevertSubmissionMessage(
        'test \nvery\n\ncommit\n\nBug: Issue 42\nChange-Id: abcdefg\n',
        {current_revision: 'abcd123', submission_id: '111'});
    const expected = 'Revert submission 111\n\n' +
        'Reason for revert: <INSERT REASONING HERE>\n';
    assert.equal(element.message, expected);
  });

  test('revert a revert', () => {
    assert.isNotOk(element.message);
    element._populateRevertSubmissionMessage(
        'Revert "one line commit"\n\nChange-Id: abcdefg\n',
        {current_revision: 'abcd123', submission_id: '111'});
    const expected = 'Revert submission 111\n\n' +
      'Reason for revert: <INSERT REASONING HERE>\n';
    assert.equal(element.message, expected);
  });
});

