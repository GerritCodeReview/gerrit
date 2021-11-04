/**
 * @license
 * Copyright (C) 2021 The Android Open Source Project
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

import '../../test/common-test-setup-karma';
import {
  createComment,
  createParsedChange,
  TEST_NUMERIC_CHANGE_ID,
} from '../../test/test-data-generators';
import {stubRestApi, waitUntil, waitUntilCalled} from '../../test/test-utils';
import {appContext} from '../app-context';
import {CommentsService} from './comments-service';
import {updateState as updateChangeState} from '../change/change-model';
import {
  GerritView,
  updateState as updateRouterState,
} from '../router/router-model';
import {comments$, portedComments$} from './comments-model';
import {PathToCommentsInfoMap} from '../../types/common';

suite('change service tests', () => {
  test('loads comments', async () => {
    new CommentsService(appContext.restApiService);
    const diffCommentsSpy = stubRestApi('getDiffComments').returns(
      Promise.resolve({'foo.c': [createComment()]})
    );
    const diffRobotCommentsSpy = stubRestApi('getDiffRobotComments').returns(
      Promise.resolve({})
    );
    const diffDraftsSpy = stubRestApi('getDiffDrafts').returns(
      Promise.resolve({})
    );
    const portedCommentsSpy = stubRestApi('getPortedComments').returns(
      Promise.resolve({'foo.c': [createComment()]})
    );
    const portedDraftsSpy = stubRestApi('getPortedDrafts').returns(
      Promise.resolve({})
    );
    let comments: PathToCommentsInfoMap = {};
    comments$.subscribe(c => (comments = c));
    let portedComments: PathToCommentsInfoMap = {};
    portedComments$.subscribe(c => (portedComments = c));

    updateRouterState(GerritView.CHANGE, TEST_NUMERIC_CHANGE_ID);
    updateChangeState(createParsedChange());

    await waitUntilCalled(diffCommentsSpy, 'diffCommentsSpy');
    await waitUntilCalled(diffRobotCommentsSpy, 'diffRobotCommentsSpy');
    await waitUntilCalled(diffDraftsSpy, 'diffDraftsSpy');
    await waitUntilCalled(portedCommentsSpy, 'portedCommentsSpy');
    await waitUntilCalled(portedDraftsSpy, 'portedDraftsSpy');
    await waitUntil(
      () => Object.keys(comments).length > 0,
      'comment in model not set'
    );
    await waitUntil(
      () => Object.keys(portedComments).length > 0,
      'ported comment in model not set'
    );

    assert.equal(comments['foo.c'].length, 1);
    assert.equal(comments['foo.c'][0].id, '12345');
    assert.equal(portedComments['foo.c'].length, 1);
    assert.equal(portedComments['foo.c'][0].id, '12345');
  });
});
