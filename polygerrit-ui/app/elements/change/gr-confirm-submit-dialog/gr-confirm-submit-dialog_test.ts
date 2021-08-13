/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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

import '../../../test/common-test-setup-karma';
import {createChange, createRevision} from '../../../test/test-data-generators';
import {queryAndAssert} from '../../../test/test-utils';
import {PatchSetNum} from '../../../types/common';
import {GrConfirmSubmitDialog} from './gr-confirm-submit-dialog';

const basicFixture = fixtureFromElement('gr-confirm-submit-dialog');

suite('gr-confirm-submit-dialog tests', () => {
  let element: GrConfirmSubmitDialog;

  setup(() => {
    element = basicFixture.instantiate();
    element._initialised = true;
  });

  test('display', async () => {
    element.action = {label: 'my-label'};
    element.change = {
      ...createChange(),
      subject: 'my-subject',
      revisions: {},
    };
    await flush();
    const header = queryAndAssert(element, '.header');
    assert.equal(header.textContent!.trim(), 'my-label');

    const message = queryAndAssert(element, '.main p');
    assert.isNotEmpty(message.textContent);
    assert.notEqual(message.textContent!.indexOf('my-subject'), -1);
  });

  test('_computeUnresolvedCommentsWarning', () => {
    const change = {...createChange(), unresolved_comment_count: 1};
    assert.equal(
      element._computeUnresolvedCommentsWarning(change),
      'Heads Up! 1 unresolved comment.'
    );

    const change2 = {...createChange(), unresolved_comment_count: 2};
    assert.equal(
      element._computeUnresolvedCommentsWarning(change2),
      'Heads Up! 2 unresolved comments.'
    );
  });

  test('_computeHasChangeEdit', () => {
    const change = {
      ...createChange(),
      revisions: {
        d442ff05d6c4f2a3af0eeca1f67374b39f9dc3d8: {
          ...createRevision(),
          _number: 'edit' as PatchSetNum,
        },
      },
      unresolved_comment_count: 0,
    };

    assert.isTrue(element._computeHasChangeEdit(change));

    const change2 = {
      ...createChange(),
      revisions: {
        d442ff05d6c4f2a3af0eeca1f67374b39f9dc3d8: {
          ...createRevision(),
          _number: 2 as PatchSetNum,
        },
      },
    };
    assert.isFalse(element._computeHasChangeEdit(change2));
  });
});
