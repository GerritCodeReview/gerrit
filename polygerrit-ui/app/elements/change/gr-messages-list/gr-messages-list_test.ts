/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import './gr-messages-list';
import {CombinedMessage, GrMessagesList, TEST_ONLY} from './gr-messages-list';
import {MessageTag} from '../../../constants/constants';
import {
  query,
  queryAll,
  queryAndAssert,
  stubRestApi,
} from '../../../test/test-utils';
import {GrMessage} from '../gr-message/gr-message';
import {
  AccountId,
  ChangeMessageId,
  ChangeMessageInfo,
  EmailAddress,
  LabelNameToInfoMap,
  NumericChangeId,
  PatchSetNum,
  ReviewInputTag,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import * as MockInteractions from '@polymer/iron-test-helpers/mock-interactions';
import {assertIsDefined} from '../../../utils/common-util';
import {html} from 'lit';
import {fixture} from '@open-wc/testing-helpers';

const author = {
  _account_id: 42 as AccountId,
  name: 'Marvin the Paranoid Android',
  email: 'marvin@sirius.org' as EmailAddress,
};

const createComment = function () {
  return {
    id: '1a2b3c4d' as UrlEncodedCommentId,
    message: 'some random test text',
    change_message_id: '8a7b6c5d',
    updated: '2016-01-01 01:02:03.000000000' as Timestamp,
    line: 1,
    patch_set: 1 as PatchSetNum,
    author,
  };
};

const randomMessage = function (opt_params?: ChangeMessageInfo) {
  const params = opt_params || ({} as ChangeMessageInfo);
  const author1 = {
    _account_id: 1115495 as AccountId,
    name: 'Andrew Bonventre',
    email: 'andybons@chromium.org' as EmailAddress,
  };
  return {
    id: (params.id || Math.random().toString()) as ChangeMessageId,
    date: (params.date || '2016-01-12 20:28:33.038000') as Timestamp,
    message: params.message || Math.random().toString(),
    _revision_number: (params._revision_number || 1) as PatchSetNum,
    author: params.author || author1,
    tag: params.tag,
  };
};

function generateRandomMessages(count: number) {
  return new Array(count)
    .fill(undefined)
    .map(() => randomMessage()) as ChangeMessageInfo[];
}

suite('gr-messages-list tests', () => {
  let element: GrMessagesList;
  let messages: ChangeMessageInfo[];

  const getMessages = function () {
    return queryAll<GrMessage>(element, 'gr-message');
  };

  const MESSAGE_ID_0 = '1234ccc949c6d482b061be6a28e10782abf0e7af';
  const MESSAGE_ID_1 = '8c19ccc949c6d482b061be6a28e10782abf0e7af';
  const MESSAGE_ID_2 = 'e7bfdbc842f6b6d8064bc68e0f52b673f40c0ca5';

  const comments = {
    file1: [
      {
        ...createComment(),
        change_message_id: MESSAGE_ID_0,
        in_reply_to: '6505d749_f0bec0aa' as UrlEncodedCommentId,
        author: {
          email: 'some@email.com' as EmailAddress,
          _account_id: 123 as AccountId,
        },
      },
      {
        ...createComment(),
        id: '2b3c4d5e' as UrlEncodedCommentId,
        change_message_id: MESSAGE_ID_1,
        in_reply_to: 'c5912363_6b820105' as UrlEncodedCommentId,
      },
      {
        ...createComment(),
        id: '2b3c4d5e' as UrlEncodedCommentId,
        change_message_id: MESSAGE_ID_1,
        in_reply_to: '6505d749_f0bec0aa' as UrlEncodedCommentId,
      },
      {
        ...createComment(),
        id: '34ed05d749_10ed44b2' as UrlEncodedCommentId,
        change_message_id: MESSAGE_ID_2,
      },
    ],
    file2: [
      {
        ...createComment(),
        change_message_id: MESSAGE_ID_1,
        in_reply_to: 'c5912363_4b7d450a' as UrlEncodedCommentId,
        id: '450a935e_4f260d25' as UrlEncodedCommentId,
      },
    ],
  };

  suite('basic tests', () => {
    setup(async () => {
      stubRestApi('getLoggedIn').returns(Promise.resolve(false));
      stubRestApi('getDiffComments').returns(Promise.resolve(comments));
      stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
      stubRestApi('getDiffDrafts').returns(Promise.resolve({}));

      messages = generateRandomMessages(3);
      element = await fixture<GrMessagesList>(
        html`<gr-messages-list></gr-messages-list>`
      );
      await element.getCommentsModel().reloadComments(0 as NumericChangeId);
      element.messages = messages;
      await element.updateComplete;
    });

    test('expand/collapse all', async () => {
      let allMessageEls = getMessages();
      for (const message of allMessageEls) {
        assertIsDefined(message.message);
        message.message = {...message.message, expanded: false};
        await message.updateComplete;
      }
      MockInteractions.tap(allMessageEls[1]);
      await element.updateComplete;
      assert.isTrue(allMessageEls[1].message?.expanded);

      MockInteractions.tap(queryAndAssert(element, '#collapse-messages'));
      await element.updateComplete;
      allMessageEls = getMessages();
      for (const message of allMessageEls) {
        assert.isTrue(message.message?.expanded);
      }

      MockInteractions.tap(queryAndAssert(element, '#collapse-messages'));
      await element.updateComplete;
      allMessageEls = getMessages();
      for (const message of allMessageEls) {
        assert.isFalse(message.message?.expanded);
      }
    });

    test('expand/collapse from external keypress', () => {
      // Start with one expanded message. -> not all collapsed
      element.scrollToMessage(messages[1].id);
      assert.isFalse(
        [...getMessages()].filter(m => m.message?.expanded).length === 0
      );

      // Press 'z' -> all collapsed
      element.handleExpandCollapse(false);
      assert.isTrue(
        [...getMessages()].filter(m => m.message?.expanded).length === 0
      );

      // Press 'x' -> all expanded
      element.handleExpandCollapse(true);
      assert.isTrue(
        [...getMessages()].filter(m => !m.message?.expanded).length === 0
      );

      // Press 'z' -> all collapsed
      element.handleExpandCollapse(false);
      assert.isTrue(
        [...getMessages()].filter(m => m.message?.expanded).length === 0
      );
    });

    test('showAllActivity does not appear when all msgs are important', () => {
      assert.isOk(query(element, '#showAllActivityToggleContainer'));
      assert.isNotOk(query(element, '.showAllActivityToggle'));
    });

    test('scroll to message', async () => {
      const allMessageEls = getMessages();
      for (const message of allMessageEls) {
        assertIsDefined(message.message);
        message.message = {...message.message, expanded: false};
      }

      const scrollToStub = sinon.stub(window, 'scrollTo');
      const highlightStub = sinon.stub(element, 'highlightEl');

      await element.scrollToMessage('invalid');

      for (const message of allMessageEls) {
        assertIsDefined(message.message);
        assert.isFalse(
          message.message.expanded,
          'expected gr-message to not be expanded'
        );
      }

      const messageID = messages[1].id;
      await element.scrollToMessage(messageID);
      assert.isTrue(
        queryAndAssert<GrMessage>(element, `[data-message-id="${messageID}"]`)
          .message?.expanded
      );

      assert.isTrue(scrollToStub.calledOnce);
      assert.isTrue(highlightStub.calledOnce);
    });

    test('scroll to message offscreen', async () => {
      const scrollToStub = sinon.stub(window, 'scrollTo');
      const highlightStub = sinon.stub(element, 'highlightEl');
      element.messages = generateRandomMessages(25);
      await element.updateComplete;
      assert.isFalse(scrollToStub.called);
      assert.isFalse(highlightStub.called);

      const messageID = element.messages[1].id;
      await element.scrollToMessage(messageID);
      assert.isTrue(scrollToStub.calledOnce);
      assert.isTrue(highlightStub.calledOnce);
      assert.isTrue(
        queryAndAssert<GrMessage>(element, `[data-message-id="${messageID}"]`)
          .message?.expanded
      );
    });

    test('associating messages with comments', async () => {
      // Have to type as any otherwise fails with
      // Argument of type 'ChangeMessageInfo[]' is not assignable to
      // parameter of type 'ConcatArray<never>'.
      const messages = ([] as any).concat(
        randomMessage(),
        {
          _index: 5,
          _revision_number: 4 as PatchSetNum,
          message: 'Uploaded patch set 4.',
          date: '2016-09-28 13:36:33.000000000' as Timestamp,
          author,
          id: '8c19ccc949c6d482b061be6a28e10782abf0e7af' as ChangeMessageId,
        } as CombinedMessage,
        {
          _index: 6,
          _revision_number: 4 as PatchSetNum,
          message: 'Patch Set 4:\n\n(6 comments)',
          date: '2016-09-28 13:36:33.000000000' as Timestamp,
          author,
          id: 'e7bfdbc842f6b6d8064bc68e0f52b673f40c0ca5' as ChangeMessageId,
        } as CombinedMessage
      );
      element.messages = messages;
      await element.updateComplete;
      const messageElements = getMessages();
      assert.equal(messageElements.length, messages.length);
      assert.deepEqual(messageElements[1].message, messages[1]);
      assert.deepEqual(messageElements[2].message, messages[2]);
    });

    test('threads', async () => {
      const messages = [
        {
          _index: 5,
          _revision_number: 4 as PatchSetNum,
          message: 'Uploaded patch set 4.',
          date: '2016-09-28 13:36:33.000000000' as Timestamp,
          author,
          id: '8c19ccc949c6d482b061be6a28e10782abf0e7af' as ChangeMessageId,
        },
      ];
      element.messages = messages;
      await element.updateComplete;
      const messageElements = getMessages();
      // threads
      assert.equal(messageElements[0].message!.commentThreads.length, 3);
      // first thread contains 1 comment
      assert.equal(
        messageElements[0].message!.commentThreads[0].comments.length,
        1
      );
    });

    test('updateTag human message', () => {
      const m = randomMessage();
      assert.equal(TEST_ONLY.computeTag(m), undefined);
    });

    test('updateTag nothing to change', () => {
      const m = randomMessage();
      const tag = 'something-normal' as ReviewInputTag;
      m.tag = tag;
      assert.equal(TEST_ONLY.computeTag(m), tag);
    });

    test('updateTag TAG_NEW_WIP_PATCHSET', () => {
      const m = randomMessage();
      m.tag = MessageTag.TAG_NEW_WIP_PATCHSET as ReviewInputTag;
      assert.equal(TEST_ONLY.computeTag(m), MessageTag.TAG_NEW_PATCHSET);
    });

    test('updateTag remove postfix', () => {
      const m = randomMessage();
      m.tag = 'something~withpostfix' as ReviewInputTag;
      assert.equal(TEST_ONLY.computeTag(m), 'something');
    });

    test('updateTag with robot comments', () => {
      const m = randomMessage();
      (m as any).commentThreads = [
        {
          comments: [
            {
              robot_id: 'id314',
              change_message_id: m.id,
            },
          ],
        },
      ];
      assert.notEqual(TEST_ONLY.computeTag(m), undefined);
    });

    test('setRevisionNumber nothing to change', () => {
      const m1 = randomMessage();
      const m2 = randomMessage();
      assert.equal(TEST_ONLY.computeRevision(m1, [m1, m2]), 1 as PatchSetNum);
      assert.equal(TEST_ONLY.computeRevision(m2, [m1, m2]), 1 as PatchSetNum);
    });

    test('setRevisionNumber reviewer updates', () => {
      const m1 = randomMessage({
        ...randomMessage(),
        tag: MessageTag.TAG_REVIEWER_UPDATE as ReviewInputTag,
        date: '2020-01-01 10:00:00.000000000' as Timestamp,
      });
      m1._revision_number = 0 as PatchSetNum;
      const m2 = randomMessage({
        ...randomMessage(),
        date: '2020-01-02 10:00:00.000000000' as Timestamp,
      });
      m2._revision_number = 1 as PatchSetNum;
      const m3 = randomMessage({
        ...randomMessage(),
        tag: MessageTag.TAG_REVIEWER_UPDATE as ReviewInputTag,
        date: '2020-01-03 10:00:00.000000000' as Timestamp,
      });
      m3._revision_number = 0 as PatchSetNum;
      const m4 = randomMessage({
        ...randomMessage(),
        date: '2020-01-04 10:00:00.000000000' as Timestamp,
      });
      m4._revision_number = 2 as PatchSetNum;
      const m5 = randomMessage({
        ...randomMessage(),
        tag: MessageTag.TAG_REVIEWER_UPDATE as ReviewInputTag,
        date: '2020-01-05 10:00:00.000000000' as Timestamp,
      });
      m5._revision_number = 0 as PatchSetNum;
      const allMessages = [m1, m2, m3, m4, m5];
      assert.equal(TEST_ONLY.computeRevision(m1, allMessages), undefined);
      assert.equal(
        TEST_ONLY.computeRevision(m2, allMessages),
        1 as PatchSetNum
      );
      assert.equal(
        TEST_ONLY.computeRevision(m3, allMessages),
        1 as PatchSetNum
      );
      assert.equal(
        TEST_ONLY.computeRevision(m4, allMessages),
        2 as PatchSetNum
      );
      assert.equal(
        TEST_ONLY.computeRevision(m5, allMessages),
        2 as PatchSetNum
      );
    });

    test('isImportant human message', () => {
      const m = randomMessage();
      assert.isTrue(TEST_ONLY.computeIsImportant(m, []));
      assert.isTrue(TEST_ONLY.computeIsImportant(m, [m]));
    });

    test('isImportant even with a tag', () => {
      const m1 = randomMessage();
      const m2 = randomMessage({
        ...randomMessage(),
        tag: 'autogenerated:gerrit1' as ReviewInputTag,
      });
      const m3 = randomMessage({
        ...randomMessage(),
        tag: 'autogenerated:gerrit2' as ReviewInputTag,
      });
      assert.isTrue(TEST_ONLY.computeIsImportant(m2, []));
      assert.isTrue(TEST_ONLY.computeIsImportant(m1, [m1, m2, m3]));
      assert.isTrue(TEST_ONLY.computeIsImportant(m2, [m1, m2, m3]));
      assert.isTrue(TEST_ONLY.computeIsImportant(m3, [m1, m2, m3]));
    });

    test('isImportant filters same tag and older revision', () => {
      const m1 = randomMessage({
        ...randomMessage(),
        tag: 'auto' as ReviewInputTag,
        _revision_number: 2 as PatchSetNum,
      });
      const m2 = randomMessage({
        ...randomMessage(),
        tag: 'auto' as ReviewInputTag,
        _revision_number: 1 as PatchSetNum,
      });
      const m3 = randomMessage({
        ...randomMessage(),
        tag: 'auto' as ReviewInputTag,
      });
      assert.isTrue(TEST_ONLY.computeIsImportant(m1, [m1]));
      assert.isTrue(TEST_ONLY.computeIsImportant(m2, [m2]));
      assert.isTrue(TEST_ONLY.computeIsImportant(m1, [m1, m2]));
      assert.isFalse(TEST_ONLY.computeIsImportant(m2, [m1, m2]));
      assert.isTrue(TEST_ONLY.computeIsImportant(m1, [m1, m3]));
      assert.isFalse(TEST_ONLY.computeIsImportant(m3, [m1, m3]));
      assert.isTrue(TEST_ONLY.computeIsImportant(m1, [m1, m2, m3]));
      assert.isFalse(TEST_ONLY.computeIsImportant(m2, [m1, m2, m3]));
      assert.isFalse(TEST_ONLY.computeIsImportant(m3, [m1, m2, m3]));
    });

    test('isImportant is evaluated after tag update', async () => {
      const m1 = randomMessage({
        ...randomMessage(),
        tag: MessageTag.TAG_NEW_PATCHSET as ReviewInputTag,
        _revision_number: 1 as PatchSetNum,
      });
      const m2 = randomMessage({
        ...randomMessage(),
        tag: MessageTag.TAG_NEW_WIP_PATCHSET as ReviewInputTag,
        _revision_number: 2 as PatchSetNum,
      });
      element.messages = [m1, m2];
      await element.updateComplete;
      assert.isFalse((m1 as CombinedMessage).isImportant);
      assert.isTrue((m2 as CombinedMessage).isImportant);
    });

    test('messages without author do not throw', async () => {
      const messages = [
        {
          _index: 5,
          _revision_number: 4 as PatchSetNum,
          message: 'Uploaded patch set 4.',
          date: '2016-09-28 13:36:33.000000000' as Timestamp,
          id: '8c19ccc949c6d482b061be6a28e10782abf0e7af' as ChangeMessageId,
        },
      ];
      element.messages = messages;
      await element.updateComplete;
      const messageEls = getMessages();
      assert.equal(messageEls.length, 1);
      assert.equal(messageEls[0].message!.message, messages[0].message);
    });
  });

  suite('gr-messages-list automate tests', () => {
    let element: GrMessagesList;
    let messages: ChangeMessageInfo[];

    setup(async () => {
      stubRestApi('getLoggedIn').returns(Promise.resolve(false));
      stubRestApi('getDiffComments').returns(Promise.resolve({}));
      stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
      stubRestApi('getDiffDrafts').returns(Promise.resolve({}));

      messages = [
        randomMessage(),
        randomMessage({
          ...randomMessage(),
          tag: 'auto' as ReviewInputTag,
          _revision_number: 2 as PatchSetNum,
        }),
        randomMessage({
          ...randomMessage(),
          tag: 'auto' as ReviewInputTag,
          _revision_number: 3 as PatchSetNum,
        }),
      ];

      element = await fixture<GrMessagesList>(
        html`<gr-messages-list></gr-messages-list>`
      );
      element.messages = messages;
      await element.updateComplete;
    });

    test('hide autogenerated button is not hidden', () => {
      const toggle = queryAndAssert(element, '.showAllActivityToggle');
      assert.isOk(toggle);
    });

    test('one unimportant message is hidden initially', () => {
      const displayedMsgs = queryAll<GrMessage>(element, 'gr-message');
      assert.equal(displayedMsgs.length, 2);
    });

    test('unimportant messages hidden after toggle', async () => {
      element.showAllActivity = true;
      await element.updateComplete;
      const toggle = queryAndAssert(element, '.showAllActivityToggle');
      assert.isOk(toggle);
      MockInteractions.tap(toggle);
      await element.updateComplete;
      const displayedMsgs = queryAll<GrMessage>(element, 'gr-message');
      assert.equal(displayedMsgs.length, 2);
    });

    test('unimportant messages shown after toggle', async () => {
      element.showAllActivity = false;
      await element.updateComplete;
      const toggle = queryAndAssert(element, '.showAllActivityToggle');
      assert.isOk(toggle);
      MockInteractions.tap(toggle);
      await element.updateComplete;
      const displayedMsgs = queryAll<GrMessage>(element, 'gr-message');
      assert.equal(displayedMsgs.length, 3);
    });

    test('_computeLabelExtremes', () => {
      // Have to type as any to be able to use null.
      element.labels = null as any;
      assert.deepEqual(element.computeLabelExtremes(), {});

      element.labels = {};
      assert.deepEqual(element.computeLabelExtremes(), {});

      element.labels = {'my-label': {}};
      assert.deepEqual(element.computeLabelExtremes(), {});

      element.labels = {'my-label': {values: {}}};
      assert.deepEqual(element.computeLabelExtremes(), {});

      element.labels = {
        'my-label': {values: {'-12': {}}},
      } as LabelNameToInfoMap;
      assert.deepEqual(element.computeLabelExtremes(), {
        'my-label': {min: -12, max: -12},
      });

      element.labels = {
        'my-label': {values: {'-2': {}, '-1': {}, '0': {}, '+1': {}, '+2': {}}},
      } as LabelNameToInfoMap;
      assert.deepEqual(element.computeLabelExtremes(), {
        'my-label': {min: -2, max: 2},
      });

      element.labels = {
        'my-label': {values: {'-12': {}}},
        'other-label': {values: {'-1': {}, ' 0': {}, '+1': {}}},
      } as LabelNameToInfoMap;
      assert.deepEqual(element.computeLabelExtremes(), {
        'my-label': {min: -12, max: -12},
        'other-label': {min: -1, max: 1},
      });
    });
  });
});
