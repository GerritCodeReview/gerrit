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
import './gr-comment';
import {html} from '@polymer/polymer/lib/utils/html-tag';
import {GrComment, __testOnly_UNSAVED_MESSAGE} from './gr-comment';
import {SpecialFilePath, CommentSide} from '../../../constants/constants';
import {
  queryAndAssert,
  stubRestApi,
  stubStorage,
  spyStorage,
  query,
} from '../../../test/test-utils';
import {
  AccountId,
  EmailAddress,
  FixId,
  NumericChangeId,
  PatchSetNum,
  RobotId,
  RobotRunId,
  Timestamp,
  UrlEncodedCommentId,
} from '../../../types/common';
import {
  pressAndReleaseKeyOn,
  tap,
} from '@polymer/iron-test-helpers/mock-interactions';
import {
  createComment,
  createFixSuggestionInfo,
} from '../../../test/test-data-generators';
import {Timer} from '../../../services/gr-reporting/gr-reporting';
import {SinonStubbedMember} from 'sinon';
import {CreateFixCommentEvent} from '../../../types/events';
import {DraftInfo, UIRobot} from '../../../utils/comment-util';
import {MockTimer} from '../../../services/gr-reporting/gr-reporting_mock';
import {GrConfirmDeleteCommentDialog} from '../gr-confirm-delete-comment-dialog/gr-confirm-delete-comment-dialog';

const basicFixture = fixtureFromElement('gr-comment');

const draftFixture = fixtureFromTemplate(html`
  <gr-comment draft="true"></gr-comment>
`);

function isVisible(el: Element) {
  assert.ok(el);
  return getComputedStyle(el).getPropertyValue('display') !== 'none';
}

suite('gr-comment tests', () => {
  suite('basic tests', () => {
    let element: GrComment;

    let openOverlaySpy: sinon.SinonSpy;

    setup(() => {
      stubRestApi('getAccount').returns(
        Promise.resolve({
          email: 'dhruvsri@google.com' as EmailAddress,
          name: 'Dhruv Srivastava',
          _account_id: 1083225 as AccountId,
          avatars: [{url: 'abc', height: 32, width: 32}],
          registered_on: '123' as Timestamp,
        })
      );
      element = basicFixture.instantiate();
      element.comment = {
        ...createComment(),
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        id: 'baf0414d_60047215' as UrlEncodedCommentId,
        line: 5,
        message: 'is this a crossover episode!?',
        updated: '2015-12-08 19:48:33.843000000' as Timestamp,
      };

      openOverlaySpy = sinon.spy(element, '_openOverlay');
    });

    teardown(() => {
      openOverlaySpy.getCalls().forEach(call => {
        call.args[0].remove();
      });
    });

    test('collapsible comments', () => {
      // When a comment (not draft) is loaded, it should be collapsed
      assert.isTrue(element.collapsed);
      assert.isFalse(
        isVisible(queryAndAssert(element, 'gr-formatted-text')),
        'gr-formatted-text is not visible'
      );
      assert.isFalse(
        isVisible(queryAndAssert(element, '.actions')),
        'actions are not visible'
      );
      assert.isNotOk(element.textarea, 'textarea is not visible');

      // The header middle content is only visible when comments are collapsed.
      // It shows the message in a condensed way, and limits to a single line.
      assert.isTrue(
        isVisible(queryAndAssert(element, '.collapsedContent')),
        'header middle content is visible'
      );

      // When the header row is clicked, the comment should expand
      tap(element.$.header);
      assert.isFalse(element.collapsed);
      assert.isTrue(
        isVisible(queryAndAssert(element, 'gr-formatted-text')),
        'gr-formatted-text is visible'
      );
      assert.isTrue(
        isVisible(queryAndAssert(element, '.actions')),
        'actions are visible'
      );
      assert.isNotOk(element.textarea, 'textarea is not visible');
      assert.isFalse(
        isVisible(queryAndAssert(element, '.collapsedContent')),
        'header middle content is not visible'
      );
    });

    test('clicking on date link fires event', () => {
      element.side = 'PARENT';
      const stub = sinon.stub();
      element.addEventListener('comment-anchor-tap', stub);
      flush();
      const dateEl = queryAndAssert(element, '.date');
      assert.ok(dateEl);
      tap(dateEl);

      assert.isTrue(stub.called);
      assert.deepEqual(stub.lastCall.args[0].detail, {
        side: element.side,
        number: element.comment!.line,
      });
    });

    test('message is not retrieved from storage when other edits', done => {
      const storageStub = stubStorage('getDraftComment');
      const loadSpy = sinon.spy(element, '_loadLocalDraft');

      element.changeNum = 1 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      element.comment = {
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        line: 5,
      };
      flush(() => {
        assert.isTrue(loadSpy.called);
        assert.isFalse(storageStub.called);
        done();
      });
    });

    test('message is retrieved from storage when no other edits', done => {
      const storageStub = stubStorage('getDraftComment');
      const loadSpy = sinon.spy(element, '_loadLocalDraft');

      element.changeNum = 1 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      element.comment = {
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        line: 5,
        path: 'test',
      };
      flush(() => {
        assert.isTrue(loadSpy.called);
        assert.isTrue(storageStub.called);
        done();
      });
    });

    test('_getPatchNum', () => {
      element.side = 'PARENT';
      element.patchNum = 1 as PatchSetNum;
      assert.equal(element._getPatchNum(), 'PARENT' as PatchSetNum);
      element.side = 'REVISION';
      assert.equal(element._getPatchNum(), 1 as PatchSetNum);
    });

    test('comment expand and collapse', () => {
      element.collapsed = true;
      assert.isFalse(
        isVisible(queryAndAssert(element, 'gr-formatted-text')),
        'gr-formatted-text is not visible'
      );
      assert.isFalse(
        isVisible(queryAndAssert(element, '.actions')),
        'actions are not visible'
      );
      assert.isNotOk(element.textarea, 'textarea is not visible');
      assert.isTrue(
        isVisible(queryAndAssert(element, '.collapsedContent')),
        'header middle content is visible'
      );

      element.collapsed = false;
      assert.isFalse(element.collapsed);
      assert.isTrue(
        isVisible(queryAndAssert(element, 'gr-formatted-text')),
        'gr-formatted-text is visible'
      );
      assert.isTrue(
        isVisible(queryAndAssert(element, '.actions')),
        'actions are visible'
      );
      assert.isNotOk(element.textarea, 'textarea is not visible');
      assert.isFalse(
        isVisible(queryAndAssert(element, '.collapsedContent')),
        'header middle content is is not visible'
      );
    });

    suite('while editing', () => {
      let handleCancelStub: sinon.SinonStub;
      let handleSaveStub: sinon.SinonStub;
      setup(() => {
        element.editing = true;
        element._messageText = 'test';
        handleCancelStub = sinon.stub(element, '_handleCancel');
        handleSaveStub = sinon.stub(element, '_handleSave');
        flush();
      });

      suite('when text is empty', () => {
        setup(() => {
          element._messageText = '';
          element.comment = {};
        });

        test('esc closes comment when text is empty', () => {
          pressAndReleaseKeyOn(element.textarea!, 27); // esc
          assert.isTrue(handleCancelStub.called);
        });

        test('ctrl+enter does not save', () => {
          pressAndReleaseKeyOn(element.textarea!, 13, 'ctrl'); // ctrl + enter
          assert.isFalse(handleSaveStub.called);
        });

        test('meta+enter does not save', () => {
          pressAndReleaseKeyOn(element.textarea!, 13, 'meta'); // meta + enter
          assert.isFalse(handleSaveStub.called);
        });

        test('ctrl+s does not save', () => {
          pressAndReleaseKeyOn(element.textarea!, 83, 'ctrl'); // ctrl + s
          assert.isFalse(handleSaveStub.called);
        });
      });

      test('esc does not close comment that has content', () => {
        pressAndReleaseKeyOn(element.textarea!, 27); // esc
        assert.isFalse(handleCancelStub.called);
      });

      test('ctrl+enter saves', () => {
        pressAndReleaseKeyOn(element.textarea!, 13, 'ctrl'); // ctrl + enter
        assert.isTrue(handleSaveStub.called);
      });

      test('meta+enter saves', () => {
        pressAndReleaseKeyOn(element.textarea!, 13, 'meta'); // meta + enter
        assert.isTrue(handleSaveStub.called);
      });

      test('ctrl+s saves', () => {
        pressAndReleaseKeyOn(element.textarea!, 83, 'ctrl'); // ctrl + s
        assert.isTrue(handleSaveStub.called);
      });
    });

    test('delete comment button for non-admins is hidden', () => {
      element._isAdmin = false;
      assert.isFalse(
        queryAndAssert(element, '.action.delete').classList.contains(
          'showDeleteButtons'
        )
      );
    });

    test('delete comment button for admins with draft is hidden', () => {
      element._isAdmin = false;
      element.draft = true;
      assert.isFalse(
        queryAndAssert(element, '.action.delete').classList.contains(
          'showDeleteButtons'
        )
      );
    });

    test('delete comment', done => {
      const stub = stubRestApi('deleteComment').returns(
        Promise.resolve({
          id: '1' as UrlEncodedCommentId,
          updated: '1' as Timestamp,
          ...createComment(),
        })
      );
      const openSpy = sinon.spy(element.confirmDeleteOverlay!, 'open');
      element.changeNum = 42 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      element._isAdmin = true;
      assert.isTrue(
        queryAndAssert(element, '.action.delete').classList.contains(
          'showDeleteButtons'
        )
      );
      tap(queryAndAssert(element, '.action.delete'));
      flush(() => {
        openSpy.lastCall.returnValue.then(() => {
          const dialog = element.confirmDeleteOverlay?.querySelector(
            '#confirmDeleteComment'
          ) as GrConfirmDeleteCommentDialog;
          dialog.message = 'removal reason';
          element._handleConfirmDeleteComment();
          assert.isTrue(
            stub.calledWith(
              42 as NumericChangeId,
              1 as PatchSetNum,
              'baf0414d_60047215' as UrlEncodedCommentId,
              'removal reason'
            )
          );
          done();
        });
      });
    });

    suite('draft update reporting', () => {
      let endStub: SinonStubbedMember<() => Timer>;
      let getTimerStub: sinon.SinonStub;
      const mockEvent = {...new Event('click'), preventDefault() {}};

      setup(() => {
        sinon.stub(element, 'save').returns(Promise.resolve({}));
        sinon.stub(element, '_discardDraft').returns(Promise.resolve({}));
        endStub = sinon.stub();
        const mockTimer = new MockTimer();
        mockTimer.end = endStub;
        getTimerStub = sinon
          .stub(element.reporting, 'getTimer')
          .returns(mockTimer);
      });

      test('create', () => {
        element.patchNum = 1 as PatchSetNum;
        element.comment = {};
        return element._handleSave(mockEvent)!.then(() => {
          assert.equal(
            (queryAndAssert(
              element,
              'gr-account-label'
            ).shadowRoot?.querySelector(
              'span.name'
            ) as HTMLSpanElement).innerText.trim(),
            'Dhruv Srivastava'
          );
          assert.isTrue(endStub.calledOnce);
          assert.isTrue(getTimerStub.calledOnce);
          assert.equal(getTimerStub.lastCall.args[0], 'CreateDraftComment');
        });
      });

      test('update', () => {
        element.comment = {
          ...createComment(),
          id: ('abc_123' as UrlEncodedCommentId) as UrlEncodedCommentId,
        };
        return element._handleSave(mockEvent)!.then(() => {
          assert.isTrue(endStub.calledOnce);
          assert.isTrue(getTimerStub.calledOnce);
          assert.equal(getTimerStub.lastCall.args[0], 'UpdateDraftComment');
        });
      });

      test('discard', () => {
        element.comment = {
          ...createComment(),
          id: ('abc_123' as UrlEncodedCommentId) as UrlEncodedCommentId,
        };
        sinon.stub(element, '_closeConfirmDiscardOverlay');
        return element._handleConfirmDiscard(mockEvent).then(() => {
          assert.isTrue(endStub.calledOnce);
          assert.isTrue(getTimerStub.calledOnce);
          assert.equal(getTimerStub.lastCall.args[0], 'DiscardDraftComment');
        });
      });
    });

    test('edit reports interaction', () => {
      const reportStub = sinon.stub(
        element.reporting,
        'recordDraftInteraction'
      );
      element.draft = true;
      flush();
      tap(queryAndAssert(element, '.edit'));
      assert.isTrue(reportStub.calledOnce);
    });

    test('discard reports interaction', () => {
      const reportStub = sinon.stub(
        element.reporting,
        'recordDraftInteraction'
      );
      element.draft = true;
      flush();
      tap(queryAndAssert(element, '.discard'));
      assert.isTrue(reportStub.calledOnce);
    });

    test('failed save draft request', done => {
      element.draft = true;
      element.changeNum = 1 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      const updateRequestStub = sinon.stub(element, '_updateRequestToast');
      const diffDraftStub = stubRestApi('saveDiffDraft').returns(
        Promise.resolve({...new Response(), ok: false})
      );
      element._saveDraft({
        ...createComment(),
        id: 'abc_123' as UrlEncodedCommentId,
      });
      flush(() => {
        let args = updateRequestStub.lastCall.args;
        assert.deepEqual(args, [0, true]);
        assert.equal(
          element._getSavingMessage(...args),
          __testOnly_UNSAVED_MESSAGE
        );
        assert.equal(
          (queryAndAssert(element, '.draftLabel') as HTMLSpanElement).innerText,
          'DRAFT(Failed to save)'
        );
        assert.isTrue(
          isVisible(queryAndAssert(element, '.save')),
          'save is visible'
        );
        diffDraftStub.returns(Promise.resolve({...new Response(), ok: true}));
        element._saveDraft({
          ...createComment(),
          id: 'abc_123' as UrlEncodedCommentId,
        });
        flush(() => {
          args = updateRequestStub.lastCall.args;
          assert.deepEqual(args, [0]);
          assert.equal(element._getSavingMessage(...args), 'All changes saved');
          assert.equal(
            (queryAndAssert(element, '.draftLabel') as HTMLSpanElement)
              .innerText,
            'DRAFT'
          );
          assert.isFalse(
            isVisible(queryAndAssert(element, '.save')),
            'save is not visible'
          );
          assert.isFalse(element._unableToSave);
          done();
        });
      });
    });

    test('failed save draft request with promise failure', done => {
      element.draft = true;
      element.changeNum = 1 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      const updateRequestStub = sinon.stub(element, '_updateRequestToast');
      const diffDraftStub = stubRestApi('saveDiffDraft').returns(
        Promise.reject(new Error())
      );
      element._saveDraft({
        ...createComment(),
        id: 'abc_123' as UrlEncodedCommentId,
      });
      flush(() => {
        let args = updateRequestStub.lastCall.args;
        assert.deepEqual(args, [0, true]);
        assert.equal(
          element._getSavingMessage(...args),
          __testOnly_UNSAVED_MESSAGE
        );
        assert.equal(
          (queryAndAssert(element, '.draftLabel') as HTMLSpanElement).innerText,
          'DRAFT(Failed to save)'
        );
        assert.isTrue(
          isVisible(queryAndAssert(element, '.save')),
          'save is visible'
        );
        diffDraftStub.returns(Promise.resolve({...new Response(), ok: true}));
        element._saveDraft({
          ...createComment(),
          id: 'abc_123' as UrlEncodedCommentId,
        });
        flush(() => {
          args = updateRequestStub.lastCall.args;
          assert.deepEqual(args, [0]);
          assert.equal(element._getSavingMessage(...args), 'All changes saved');
          assert.equal(
            (queryAndAssert(element, '.draftLabel') as HTMLSpanElement)
              .innerText,
            'DRAFT'
          );
          assert.isFalse(
            isVisible(queryAndAssert(element, '.save')),
            'save is not visible'
          );
          assert.isFalse(element._unableToSave);
          done();
        });
      });
    });
  });

  suite('gr-comment draft tests', () => {
    let element: GrComment;

    setup(() => {
      stubRestApi('getAccount').returns(Promise.resolve(undefined));
      stubRestApi('saveDiffDraft').returns(
        Promise.resolve({
          ...new Response(),
          ok: true,
          text() {
            return Promise.resolve(
              ")]}'\n{" +
                '"...createComment(), id": "baf0414d_40572e03",' +
                '"path": "/path/to/file",' +
                '"line": 5,' +
                '"updated": "2015-12-08 21:52:36.177000000",' +
                '"message": "saved!"' +
                '}'
            );
          },
        })
      );
      stubRestApi('removeChangeReviewer').returns(
        Promise.resolve({...new Response(), ok: true})
      );
      element = draftFixture.instantiate() as GrComment;
      stubStorage('getDraftComment').returns(null);
      element.changeNum = 42 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      element.editing = false;
      element.comment = {
        ...createComment(),
        __draft: true,
        __draftID: 'temp_draft_id',
        path: '/path/to/file',
        line: 5,
      };
    });

    test('button visibility states', () => {
      element.showActions = false;
      assert.isTrue(
        queryAndAssert(element, '.humanActions').hasAttribute('hidden')
      );
      assert.isTrue(
        queryAndAssert(element, '.robotActions').hasAttribute('hidden')
      );

      element.showActions = true;
      assert.isFalse(
        queryAndAssert(element, '.humanActions').hasAttribute('hidden')
      );
      assert.isTrue(
        queryAndAssert(element, '.robotActions').hasAttribute('hidden')
      );

      element.draft = true;
      flush();
      assert.isTrue(
        isVisible(queryAndAssert(element, '.edit')),
        'edit is visible'
      );
      assert.isTrue(
        isVisible(queryAndAssert(element, '.discard')),
        'discard is visible'
      );
      assert.isFalse(
        isVisible(queryAndAssert(element, '.save')),
        'save is not visible'
      );
      assert.isFalse(
        isVisible(queryAndAssert(element, '.cancel')),
        'cancel is not visible'
      );
      assert.isTrue(
        isVisible(queryAndAssert(element, '.resolve')),
        'resolve is visible'
      );
      assert.isFalse(
        queryAndAssert(element, '.humanActions').hasAttribute('hidden')
      );
      assert.isTrue(
        queryAndAssert(element, '.robotActions').hasAttribute('hidden')
      );

      element.editing = true;
      flush();
      assert.isFalse(
        isVisible(queryAndAssert(element, '.edit')),
        'edit is not visible'
      );
      assert.isFalse(
        isVisible(queryAndAssert(element, '.discard')),
        'discard not visible'
      );
      assert.isTrue(
        isVisible(queryAndAssert(element, '.save')),
        'save is visible'
      );
      assert.isTrue(
        isVisible(queryAndAssert(element, '.cancel')),
        'cancel is visible'
      );
      assert.isTrue(
        isVisible(queryAndAssert(element, '.resolve')),
        'resolve is visible'
      );
      assert.isFalse(
        queryAndAssert(element, '.humanActions').hasAttribute('hidden')
      );
      assert.isTrue(
        queryAndAssert(element, '.robotActions').hasAttribute('hidden')
      );

      element.draft = false;
      element.editing = false;
      flush();
      assert.isFalse(
        isVisible(queryAndAssert(element, '.edit')),
        'edit is not visible'
      );
      assert.isFalse(
        isVisible(queryAndAssert(element, '.discard')),
        'discard is not visible'
      );
      assert.isFalse(
        isVisible(queryAndAssert(element, '.save')),
        'save is not visible'
      );
      assert.isFalse(
        isVisible(queryAndAssert(element, '.cancel')),
        'cancel is not visible'
      );
      assert.isFalse(
        queryAndAssert(element, '.humanActions').hasAttribute('hidden')
      );
      assert.isTrue(
        queryAndAssert(element, '.robotActions').hasAttribute('hidden')
      );

      element.comment!.id = 'foo' as UrlEncodedCommentId;
      element.draft = true;
      element.editing = true;
      flush();
      assert.isTrue(
        isVisible(queryAndAssert(element, '.cancel')),
        'cancel is visible'
      );
      assert.isFalse(
        queryAndAssert(element, '.humanActions').hasAttribute('hidden')
      );
      assert.isTrue(
        queryAndAssert(element, '.robotActions').hasAttribute('hidden')
      );

      // Delete button is not hidden by default
      assert.isFalse(
        (queryAndAssert(element, '#deleteBtn') as HTMLElement).hidden
      );

      element.isRobotComment = true;
      element.draft = true;
      assert.isTrue(
        queryAndAssert(element, '.humanActions').hasAttribute('hidden')
      );
      assert.isFalse(
        queryAndAssert(element, '.robotActions').hasAttribute('hidden')
      );

      // It is not expected to see Robot comment drafts, but if they appear,
      // they will behave the same as non-drafts.
      element.draft = false;
      assert.isTrue(
        queryAndAssert(element, '.humanActions').hasAttribute('hidden')
      );
      assert.isFalse(
        queryAndAssert(element, '.robotActions').hasAttribute('hidden')
      );

      // A robot comment with run ID should display plain text.
      element.set(['comment', 'robot_run_id'], 'text');
      element.editing = false;
      element.collapsed = false;
      flush();
      assert.isTrue(
        queryAndAssert(element, '.robotRun.link').textContent === 'Run Details'
      );

      // A robot comment with run ID and url should display a link.
      element.set(['comment', 'url'], '/path/to/run');
      flush();
      assert.notEqual(
        getComputedStyle(queryAndAssert(element, '.robotRun.link')).display,
        'none'
      );

      // Delete button is hidden for robot comments
      assert.isTrue(
        (queryAndAssert(element, '#deleteBtn') as HTMLElement).hidden
      );
    });

    test('collapsible drafts', () => {
      assert.isTrue(element.collapsed);
      assert.isFalse(
        isVisible(queryAndAssert(element, 'gr-formatted-text')),
        'gr-formatted-text is not visible'
      );
      assert.isFalse(
        isVisible(queryAndAssert(element, '.actions')),
        'actions are not visible'
      );
      assert.isNotOk(element.textarea, 'textarea is not visible');
      assert.isTrue(
        isVisible(queryAndAssert(element, '.collapsedContent')),
        'header middle content is visible'
      );

      tap(element.$.header);
      assert.isFalse(element.collapsed);
      assert.isTrue(
        isVisible(queryAndAssert(element, 'gr-formatted-text')),
        'gr-formatted-text is visible'
      );
      assert.isTrue(
        isVisible(queryAndAssert(element, '.actions')),
        'actions are visible'
      );
      assert.isNotOk(element.textarea, 'textarea is not visible');
      assert.isFalse(
        isVisible(queryAndAssert(element, '.collapsedContent')),
        'header middle content is is not visible'
      );

      // When the edit button is pressed, should still see the actions
      // and also textarea
      element.draft = true;
      flush();
      tap(queryAndAssert(element, '.edit'));
      flush();
      assert.isFalse(element.collapsed);
      assert.isFalse(
        isVisible(queryAndAssert(element, 'gr-formatted-text')),
        'gr-formatted-text is not visible'
      );
      assert.isTrue(
        isVisible(queryAndAssert(element, '.actions')),
        'actions are visible'
      );
      assert.isTrue(isVisible(element.textarea!), 'textarea is visible');
      assert.isFalse(
        isVisible(queryAndAssert(element, '.collapsedContent')),
        'header middle content is not visible'
      );

      // When toggle again, everything should be hidden except for textarea
      // and header middle content should be visible
      tap(element.$.header);
      assert.isTrue(element.collapsed);
      assert.isFalse(
        isVisible(queryAndAssert(element, 'gr-formatted-text')),
        'gr-formatted-text is not visible'
      );
      assert.isFalse(
        isVisible(queryAndAssert(element, '.actions')),
        'actions are not visible'
      );
      assert.isFalse(
        isVisible(queryAndAssert(element, 'gr-textarea')),
        'textarea is not visible'
      );
      assert.isTrue(
        isVisible(queryAndAssert(element, '.collapsedContent')),
        'header middle content is visible'
      );

      // When toggle again, textarea should remain open in the state it was
      // before
      tap(element.$.header);
      assert.isFalse(
        isVisible(queryAndAssert(element, 'gr-formatted-text')),
        'gr-formatted-text is not visible'
      );
      assert.isTrue(
        isVisible(queryAndAssert(element, '.actions')),
        'actions are visible'
      );
      assert.isTrue(isVisible(element.textarea!), 'textarea is visible');
      assert.isFalse(
        isVisible(queryAndAssert(element, '.collapsedContent')),
        'header middle content is not visible'
      );
    });

    test('robot comment layout', done => {
      const comment = {
        robot_id: 'happy_robot_id' as RobotId,
        url: '/robot/comment',
        author: {
          name: 'Happy Robot',
          display_name: 'Display name Robot',
        },
        ...element.comment,
      };
      element.comment = comment;
      element.collapsed = false;
      flush(() => {
        let runIdMessage;
        runIdMessage = queryAndAssert(element, '.runIdMessage') as HTMLElement;
        assert.isFalse((runIdMessage as HTMLElement).hidden);

        const runDetailsLink = queryAndAssert(
          element,
          '.robotRunLink'
        ) as HTMLAnchorElement;
        assert.isTrue(
          runDetailsLink.href.indexOf((element.comment as UIRobot).url!) !== -1
        );

        const robotServiceName = queryAndAssert(element, '.robotName');
        assert.equal(robotServiceName.textContent?.trim(), 'happy_robot_id');

        const authorName = queryAndAssert(element, '.robotId');
        assert.isTrue(
          (authorName as HTMLDivElement).innerText === 'Happy Robot'
        );

        element.collapsed = true;
        flush();
        runIdMessage = queryAndAssert(element, '.runIdMessage');
        assert.isTrue((runIdMessage as HTMLDivElement).hidden);
        done();
      });
    });

    test('author name fallback to email', done => {
      const comment = {
        url: '/robot/comment',
        author: {
          email: 'test@test.com' as EmailAddress,
        },
        ...element.comment,
      };
      element.comment = comment;
      element.collapsed = false;
      flush(() => {
        const authorName = queryAndAssert(
          queryAndAssert(element, 'gr-account-label'),
          'span.name'
        ) as HTMLSpanElement;
        assert.equal(authorName.innerText.trim(), 'test@test.com');
        done();
      });
    });

    test('patchset level comment', done => {
      const comment = {
        ...element.comment,
        path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
        line: undefined,
        range: undefined,
      };
      element.comment = comment;
      flush();
      tap(queryAndAssert(element, '.edit'));
      assert.isTrue(element.editing);

      element._messageText = 'hello world';
      const eraseMessageDraftSpy = spyStorage('eraseDraftComment');
      const mockEvent = {...new Event('click'), preventDefault: sinon.stub()};
      element._handleSave(mockEvent);
      flush(() => {
        assert.isTrue(eraseMessageDraftSpy.called);
        done();
      });
    });

    test.only('draft creation/cancellation', done => {
      assert.isFalse(element.editing);
      element.draft = true;
      flush();
      tap(queryAndAssert(element, '.edit'));
      assert.isTrue(element.editing);

      element._messageText = '';
      const eraseMessageDraftSpy = sinon.spy(element, '_eraseDraftComment');

      // Save should be disabled on an empty message.
      let disabled = queryAndAssert(element, '.save').hasAttribute('disabled');
      assert.isTrue(disabled, 'save button should be disabled.');
      element._messageText = '     ';
      disabled = queryAndAssert(element, '.save').hasAttribute('disabled');
      assert.isTrue(disabled, 'save button should be disabled.');

      const updateStub = sinon.stub();
      element.addEventListener('comment-update', updateStub);

      let numDiscardEvents = 0;
      element.addEventListener('comment-discard', () => {
        numDiscardEvents++;
        assert.isFalse(eraseMessageDraftSpy.called);
        if (numDiscardEvents === 2) {
          assert.isFalse(updateStub.called);
          done();
        }
      });
      tap(queryAndAssert(element, '.cancel'));
      element.fireUpdateTask.flush();
      element._messageText = '';
      element.editing = true;
      flush();
      pressAndReleaseKeyOn(element.textarea!, 27); // esc
    });

    test('draft discard removes message from storage', done => {
      element._messageText = '';
      const eraseMessageDraftSpy = sinon.spy(element, '_eraseDraftComment');
      sinon.stub(element, '_closeConfirmDiscardOverlay');

      element.addEventListener('comment-discard', () => {
        assert.isTrue(eraseMessageDraftSpy.called);
        done();
      });
      element._handleConfirmDiscard({
        ...new Event('click'),
        preventDefault: sinon.stub(),
      });
    });

    test('storage is cleared only after save success', () => {
      element._messageText = 'test';
      const eraseStub = sinon.stub(element, '_eraseDraftComment');

      const saveDraftStub = sinon
        .stub(element, '_saveDraft')
        .returns(Promise.resolve({...new Response(), ok: false}));

      const savePromise = element.save();
      assert.isFalse(eraseStub.called);
      return savePromise.then(() => {
        assert.isFalse(eraseStub.called);

        saveDraftStub.restore();
        sinon
          .stub(element, '_saveDraft')
          .returns(Promise.resolve({...new Response(), ok: true}));
        return element.save().then(() => {
          assert.isTrue(eraseStub.called);
        });
      });
    });

    test('_computeSaveDisabled', () => {
      const comment = {unresolved: true};
      const msgComment = {message: 'test', unresolved: true};
      assert.equal(element._computeSaveDisabled('', comment, false), true);
      assert.equal(element._computeSaveDisabled('test', comment, false), false);
      assert.equal(element._computeSaveDisabled('', msgComment, false), true);
      assert.equal(
        element._computeSaveDisabled('test', msgComment, false),
        false
      );
      assert.equal(
        element._computeSaveDisabled('test2', msgComment, false),
        false
      );
      assert.equal(element._computeSaveDisabled('test', comment, true), false);
      assert.equal(element._computeSaveDisabled('', comment, true), true);
      assert.equal(element._computeSaveDisabled('', comment, false), true);
    });

    suite('confirm discard', () => {
      let discardStub: sinon.SinonStub;
      let overlayStub: sinon.SinonStub;
      const mockEvent = {...new Event('click'), preventDefault: sinon.stub()};
      setup(() => {
        discardStub = sinon.stub(element, '_discardDraft');
        overlayStub = sinon
          .stub(element, '_openOverlay')
          .returns(Promise.resolve());
      });

      test('confirms discard of comments with message text', () => {
        element._messageText = 'test';
        element._handleDiscard(mockEvent);
        assert.isTrue(overlayStub.calledWith(element.confirmDiscardOverlay));
        assert.isFalse(discardStub.called);
      });

      test('no confirmation for comments without message text', () => {
        element._messageText = '';
        element._handleDiscard(mockEvent);
        assert.isFalse(overlayStub.called);
        assert.isTrue(discardStub.calledOnce);
      });
    });

    test('ctrl+s saves comment', done => {
      const stub = sinon.stub(element, 'save').callsFake(() => {
        assert.isTrue(stub.called);
        stub.restore();
        done();
        return Promise.resolve();
      });
      element._messageText = 'is that the horse from horsing around??';
      element.editing = true;
      flush();
      pressAndReleaseKeyOn(element.textarea!.$.textarea.textarea, 83, 'ctrl'); // 'ctrl + s'
    });

    test('draft saving/editing', done => {
      const dispatchEventStub = sinon.stub(element, 'dispatchEvent');

      element.draft = true;
      flush();
      tap(queryAndAssert(element, '.edit'));
      element._messageText = 'good news, everyone!';
      flush();
      assert.equal(dispatchEventStub.lastCall.args[0].type, 'comment-update');
      assert.isTrue(dispatchEventStub.calledTwice);

      element._messageText = 'good news, everyone!';
      flush();
      assert.isTrue(dispatchEventStub.calledTwice);

      tap(queryAndAssert(element, '.save'));

      assert.isTrue(
        element.disabled,
        'Element should be disabled when creating draft.'
      );

      element
        ._xhrPromise!.then(draft => {
          const evt = dispatchEventStub.lastCall.args[0] as CustomEvent<{
            comment: DraftInfo;
          }>;
          assert.equal(evt.type, 'comment-save');

          assert.deepEqual(evt.detail, {
            comment: {
              __draft: true,
              __draftID: 'temp_draft_id',
              ...createComment(),
              id: 'baf0414d_40572e03' as UrlEncodedCommentId,
              line: 5,
              message: 'saved!',
              path: '/path/to/file',
              updated: '2015-12-08 21:52:36.177000000' as Timestamp,
            },
          });
          assert.isFalse(
            element.disabled,
            'Element should be enabled when done creating draft.'
          );
          assert.equal(draft.message, 'saved!');
          assert.isFalse(element.editing);
        })
        .then(() => {
          tap(queryAndAssert(element, '.edit'));
          element._messageText =
            'You’ll be delivering a package to Chapek 9, ' +
            'a world where humans are killed on sight.';
          tap(queryAndAssert(element, '.save'));
          assert.isTrue(
            element.disabled,
            'Element should be disabled when updating draft.'
          );

          element._xhrPromise!.then(draft => {
            assert.isFalse(
              element.disabled,
              'Element should be enabled when done updating draft.'
            );
            assert.equal(draft.message, 'saved!');
            assert.isFalse(element.editing);
            dispatchEventStub.restore();
            done();
          });
        });
    });

    test('draft prevent save when disabled', () => {
      const saveStub = sinon.stub(element, 'save').returns(Promise.resolve());
      element.showActions = true;
      element.draft = true;
      flush();
      tap(element.$.header);
      tap(queryAndAssert(element, '.edit'));
      element._messageText = 'good news, everyone!';
      flush();

      element.disabled = true;
      tap(queryAndAssert(element, '.save'));
      assert.isFalse(saveStub.called);

      element.disabled = false;
      tap(queryAndAssert(element, '.save'));
      assert.isTrue(saveStub.calledOnce);
    });

    test('proper event fires on resolve, comment is not saved', done => {
      const save = sinon.stub(element, 'save');
      element.addEventListener('comment-update', e => {
        assert.isTrue(e.detail.comment.unresolved);
        assert.isFalse(save.called);
        done();
      });
      tap(queryAndAssert(element, '.resolve input'));
    });

    test('resolved comment state indicated by checkbox', () => {
      sinon.stub(element, 'save');
      element.comment = {unresolved: false};
      assert.isTrue(
        (queryAndAssert(element, '.resolve input') as HTMLInputElement).checked
      );
      element.comment = {unresolved: true};
      assert.isFalse(
        (queryAndAssert(element, '.resolve input') as HTMLInputElement).checked
      );
    });

    test('resolved checkbox saves with tap when !editing', () => {
      element.editing = false;
      const save = sinon.stub(element, 'save');

      element.comment = {unresolved: false};
      assert.isTrue(
        (queryAndAssert(element, '.resolve input') as HTMLInputElement).checked
      );
      element.comment = {unresolved: true};
      assert.isFalse(
        (queryAndAssert(element, '.resolve input') as HTMLInputElement).checked
      );
      assert.isFalse(save.called);
      tap(element.$.resolvedCheckbox);
      assert.isTrue(
        (queryAndAssert(element, '.resolve input') as HTMLInputElement).checked
      );
      assert.isTrue(save.called);
    });

    suite('draft saving messages', () => {
      test('_getSavingMessage', () => {
        assert.equal(element._getSavingMessage(0), 'All changes saved');
        assert.equal(element._getSavingMessage(1), 'Saving 1 draft...');
        assert.equal(element._getSavingMessage(2), 'Saving 2 drafts...');
        assert.equal(element._getSavingMessage(3), 'Saving 3 drafts...');
      });

      test('_show{Start,End}Request', () => {
        const updateStub = sinon.stub(element, '_updateRequestToast');
        element._numPendingDraftRequests.number = 1;

        element._showStartRequest();
        assert.isTrue(updateStub.calledOnce);
        assert.equal(updateStub.lastCall.args[0], 2);
        assert.equal(element._numPendingDraftRequests.number, 2);

        element._showEndRequest();
        assert.isTrue(updateStub.calledTwice);
        assert.equal(updateStub.lastCall.args[0], 1);
        assert.equal(element._numPendingDraftRequests.number, 1);

        element._showEndRequest();
        assert.isTrue(updateStub.calledThrice);
        assert.equal(updateStub.lastCall.args[0], 0);
        assert.equal(element._numPendingDraftRequests.number, 0);
      });
    });

    test('cancelling an unsaved draft discards, persists in storage', () => {
      const discardSpy = sinon.spy(element, '_fireDiscard');
      const storeStub = stubStorage('setDraftComment');
      const eraseStub = stubStorage('eraseDraftComment');
      element._messageText = 'test text';
      flush();

      assert.isTrue(storeStub.called);
      assert.equal(storeStub.lastCall.args[1], 'test text');
      element._handleCancel({
        ...new Event('click'),
        preventDefault: sinon.stub(),
      });
      assert.isTrue(discardSpy.called);
      assert.isFalse(eraseStub.called);
    });

    test('cancelling edit on a saved draft does not store', () => {
      element.comment!.id = 'foo' as UrlEncodedCommentId;
      const discardSpy = sinon.spy(element, '_fireDiscard');
      const storeStub = stubStorage('setDraftComment');
      element._messageText = 'test text';
      flush();

      assert.isFalse(storeStub.called);
      element._handleCancel({...new Event('click'), preventDefault: () => {}});
      assert.isTrue(discardSpy.called);
    });

    test('deleting text from saved draft and saving deletes the draft', () => {
      element.comment = {
        ...createComment(),
        id: 'foo' as UrlEncodedCommentId,
        message: 'test',
      };
      element._messageText = '';
      const discardStub = sinon.stub(element, '_discardDraft');

      element.save();
      assert.isTrue(discardStub.called);
    });

    test('_handleFix fires create-fix event', done => {
      element.addEventListener(
        'create-fix-comment',
        (e: CreateFixCommentEvent) => {
          assert.deepEqual(e.detail, element._getEventPayload());
          done();
        }
      );
      element.isRobotComment = true;
      element.comments = [element.comment!];
      flush();

      tap(queryAndAssert(element, '.fix'));
    });

    test('do not show Please Fix button if human reply exists', () => {
      element.comments = [
        {
          robot_id: 'happy_robot_id' as RobotId,
          robot_run_id: '5838406743490560' as RobotRunId,
          fix_suggestions: [
            {
              fix_id: '478ff847_3bf47aaf' as FixId,
              description: 'Make the smiley happier by giving it a nose.',
              replacements: [
                {
                  path: 'Documentation/config-gerrit.txt',
                  range: {
                    start_line: 10,
                    start_character: 7,
                    end_line: 10,
                    end_character: 9,
                  },
                  replacement: ':-)',
                },
              ],
            },
          ],
          author: {
            _account_id: 1030912 as AccountId,
            name: 'Alice Kober-Sotzek',
            email: 'aliceks@google.com' as EmailAddress,
            avatars: [
              {
                url: '/s32-p/photo.jpg',
                height: 32,
                width: 32,
              },
              {
                url: '/AaAdOFzPlFI/s56-p/photo.jpg',
                height: 56,
                width: 32,
              },
              {
                url: '/AaAdOFzPlFI/s100-p/photo.jpg',
                height: 100,
                width: 32,
              },
              {
                url: '/AaAdOFzPlFI/s120-p/photo.jpg',
                height: 120,
                width: 32,
              },
            ],
          },
          patch_set: 1 as PatchSetNum,
          ...createComment(),
          id: 'eb0d03fd_5e95904f' as UrlEncodedCommentId,
          line: 10,
          updated: '2017-04-04 15:36:17.000000000' as Timestamp,
          message: 'This is a robot comment with a fix.',
          unresolved: false,
          collapsed: false,
        },
        {
          __draft: true,
          __draftID: '0.wbrfbwj89sa',
          __date: new Date(),
          path: 'Documentation/config-gerrit.txt',
          side: CommentSide.REVISION,
          line: 10,
          in_reply_to: 'eb0d03fd_5e95904f' as UrlEncodedCommentId,
          message: '> This is a robot comment with a fix.\n\nPlease fix.',
          unresolved: true,
        },
      ];
      element.comment = element.comments[0];
      flush();
      assert.isNull(
        element.shadowRoot?.querySelector('robotActions gr-button')
      );
    });

    test('show Please Fix if no human reply', () => {
      element.comments = [
        {
          robot_id: 'happy_robot_id' as RobotId,
          robot_run_id: '5838406743490560' as RobotRunId,
          fix_suggestions: [
            {
              fix_id: '478ff847_3bf47aaf' as FixId,
              description: 'Make the smiley happier by giving it a nose.',
              replacements: [
                {
                  path: 'Documentation/config-gerrit.txt',
                  range: {
                    start_line: 10,
                    start_character: 7,
                    end_line: 10,
                    end_character: 9,
                  },
                  replacement: ':-)',
                },
              ],
            },
          ],
          author: {
            _account_id: 1030912 as AccountId,
            name: 'Alice Kober-Sotzek',
            email: 'aliceks@google.com' as EmailAddress,
            avatars: [
              {
                url: '/s32-p/photo.jpg',
                height: 32,
                width: 32,
              },
              {
                url: '/AaAdOFzPlFI/s56-p/photo.jpg',
                height: 56,
                width: 32,
              },
              {
                url: '/AaAdOFzPlFI/s100-p/photo.jpg',
                height: 100,
                width: 32,
              },
              {
                url: '/AaAdOFzPlFI/s120-p/photo.jpg',
                height: 120,
                width: 32,
              },
            ],
          },
          patch_set: 1 as PatchSetNum,
          ...createComment(),
          id: 'eb0d03fd_5e95904f' as UrlEncodedCommentId,
          line: 10,
          updated: '2017-04-04 15:36:17.000000000' as Timestamp,
          message: 'This is a robot comment with a fix.',
          unresolved: false,
          collapsed: false,
        },
      ];
      element.comment = element.comments[0];
      flush();
      queryAndAssert(element, '.robotActions gr-button');
    });

    test('_handleShowFix fires open-fix-preview event', done => {
      element.addEventListener('open-fix-preview', e => {
        assert.deepEqual(e.detail, element._getEventPayload());
        done();
      });
      element.comment = {
        ...createComment(),
        fix_suggestions: [{...createFixSuggestionInfo()}],
      };
      element.isRobotComment = true;
      flush();

      tap(queryAndAssert(element, '.show-fix'));
    });
  });

  suite('respectful tips', () => {
    let element: GrComment;

    let clock: sinon.SinonFakeTimers;
    setup(() => {
      stubRestApi('getAccount').returns(Promise.resolve(undefined));
      clock = sinon.useFakeTimers();
    });

    teardown(() => {
      clock.restore();
      sinon.restore();
    });

    test('show tip when no cached record', done => {
      element = draftFixture.instantiate() as GrComment;
      const respectfulGetStub = stubStorage('getRespectfulTipVisibility');
      const respectfulSetStub = stubStorage('setRespectfulTipVisibility');
      respectfulGetStub.returns(null);
      // fake random
      element.getRandomNum = () => 0;
      element.comment = {__editing: true, __draft: true};
      flush(() => {
        assert.isTrue(respectfulGetStub.called);
        assert.isTrue(respectfulSetStub.called);
        assert.isTrue(!!queryAndAssert(element, '.respectfulReviewTip'));
        done();
      });
    });

    test('add 14-day delays once dismissed', done => {
      element = draftFixture.instantiate() as GrComment;
      const respectfulGetStub = stubStorage('getRespectfulTipVisibility');
      const respectfulSetStub = stubStorage('setRespectfulTipVisibility');
      respectfulGetStub.returns(null);
      // fake random
      element.getRandomNum = () => 0;
      element.comment = {__editing: true, __draft: true};
      flush(() => {
        assert.isTrue(respectfulGetStub.called);
        assert.isTrue(respectfulSetStub.called);
        assert.isTrue(respectfulSetStub.lastCall.args[0] === undefined);
        assert.isTrue(!!queryAndAssert(element, '.respectfulReviewTip'));

        tap(queryAndAssert(element, '.respectfulReviewTip .close'));
        flush();
        assert.isTrue(respectfulSetStub.lastCall.args[0] === 14);
        done();
      });
    });

    test('do not show tip when fall out of probability', done => {
      element = draftFixture.instantiate() as GrComment;
      const respectfulGetStub = stubStorage('getRespectfulTipVisibility');
      const respectfulSetStub = stubStorage('setRespectfulTipVisibility');
      respectfulGetStub.returns(null);
      // fake random
      element.getRandomNum = () => 3;
      element.comment = {__editing: true, __draft: true};
      flush(() => {
        assert.isTrue(respectfulGetStub.called);
        assert.isFalse(respectfulSetStub.called);
        assert.isNotOk(query(element, '.respectfulReviewTip'));
        done();
      });
    });

    test('show tip when editing changed to true', done => {
      element = draftFixture.instantiate() as GrComment;
      const respectfulGetStub = stubStorage('getRespectfulTipVisibility');
      const respectfulSetStub = stubStorage('setRespectfulTipVisibility');
      respectfulGetStub.returns(null);
      // fake random
      element.getRandomNum = () => 0;
      element.comment = {__editing: false};
      flush(() => {
        assert.isFalse(respectfulGetStub.called);
        assert.isFalse(respectfulSetStub.called);
        assert.isNotOk(query(element, '.respectfulReviewTip'));

        element.editing = true;
        flush(() => {
          assert.isTrue(respectfulGetStub.called);
          assert.isTrue(respectfulSetStub.called);
          assert.isTrue(!!queryAndAssert(element, '.respectfulReviewTip'));
          done();
        });
      });
    });

    test('no tip when cached record', done => {
      element = draftFixture.instantiate() as GrComment;
      const respectfulGetStub = stubStorage('getRespectfulTipVisibility');
      const respectfulSetStub = stubStorage('setRespectfulTipVisibility');
      respectfulGetStub.returns({updated: 0});
      // fake random
      element.getRandomNum = () => 0;
      element.comment = {__editing: true, __draft: true};
      flush(() => {
        assert.isTrue(respectfulGetStub.called);
        assert.isFalse(respectfulSetStub.called);
        assert.isNotOk(query(element, '.respectfulReviewTip'));
        done();
      });
    });
  });
});
