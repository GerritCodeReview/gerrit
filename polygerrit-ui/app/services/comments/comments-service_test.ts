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
  createFixSuggestionInfo,
} from '../../test/test-data-generators';
import {stubRestApi} from '../../test/test-utils';
import {
  NumericChangeId,
  RobotId,
  RobotRunId,
  Timestamp,
  UrlEncodedCommentId,
} from '../../types/common';
import {appContext} from '../app-context';
import {CommentsService} from './comments-service';

suite('change service tests', () => {
  let commentsService: CommentsService;

  test('loads logged-out', () => {
    const changeNum = 1234 as NumericChangeId;
    commentsService = new CommentsService(appContext.restApiService);
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    const diffCommentsSpy = stubRestApi('getDiffComments').returns(
      Promise.resolve({
        'foo.c': [
          {
            ...createComment(),
            id: '123' as UrlEncodedCommentId,
            message: 'Done',
            updated: '2017-02-08 16:40:49' as Timestamp,
          },
        ],
      })
    );
    const diffRobotCommentsSpy = stubRestApi('getDiffRobotComments').returns(
      Promise.resolve({
        'foo.c': [
          {
            ...createComment(),
            id: '321' as UrlEncodedCommentId,
            message: 'Done',
            updated: '2017-02-08 16:40:49' as Timestamp,
            robot_id: 'robot_1' as RobotId,
            robot_run_id: 'run_1' as RobotRunId,
            properties: {},
            fix_suggestions: [
              createFixSuggestionInfo('fix_1'),
              createFixSuggestionInfo('fix_2'),
            ],
          },
        ],
      })
    );
    const diffDraftsSpy = stubRestApi('getDiffDrafts').returns(
      Promise.resolve({})
    );

    commentsService.reloadAll(changeNum);
    assert.isTrue(diffCommentsSpy.calledWithExactly(changeNum));
    assert.isTrue(diffRobotCommentsSpy.calledWithExactly(changeNum));
    assert.isTrue(diffDraftsSpy.calledWithExactly(changeNum));
  });

  test('loads logged-in', () => {
    const changeNum = 1234 as NumericChangeId;

    stubRestApi('getLoggedIn').returns(Promise.resolve(true));
    const diffCommentsSpy = stubRestApi('getDiffComments').returns(
      Promise.resolve({
        'foo.c': [
          {
            ...createComment(),
            id: '123' as UrlEncodedCommentId,
            message: 'Done',
            updated: '2017-02-08 16:40:49' as Timestamp,
          },
        ],
      })
    );
    const diffRobotCommentsSpy = stubRestApi('getDiffRobotComments').returns(
      Promise.resolve({
        'foo.c': [
          {
            ...createComment(),
            id: '321' as UrlEncodedCommentId,
            message: 'Done',
            updated: '2017-02-08 16:40:49' as Timestamp,
            robot_id: 'robot_1' as RobotId,
            robot_run_id: 'run_1' as RobotRunId,
            properties: {},
            fix_suggestions: [
              createFixSuggestionInfo('fix_1'),
              createFixSuggestionInfo('fix_2'),
            ],
          },
        ],
      })
    );
    const diffDraftsSpy = stubRestApi('getDiffDrafts').returns(
      Promise.resolve({})
    );

    commentsService.reloadAll(changeNum);
    assert.isTrue(diffCommentsSpy.calledWithExactly(changeNum));
    assert.isTrue(diffRobotCommentsSpy.calledWithExactly(changeNum));
    assert.isTrue(diffDraftsSpy.calledWithExactly(changeNum));
  });
});
