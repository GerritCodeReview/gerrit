/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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
import './gr-comment-thread';
import {
  sortComments,
} from '../../../utils/comment-util';
import {GrCommentThread} from './gr-comment-thread';
import {
  NumericChangeId,
  UrlEncodedCommentId,
  Timestamp,
  CommentInfo,
} from '../../../types/common';
import {html} from '@polymer/polymer/lib/utils/html-tag';
import {
  stubRestApi
} from '../../../test/test-utils';

const basicFixture = fixtureFromElement('gr-comment-thread');

suite('gr-comment-thread tests', () => {
    let element: GrCommentThread;

    setup(() => {
      stubRestApi('getLoggedIn').returns(Promise.resolve(false));
      element = basicFixture.instantiate();
      element.changeNum = 1 as NumericChangeId;
      flush();
    });

    test('renders without patchNum and changeNum', async () => {
      const fixture = fixtureFromTemplate(
        html`<gr-comment-thread show-file-path="" path="path/to/file"></gr-change-metadata>`
      );
      fixture.instantiate();
      await flush();
    });

    test('comments are sorted correctly', () => {
      const comments: CommentInfo[] = [
        {
          id: 'jacks_confession' as UrlEncodedCommentId,
          in_reply_to: 'sallys_confession' as UrlEncodedCommentId,
          message: 'i like you, too',
          updated: '2015-12-25 15:00:20.396000000' as Timestamp,
        },
        {
          id: 'sallys_confession' as UrlEncodedCommentId,
          message: 'i like you, jack',
          updated: '2015-12-24 15:00:20.396000000' as Timestamp,
        },
        {
          id: 'sally_to_dr_finklestein' as UrlEncodedCommentId,
          message: 'i’m running away',
          updated: '2015-10-31 09:00:20.396000000' as Timestamp,
        },
        {
          id: 'sallys_defiance' as UrlEncodedCommentId,
          in_reply_to: 'sally_to_dr_finklestein' as UrlEncodedCommentId,
          message: 'i will poison you so i can get away',
          updated: '2015-10-31 15:00:20.396000000' as Timestamp,
        },
        {
          id: 'dr_finklesteins_response' as UrlEncodedCommentId,
          in_reply_to: 'sally_to_dr_finklestein' as UrlEncodedCommentId,
          message: 'no i will pull a thread and your arm will fall off',
          updated: '2015-10-31 11:00:20.396000000' as Timestamp,
        },
        {
          id: 'sallys_mission' as UrlEncodedCommentId,
          message: 'i have to find santa',
          updated: '2015-12-24 15:00:20.396000000' as Timestamp,
        },
      ];
      const results = sortComments(comments);
      assert.deepEqual(results, [
        {
          id: 'sally_to_dr_finklestein' as UrlEncodedCommentId,
          message: 'i’m running away',
          updated: '2015-10-31 09:00:20.396000000' as Timestamp,
        },
        {
          id: 'dr_finklesteins_response' as UrlEncodedCommentId,
          in_reply_to: 'sally_to_dr_finklestein' as UrlEncodedCommentId,
          message: 'no i will pull a thread and your arm will fall off',
          updated: '2015-10-31 11:00:20.396000000' as Timestamp,
        },
        {
          id: 'sallys_defiance' as UrlEncodedCommentId,
          in_reply_to: 'sally_to_dr_finklestein' as UrlEncodedCommentId,
          message: 'i will poison you so i can get away',
          updated: '2015-10-31 15:00:20.396000000' as Timestamp,
        },
        {
          id: 'sallys_confession' as UrlEncodedCommentId,
          message: 'i like you, jack',
          updated: '2015-12-24 15:00:20.396000000' as Timestamp,
        },
        {
          id: 'sallys_mission' as UrlEncodedCommentId,
          message: 'i have to find santa',
          updated: '2015-12-24 15:00:20.396000000' as Timestamp,
        },
        {
          id: 'jacks_confession' as UrlEncodedCommentId,
          in_reply_to: 'sallys_confession' as UrlEncodedCommentId,
          message: 'i like you, too',
          updated: '2015-12-25 15:00:20.396000000' as Timestamp,
        },
      ]);
    });
});
