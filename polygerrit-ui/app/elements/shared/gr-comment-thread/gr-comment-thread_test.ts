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
import {sortComments} from '../../../utils/comment-util';
import {GrCommentThread} from './gr-comment-thread';
import {
  NumericChangeId,
  UrlEncodedCommentId,
  Timestamp,
  CommentInfo,
} from '../../../types/common';
import {stubRestApi} from '../../../test/test-utils';
import {createComment, createThread} from '../../../test/test-data-generators';

const basicFixture = fixtureFromElement('gr-comment-thread');

suite('gr-comment-thread tests', () => {
  suite('basic test', () => {
    let element: GrCommentThread;

    setup(async () => {
      stubRestApi('getLoggedIn').returns(Promise.resolve(false));
      element = basicFixture.instantiate();
      element.changeNum = 1 as NumericChangeId;
      await flush();
    });

    test('renders', async () => {
      element.thread = {
        ...createThread(),
        comments: [
          {
            ...createComment(),
            author: {name: 'Kermit'},
            id: 'the-root' as UrlEncodedCommentId,
            message: 'start the conversation',
            updated: '2021-11-01 10:11:12.000000000' as Timestamp,
          },
          {
            ...createComment(),
            author: {name: 'Ms Piggy'},
            id: 'the-reply' as UrlEncodedCommentId,
            message: 'keep it going',
            updated: '2021-11-02 10:11:12.000000000' as Timestamp,
            in_reply_to: 'the-root' as UrlEncodedCommentId,
          },
          {
            ...createComment(),
            author: {name: 'Kermit'},
            id: 'the-draft' as UrlEncodedCommentId,
            message: 'stop it',
            updated: '2021-11-03 10:11:12.000000000' as Timestamp,
            in_reply_to: 'the-reply' as UrlEncodedCommentId,
            __draft: true,
          },
        ],
      };
      await flush();
      expect(element).shadowDom.to.equal(`
        <dom-if style="display: none;"><template is="dom-if"></template></dom-if>
        <div id="container">
          <h3 class="assistive-tech-only">Draft Comment thread by Kermit</h3>
          <div class="comment-box" tabindex="0">
            <gr-comment></gr-comment>
            <gr-comment></gr-comment>
            <gr-comment></gr-comment>
            <dom-repeat as="comment" id="commentList" style="display: none;">
              <template is="dom-repeat"></template>
            </dom-repeat>
            <div hidden="true" id="commentInfoContainer">
              <span id="unresolvedLabel">Resolved</span>
              <div id="actions">
                <iron-icon
                  class="link-icon"
                  icon="gr-icons:link"
                  role="button"
                  tabindex="0"
                  title="Copy link to this comment"
                >
                </iron-icon>
                <gr-button
                  aria-disabled="false"
                  class="action reply"
                  id="replyBtn"
                  link=""
                  role="button"
                  tabindex="0"
                >
                  Reply
                </gr-button>
                <gr-button
                  aria-disabled="false"
                  class="action quote"
                  id="quoteBtn"
                  link=""
                  role="button"
                  tabindex="0"
                >
                  Quote
                </gr-button>
                <dom-if style="display: none;"><template is="dom-if"></template></dom-if>
              </div>
            </div>
          </div>
          <dom-if style="display: none;"><template is="dom-if"></template></dom-if>
        </div>
      `);
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
});
