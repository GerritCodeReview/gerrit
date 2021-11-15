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

import '../../../test/common-test-setup-karma.js';
import './gr-messages-list.js';
import {createCommentApiMockWithTemplateElement} from '../../../test/mocks/comment-api.js';
import {TEST_ONLY} from './gr-messages-list.js';
import {MessageTag} from '../../../constants/constants.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';
import {stubRestApi} from '../../../test/test-utils.js';
import {updateStateComments} from '../../../services/comments/comments-model.js';

createCommentApiMockWithTemplateElement(
    'gr-messages-list-comment-mock-api', html`
     <gr-messages-list id="messagesList"></gr-messages-list>
`);

const basicFixture = fixtureFromTemplate(html`
<gr-messages-list-comment-mock-api>
  <gr-messages-list></gr-messages-list>
</gr-messages-list-comment-mock-api>
`);

const randomMessage = function(opt_params) {
  const params = opt_params || {};
  const author1 = {
    _account_id: 1115495,
    name: 'Andrew Bonventre',
    email: 'andybons@chromium.org',
  };
  return {
    id: params.id || Math.random().toString(),
    date: params.date || '2016-01-12 20:28:33.038000',
    message: params.message || Math.random().toString(),
    _revision_number: params._revision_number || 1,
    author: params.author || author1,
    tag: params.tag,
  };
};

function generateRandomMessages(count) {
  return new Array(count).fill()
      .map(() => randomMessage());
}

suite('gr-messages-list tests', () => {
  let element;
  let messages;

  let commentApiWrapper;

  const getMessages = function() {
    return element.root.querySelectorAll('gr-message');
  };

  const MESSAGE_ID_0 = '1234ccc949c6d482b061be6a28e10782abf0e7af';
  const MESSAGE_ID_1 = '8c19ccc949c6d482b061be6a28e10782abf0e7af';
  const MESSAGE_ID_2 = 'e7bfdbc842f6b6d8064bc68e0f52b673f40c0ca5';

  const author = {
    _account_id: 42,
    name: 'Marvin the Paranoid Android',
    email: 'marvin@sirius.org',
  };

  const createComment = function() {
    return {
      id: '1a2b3c4d',
      message: 'some random test text',
      change_message_id: '8a7b6c5d',
      updated: '2016-01-01 01:02:03.000000000',
      line: 1,
      patch_set: 1,
      author,
    };
  };

  const comments = {
    file1: [
      {
        ...createComment(),
        change_message_id: MESSAGE_ID_0,
        in_reply_to: '6505d749_f0bec0aa',
        author: {
          email: 'some@email.com',
          _account_id: 123,
        },
      },
      {
        ...createComment(),
        id: '2b3c4d5e',
        change_message_id: MESSAGE_ID_1,
        in_reply_to: 'c5912363_6b820105',
      },
      {
        ...createComment(),
        id: '2b3c4d5e',
        change_message_id: MESSAGE_ID_1,
        in_reply_to: '6505d749_f0bec0aa',
      },
      {
        ...createComment(),
        id: '34ed05d749_10ed44b2',
        change_message_id: MESSAGE_ID_2,
      },
    ],
    file2: [
      {
        ...createComment(),
        change_message_id: MESSAGE_ID_1,
        in_reply_to: 'c5912363_4b7d450a',
        id: '450a935e_4f260d25',
      },
    ],
  };

  suite('basic tests', () => {
    setup(() => {
      stubRestApi('getLoggedIn').returns(Promise.resolve(false));
      stubRestApi('getDiffComments').returns(Promise.resolve(comments));
      stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
      stubRestApi('getDiffDrafts').returns(Promise.resolve({}));

      messages = generateRandomMessages(3);
      // Element must be wrapped in an element with direct access to the
      // comment API.
      commentApiWrapper = basicFixture.instantiate();
      element = commentApiWrapper.$.messagesList;
      updateStateComments(comments);
      element.messages = messages;
      flush();
    });

    test('expand/collapse all', () => {
      let allMessageEls = getMessages();
      for (const message of allMessageEls) {
        message._expanded = false;
      }
      MockInteractions.tap(allMessageEls[1]);
      assert.isTrue(allMessageEls[1]._expanded);

      MockInteractions.tap(element.shadowRoot
          .querySelector('#collapse-messages'));
      allMessageEls = getMessages();
      for (const message of allMessageEls) {
        assert.isTrue(message._expanded);
      }

      MockInteractions.tap(element.shadowRoot
          .querySelector('#collapse-messages'));
      allMessageEls = getMessages();
      for (const message of allMessageEls) {
        assert.isFalse(message._expanded);
      }
    });

    test('expand/collapse from external keypress', () => {
      // Start with one expanded message. -> not all collapsed
      element.scrollToMessage(messages[1].id);
      assert.isFalse([...getMessages()].filter(m => m._expanded).length == 0);

      // Press 'z' -> all collapsed
      element.handleExpandCollapse(false);
      assert.isTrue([...getMessages()].filter(m => m._expanded).length == 0);

      // Press 'x' -> all expanded
      element.handleExpandCollapse(true);
      assert.isTrue([...getMessages()].filter(m => !m._expanded).length == 0);

      // Press 'z' -> all collapsed
      element.handleExpandCollapse(false);
      assert.isTrue([...getMessages()].filter(m => m._expanded).length == 0);
    });

    test('showAllActivity does not appear when all msgs are important', () => {
      assert.isOk(element.shadowRoot
          .querySelector('#showAllActivityToggleContainer'));
      assert.isNotOk(element.shadowRoot
          .querySelector('.showAllActivityToggle'));
    });

    test('scroll to message', () => {
      const allMessageEls = getMessages();
      for (const message of allMessageEls) {
        message.set('message.expanded', false);
      }

      const scrollToStub = sinon.stub(window, 'scrollTo');
      const highlightStub = sinon.stub(element, '_highlightEl');

      element.scrollToMessage('invalid');

      for (const message of allMessageEls) {
        assert.isFalse(message._expanded,
            'expected gr-message to not be expanded');
      }

      const messageID = messages[1].id;
      element.scrollToMessage(messageID);
      assert.isTrue(
          element.shadowRoot
              .querySelector('[data-message-id="' + messageID + '"]')
              ._expanded);

      assert.isTrue(scrollToStub.calledOnce);
      assert.isTrue(highlightStub.calledOnce);
    });

    test('scroll to message offscreen', () => {
      const scrollToStub = sinon.stub(window, 'scrollTo');
      const highlightStub = sinon.stub(element, '_highlightEl');
      element.messages = generateRandomMessages(25);
      flush();
      assert.isFalse(scrollToStub.called);
      assert.isFalse(highlightStub.called);

      const messageID = element.messages[1].id;
      element.scrollToMessage(messageID);
      assert.isTrue(scrollToStub.calledOnce);
      assert.isTrue(highlightStub.calledOnce);
      assert.isTrue(
          element.shadowRoot
              .querySelector('[data-message-id="' + messageID + '"]')
              ._expanded);
    });

    test('associating messages with comments', () => {
      const messages = [].concat(
          randomMessage(),
          {
            _index: 5,
            _revision_number: 4,
            message: 'Uploaded patch set 4.',
            date: '2016-09-28 13:36:33.000000000',
            author,
            id: '8c19ccc949c6d482b061be6a28e10782abf0e7af',
          },
          {
            _index: 6,
            _revision_number: 4,
            message: 'Patch Set 4:\n\n(6 comments)',
            date: '2016-09-28 13:36:33.000000000',
            author,
            id: 'e7bfdbc842f6b6d8064bc68e0f52b673f40c0ca5',
          }
      );
      element.messages = messages;
      flush();
      const messageElements = getMessages();
      assert.equal(messageElements.length, messages.length);
      assert.deepEqual(messageElements[1].message, messages[1]);
      assert.deepEqual(messageElements[2].message, messages[2]);
    });

    test('threads', () => {
      const messages = [
        {
          _index: 5,
          _revision_number: 4,
          message: 'Uploaded patch set 4.',
          date: '2016-09-28 13:36:33.000000000',
          author,
          id: '8c19ccc949c6d482b061be6a28e10782abf0e7af',
        },
      ];
      element.messages = messages;
      flush();
      const messageElements = getMessages();
      // threads
      assert.equal(
          messageElements[0].message.commentThreads.length,
          3);
      // first thread contains 1 comment
      assert.equal(
          messageElements[0].message.commentThreads[0].comments.length,
          1);
    });

    test('updateTag human message', () => {
      const m = randomMessage();
      assert.equal(TEST_ONLY.computeTag(m), undefined);
    });

    test('updateTag nothing to change', () => {
      const m = randomMessage();
      const tag = 'something-normal';
      m.tag = tag;
      assert.equal(TEST_ONLY.computeTag(m), tag);
    });

    test('updateTag TAG_NEW_WIP_PATCHSET', () => {
      const m = randomMessage();
      m.tag = MessageTag.TAG_NEW_WIP_PATCHSET;
      assert.equal(TEST_ONLY.computeTag(m), MessageTag.TAG_NEW_PATCHSET);
    });

    test('updateTag remove postfix', () => {
      const m = randomMessage();
      m.tag = 'something~withpostfix';
      assert.equal(TEST_ONLY.computeTag(m), 'something');
    });

    test('updateTag with robot comments', () => {
      const m = randomMessage();
      m.commentThreads = [{
        comments: [{
          robot_id: 'id314',
          change_message_id: m.id,
        }],
      }];
      assert.notEqual(TEST_ONLY.computeTag(m), undefined);
    });

    test('setRevisionNumber nothing to change', () => {
      const m1 = randomMessage();
      const m2 = randomMessage();
      assert.equal(TEST_ONLY.computeRevision(m1, [m1, m2]), 1);
      assert.equal(TEST_ONLY.computeRevision(m2, [m1, m2]), 1);
    });

    test('setRevisionNumber reviewer updates', () => {
      const m1 = randomMessage(
          {
            tag: MessageTag.TAG_REVIEWER_UPDATE,
            date: '2020-01-01 10:00:00.000000000',
          });
      m1._revision_number = undefined;
      const m2 = randomMessage(
          {
            date: '2020-01-02 10:00:00.000000000',
          });
      m2._revision_number = 1;
      const m3 = randomMessage(
          {
            tag: MessageTag.TAG_REVIEWER_UPDATE,
            date: '2020-01-03 10:00:00.000000000',
          });
      m3._revision_number = undefined;
      const m4 = randomMessage(
          {
            date: '2020-01-04 10:00:00.000000000',
          });
      m4._revision_number = 2;
      const m5 = randomMessage(
          {
            tag: MessageTag.TAG_REVIEWER_UPDATE,
            date: '2020-01-05 10:00:00.000000000',
          });
      m5._revision_number = undefined;
      const allMessages = [m1, m2, m3, m4, m5];
      assert.equal(TEST_ONLY.computeRevision(m1, allMessages), undefined);
      assert.equal(TEST_ONLY.computeRevision(m2, allMessages), 1);
      assert.equal(TEST_ONLY.computeRevision(m3, allMessages), 1);
      assert.equal(TEST_ONLY.computeRevision(m4, allMessages), 2);
      assert.equal(TEST_ONLY.computeRevision(m5, allMessages), 2);
    });

    test('isImportant human message', () => {
      const m = randomMessage();
      assert.isTrue(TEST_ONLY.computeIsImportant(m, []));
      assert.isTrue(TEST_ONLY.computeIsImportant(m, [m]));
    });

    test('isImportant even with a tag', () => {
      const m1 = randomMessage();
      const m2 = randomMessage({tag: 'autogenerated:gerrit1'});
      const m3 = randomMessage({tag: 'autogenerated:gerrit2'});
      assert.isTrue(TEST_ONLY.computeIsImportant(m2, []));
      assert.isTrue(TEST_ONLY.computeIsImportant(m1, [m1, m2, m3]));
      assert.isTrue(TEST_ONLY.computeIsImportant(m2, [m1, m2, m3]));
      assert.isTrue(TEST_ONLY.computeIsImportant(m3, [m1, m2, m3]));
    });

    test('isImportant filters same tag and older revision', () => {
      const m1 = randomMessage({tag: 'auto', _revision_number: 2});
      const m2 = randomMessage({tag: 'auto', _revision_number: 1});
      const m3 = randomMessage({tag: 'auto'});
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

    test('isImportant is evaluated after tag update', () => {
      const m1 = randomMessage(
          {tag: MessageTag.TAG_NEW_PATCHSET, _revision_number: 1});
      const m2 = randomMessage(
          {tag: MessageTag.TAG_NEW_WIP_PATCHSET, _revision_number: 2});
      element.messages = [m1, m2];
      flush();
      assert.isFalse(m1.isImportant);
      assert.isTrue(m2.isImportant);
    });

    test('messages without author do not throw', () => {
      const messages = [{
        _index: 5,
        _revision_number: 4,
        message: 'Uploaded patch set 4.',
        date: '2016-09-28 13:36:33.000000000',
        id: '8c19ccc949c6d482b061be6a28e10782abf0e7af',
      }];
      element.messages = messages;
      flush();
      const messageEls = getMessages();
      assert.equal(messageEls.length, 1);
      assert.equal(messageEls[0].message.message, messages[0].message);
    });
  });

  suite('gr-messages-list automate tests', () => {
    let element;
    let messages;

    let commentApiWrapper;

    setup(() => {
      stubRestApi('getLoggedIn').returns(Promise.resolve(false));
      stubRestApi('getDiffComments').returns(Promise.resolve({}));
      stubRestApi('getDiffRobotComments').returns(Promise.resolve({}));
      stubRestApi('getDiffDrafts').returns(Promise.resolve({}));

      messages = [
        randomMessage(),
        randomMessage({tag: 'auto', _revision_number: 2}),
        randomMessage({tag: 'auto', _revision_number: 3}),
      ];

      // Element must be wrapped in an element with direct access to the
      // comment API.
      commentApiWrapper = basicFixture.instantiate();
      element = commentApiWrapper.$.messagesList;
      element.messages = messages;
      flush();
    });

    test('hide autogenerated button is not hidden', () => {
      const toggle = element.root.querySelector('.showAllActivityToggle');
      assert.isOk(toggle);
    });

    test('one unimportant message is hidden initially', () => {
      const displayedMsgs = element.root.querySelectorAll('gr-message');
      assert.equal(displayedMsgs.length, 2);
    });

    test('unimportant messages hidden after toggle', () => {
      element._showAllActivity = true;
      const toggle = element.root.querySelector('.showAllActivityToggle');
      assert.isOk(toggle);
      MockInteractions.tap(toggle);
      flush();
      const displayedMsgs = element.root.querySelectorAll('gr-message');
      assert.equal(displayedMsgs.length, 2);
    });

    test('unimportant messages shown after toggle', () => {
      element._showAllActivity = false;
      const toggle = element.root.querySelector('.showAllActivityToggle');
      assert.isOk(toggle);
      MockInteractions.tap(toggle);
      flush();
      const displayedMsgs = element.root.querySelectorAll('gr-message');
      assert.equal(displayedMsgs.length, 3);
    });

    test('_computeLabelExtremes', () => {
      const computeSpy = sinon.spy(element, '_computeLabelExtremes');

      element.labels = null;
      assert.isTrue(computeSpy.calledOnce);
      assert.deepEqual(computeSpy.lastCall.returnValue, {});

      element.labels = {};
      assert.isTrue(computeSpy.calledTwice);
      assert.deepEqual(computeSpy.lastCall.returnValue, {});

      element.labels = {'my-label': {}};
      assert.isTrue(computeSpy.calledThrice);
      assert.deepEqual(computeSpy.lastCall.returnValue, {});

      element.labels = {'my-label': {values: {}}};
      assert.equal(computeSpy.callCount, 4);
      assert.deepEqual(computeSpy.lastCall.returnValue, {});

      element.labels = {'my-label': {values: {'-12': {}}}};
      assert.equal(computeSpy.callCount, 5);
      assert.deepEqual(computeSpy.lastCall.returnValue,
          {'my-label': {min: -12, max: -12}});

      element.labels = {
        'my-label': {values: {'-2': {}, '-1': {}, '0': {}, '+1': {}, '+2': {}}},
      };
      assert.equal(computeSpy.callCount, 6);
      assert.deepEqual(computeSpy.lastCall.returnValue,
          {'my-label': {min: -2, max: 2}});

      element.labels = {
        'my-label': {values: {'-12': {}}},
        'other-label': {values: {'-1': {}, ' 0': {}, '+1': {}}},
      };
      assert.equal(computeSpy.callCount, 7);
      assert.deepEqual(computeSpy.lastCall.returnValue, {
        'my-label': {min: -12, max: -12},
        'other-label': {min: -1, max: 1},
      });
    });
  });
});

