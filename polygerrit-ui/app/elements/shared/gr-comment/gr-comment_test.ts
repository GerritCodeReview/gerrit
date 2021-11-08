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
  isVisible,
  stubReporting,
  mockPromise,
} from '../../../test/test-utils';
import {
  AccountId,
  EmailAddress,
  FixId,
  NumericChangeId,
  ParsedJSON,
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
  createDraft,
  createFixSuggestionInfo,
} from '../../../test/test-data-generators';
import {Timer} from '../../../services/gr-reporting/gr-reporting';
import {SinonFakeTimers, SinonStubbedMember} from 'sinon';
import {CreateFixCommentEvent} from '../../../types/events';
import {DraftInfo, RobotCommentInfo} from '../../../utils/comment-util';
import {MockTimer} from '../../../services/gr-reporting/gr-reporting_mock';
import {GrConfirmDeleteCommentDialog} from '../gr-confirm-delete-comment-dialog/gr-confirm-delete-comment-dialog';

const basicFixture = fixtureFromElement('gr-comment');

const draftFixture = fixtureFromTemplate(html`
  <gr-comment draft="true"></gr-comment>
`);

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

      openOverlaySpy = sinon.spy(element, 'openOverlay');
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

    test('message is not retrieved from storage when missing path', async () => {
      const storageStub = stubStorage('getDraftComment');
      const loadSpy = sinon.spy(element, 'loadLocalDraft');

      element.changeNum = 1 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      element.comment = {
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        line: 5,
      };
      await flush();
      assert.isTrue(loadSpy.called);
      assert.isFalse(storageStub.called);
    });

    test('message is not retrieved from storage when message present', async () => {
      const storageStub = stubStorage('getDraftComment');
      const loadSpy = sinon.spy(element, 'loadLocalDraft');

      element.changeNum = 1 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      element.comment = {
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        message: 'This is a message',
        line: 5,
        path: 'test',
        __editing: true,
        __draft: true,
      };
      await flush();
      assert.isTrue(loadSpy.called);
      assert.isFalse(storageStub.called);
    });

    test('message is retrieved from storage for drafts in edit', async () => {
      const storageStub = stubStorage('getDraftComment');
      const loadSpy = sinon.spy(element, 'loadLocalDraft');

      element.changeNum = 1 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      element.comment = {
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        line: 5,
        path: 'test',
        __editing: true,
        __draft: true,
      };
      await flush();
      assert.isTrue(loadSpy.called);
      assert.isTrue(storageStub.called);
    });

    test('comment message sets messageText only when empty', () => {
      element.changeNum = 1 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      element.messageText = '';
      element.comment = {
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        line: 5,
        path: 'test',
        __editing: true,
        __draft: true,
        message: 'hello world',
      };
      // messageText was empty so overwrite the message now
      assert.equal(element.messageText, 'hello world');

      element.comment!.message = 'new message';
      // messageText was already set so do not overwrite it
      assert.equal(element.messageText, 'hello world');
    });

    test('comment message sets messageText when not edited', () => {
      element.changeNum = 1 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      element.messageText = 'Some text';
      element.comment = {
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        line: 5,
        path: 'test',
        __editing: false,
        __draft: true,
        message: 'hello world',
      };
      // messageText was empty so overwrite the message now
      assert.equal(element.messageText, 'hello world');

      element.comment!.message = 'new message';
      // messageText was already set so do not overwrite it
      assert.equal(element.messageText, 'hello world');
    });

    test('getPatchNum', () => {
      element.side = 'PARENT';
      element.patchNum = 1 as PatchSetNum;
      assert.equal(element.getPatchNum(), 'PARENT' as PatchSetNum);
      element.side = 'REVISION';
      assert.equal(element.getPatchNum(), 1 as PatchSetNum);
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
        element.messageText = 'test';
        handleCancelStub = sinon.stub(element, 'handleCancel');
        handleSaveStub = sinon.stub(element, 'handleSave');
        flush();
      });

      suite('when text is empty', () => {
        setup(() => {
          element.messageText = '';
          element.comment = {};
        });

        test('esc closes comment when text is empty', () => {
          pressAndReleaseKeyOn(element.textarea!, 27, null, 'Escape');
          assert.isTrue(handleCancelStub.called);
        });

        test('ctrl+enter does not save', () => {
          pressAndReleaseKeyOn(element.textarea!, 13, 'ctrl', 'Enter');
          assert.isFalse(handleSaveStub.called);
        });

        test('meta+enter does not save', () => {
          pressAndReleaseKeyOn(element.textarea!, 13, 'meta', 'Enter');
          assert.isFalse(handleSaveStub.called);
        });

        test('ctrl+s does not save', () => {
          pressAndReleaseKeyOn(element.textarea!, 83, 'ctrl', 's');
          assert.isFalse(handleSaveStub.called);
        });
      });

      test('esc does not close comment that has content', () => {
        pressAndReleaseKeyOn(element.textarea!, 27, null, 'Escape');
        assert.isFalse(handleCancelStub.called);
      });

      test('ctrl+enter saves', () => {
        pressAndReleaseKeyOn(element.textarea!, 13, 'ctrl', 'Enter');
        assert.isTrue(handleSaveStub.called);
      });

      test('meta+enter saves', () => {
        pressAndReleaseKeyOn(element.textarea!, 13, 'meta', 'Enter');
        assert.isTrue(handleSaveStub.called);
      });

      test('ctrl+s saves', () => {
        pressAndReleaseKeyOn(element.textarea!, 83, 'ctrl', 's');
        assert.isTrue(handleSaveStub.called);
      });
    });

    test('delete comment button for non-admins is hidden', () => {
      element.isAdmin = false;
      assert.isFalse(
        queryAndAssert(element, '.action.delete').classList.contains(
          'showDeleteButtons'
        )
      );
    });

    test('delete comment button for admins with draft is hidden', () => {
      element.isAdmin = false;
      element.draft = true;
      assert.isFalse(
        queryAndAssert(element, '.action.delete').classList.contains(
          'showDeleteButtons'
        )
      );
    });

    test('delete comment', async () => {
      const stub = stubRestApi('deleteComment').returns(
        Promise.resolve(createComment())
      );
      const openSpy = sinon.spy(element.confirmDeleteOverlay!, 'open');
      element.changeNum = 42 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      element.isAdmin = true;
      assert.isTrue(
        queryAndAssert(element, '.action.delete').classList.contains(
          'showDeleteButtons'
        )
      );
      tap(queryAndAssert(element, '.action.delete'));
      await flush();
      await openSpy.lastCall.returnValue;
      const dialog = element.confirmDeleteOverlay?.querySelector(
        '#confirmDeleteComment'
      ) as GrConfirmDeleteCommentDialog;
      dialog.message = 'removal reason';
      element.handleConfirmDeleteComment();
      assert.isTrue(
        stub.calledWith(
          42 as NumericChangeId,
          1 as PatchSetNum,
          'baf0414d_60047215' as UrlEncodedCommentId,
          'removal reason'
        )
      );
    });

    suite('draft update reporting', () => {
      let endStub: SinonStubbedMember<() => Timer>;
      let getTimerStub: sinon.SinonStub;
      const mockEvent = {...new Event('click'), preventDefault() {}};

      setup(() => {
        sinon.stub(element, 'save').returns(Promise.resolve({}));
        endStub = sinon.stub();
        const mockTimer = new MockTimer();
        mockTimer.end = endStub;
        getTimerStub = stubReporting('getTimer').returns(mockTimer);
      });

      test('create', async () => {
        element.patchNum = 1 as PatchSetNum;
        element.comment = {};
        sinon.stub(element, 'discardDraft').returns(Promise.resolve({}));
        await element.handleSave(mockEvent);
        await flush();
        const grAccountLabel = queryAndAssert(element, 'gr-account-label');
        const spanName = queryAndAssert<HTMLSpanElement>(
          grAccountLabel,
          'span.name'
        );
        assert.equal(spanName.innerText.trim(), 'Dhruv Srivastava');
        assert.isTrue(endStub.calledOnce);
        assert.isTrue(getTimerStub.calledOnce);
        assert.equal(getTimerStub.lastCall.args[0], 'CreateDraftComment');
      });

      test('update', () => {
        element.comment = {
          ...createComment(),
          id: 'abc_123' as UrlEncodedCommentId as UrlEncodedCommentId,
        };
        sinon.stub(element, 'discardDraft').returns(Promise.resolve({}));
        return element.handleSave(mockEvent)!.then(() => {
          assert.isTrue(endStub.calledOnce);
          assert.isTrue(getTimerStub.calledOnce);
          assert.equal(getTimerStub.lastCall.args[0], 'UpdateDraftComment');
        });
      });

      test('discard', () => {
        element.comment = {
          ...createComment(),
          id: 'abc_123' as UrlEncodedCommentId as UrlEncodedCommentId,
        };
        element.comment = createDraft();
        sinon.stub(element, 'fireDiscard');
        sinon.stub(element, 'eraseDraftCommentFromStorage');
        sinon
          .stub(element, 'deleteDraft')
          .returns(Promise.resolve(new Response()));
        return element.discardDraft().then(() => {
          assert.isTrue(endStub.calledOnce);
          assert.isTrue(getTimerStub.calledOnce);
          assert.equal(getTimerStub.lastCall.args[0], 'DiscardDraftComment');
        });
      });
    });

    test('edit reports interaction', () => {
      sinon.stub(element, 'fireEdit');
      element.draft = true;
      flush();
      tap(queryAndAssert(element, '.edit'));
    });

    test('discard reports interaction', () => {
      sinon.stub(element, 'eraseDraftCommentFromStorage');
      sinon.stub(element, 'fireDiscard');
      sinon
        .stub(element, 'deleteDraft')
        .returns(Promise.resolve(new Response()));
      element.draft = true;
      element.comment = createDraft();
      flush();
      tap(queryAndAssert(element, '.discard'));
    });

    test('failed save draft request', async () => {
      element.draft = true;
      element.changeNum = 1 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      const updateRequestStub = sinon.stub(element, 'updateRequestToast');
      const diffDraftStub = stubRestApi('saveDiffDraft').returns(
        Promise.resolve({...new Response(), ok: false})
      );
      element.saveDraft({
        ...createComment(),
        id: 'abc_123' as UrlEncodedCommentId,
      });
      await flush();
      let args = updateRequestStub.lastCall.args;
      assert.deepEqual(args, [0, true]);
      assert.equal(
        element.getSavingMessage(...args),
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
      element.saveDraft({
        ...createComment(),
        id: 'abc_123' as UrlEncodedCommentId,
      });
      await flush();
      args = updateRequestStub.lastCall.args;
      assert.deepEqual(args, [0]);
      assert.equal(element.getSavingMessage(...args), 'All changes saved');
      assert.equal(
        (queryAndAssert(element, '.draftLabel') as HTMLSpanElement).innerText,
        'DRAFT'
      );
      assert.isFalse(
        isVisible(queryAndAssert(element, '.save')),
        'save is not visible'
      );
      assert.isFalse(element.unableToSave);
    });

    test('failed save draft request with promise failure', async () => {
      element.draft = true;
      element.changeNum = 1 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      const updateRequestStub = sinon.stub(element, 'updateRequestToast');
      const diffDraftStub = stubRestApi('saveDiffDraft').returns(
        Promise.reject(new Error())
      );
      element.saveDraft({
        ...createComment(),
        id: 'abc_123' as UrlEncodedCommentId,
      });
      await flush();
      let args = updateRequestStub.lastCall.args;
      assert.deepEqual(args, [0, true]);
      assert.equal(
        element.getSavingMessage(...args),
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
      element.saveDraft({
        ...createComment(),
        id: 'abc_123' as UrlEncodedCommentId,
      });
      await flush();
      args = updateRequestStub.lastCall.args;
      assert.deepEqual(args, [0]);
      assert.equal(element.getSavingMessage(...args), 'All changes saved');
      assert.equal(
        (queryAndAssert(element, '.draftLabel') as HTMLSpanElement).innerText,
        'DRAFT'
      );
      assert.isFalse(
        isVisible(queryAndAssert(element, '.save')),
        'save is not visible'
      );
      assert.isFalse(element.unableToSave);
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
                '"id": "baf0414d_40572e03",' +
                '"path": "/path/to/file",' +
                '"line": 5,' +
                '"updated": "2015-12-08 21:52:36.177000000",' +
                '"message": "saved!",' +
                '"side": "REVISION",' +
                '"unresolved": false,' +
                '"patch_set": 1' +
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
        id: undefined,
      };
    });

    test('button visibility states', async () => {
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
      await flush();
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
      await flush();
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
      await flush();
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
      await flush();
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
      await flush();
      assert.isTrue(
        queryAndAssert(element, '.robotRun.link').textContent === 'Run Details'
      );

      // A robot comment with run ID and url should display a link.
      element.set(['comment', 'url'], '/path/to/run');
      await flush();
      assert.notEqual(
        getComputedStyle(queryAndAssert(element, '.robotRun.link')).display,
        'none'
      );

      // Delete button is hidden for robot comments
      assert.isTrue(
        (queryAndAssert(element, '#deleteBtn') as HTMLElement).hidden
      );
    });

    test('collapsible drafts', async () => {
      const fireEditStub = sinon.stub(element, 'fireEdit');
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
      await flush();
      tap(queryAndAssert(element, '.edit'));
      await flush();
      assert.isTrue(fireEditStub.called);
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

    test('robot comment layout', async () => {
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
      await flush;
      let runIdMessage;
      runIdMessage = queryAndAssert(element, '.runIdMessage') as HTMLElement;
      assert.isFalse((runIdMessage as HTMLElement).hidden);

      const runDetailsLink = queryAndAssert(
        element,
        '.robotRunLink'
      ) as HTMLAnchorElement;
      assert.isTrue(
        runDetailsLink.href.indexOf(
          (element.comment as RobotCommentInfo).url!
        ) !== -1
      );

      const robotServiceName = queryAndAssert(element, '.robotName');
      assert.equal(robotServiceName.textContent?.trim(), 'happy_robot_id');

      const authorName = queryAndAssert(element, '.robotId');
      assert.isTrue((authorName as HTMLDivElement).innerText === 'Happy Robot');

      element.collapsed = true;
      await flush();
      runIdMessage = queryAndAssert(element, '.runIdMessage');
      assert.isTrue((runIdMessage as HTMLDivElement).hidden);
    });

    test('author name fallback to email', async () => {
      const comment = {
        url: '/robot/comment',
        author: {
          email: 'test@test.com' as EmailAddress,
        },
        ...element.comment,
      };
      element.comment = comment;
      element.collapsed = false;
      await flush();
      const authorName = queryAndAssert(
        queryAndAssert(element, 'gr-account-label'),
        'span.name'
      ) as HTMLSpanElement;
      assert.equal(authorName.innerText.trim(), 'test@test.com');
    });

    test('patchset level comment', async () => {
      const fireEditStub = sinon.stub(element, 'fireEdit');
      const comment = {
        ...element.comment,
        path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
        line: undefined,
        range: undefined,
      };
      element.comment = comment;
      await flush();
      tap(queryAndAssert(element, '.edit'));
      assert.isTrue(fireEditStub.called);
      assert.isTrue(element.editing);

      element.messageText = 'hello world';
      const eraseMessageDraftSpy = spyStorage('eraseDraftComment');
      const mockEvent = {...new Event('click'), preventDefault: sinon.stub()};
      element.handleSave(mockEvent);
      await flush();
      assert.isTrue(eraseMessageDraftSpy.called);
    });

    test('draft creation/cancellation', async () => {
      const fireEditStub = sinon.stub(element, 'fireEdit');
      assert.isFalse(element.editing);
      element.draft = true;
      await flush();
      tap(queryAndAssert(element, '.edit'));
      assert.isTrue(fireEditStub.called);
      assert.isTrue(element.editing);

      element.comment!.message = '';
      element.messageText = '';
      const eraseMessageDraftSpy = sinon.spy(
        element,
        'eraseDraftCommentFromStorage'
      );

      // Save should be disabled on an empty message.
      let disabled = queryAndAssert(element, '.save').hasAttribute('disabled');
      assert.isTrue(disabled, 'save button should be disabled.');
      element.messageText = '     ';
      disabled = queryAndAssert(element, '.save').hasAttribute('disabled');
      assert.isTrue(disabled, 'save button should be disabled.');

      const updateStub = sinon.stub();
      element.addEventListener('comment-update', updateStub);

      let numDiscardEvents = 0;
      const promise = mockPromise();
      element.addEventListener('comment-discard', () => {
        numDiscardEvents++;
        assert.isFalse(eraseMessageDraftSpy.called);
        if (numDiscardEvents === 2) {
          assert.isFalse(updateStub.called);
          promise.resolve();
        }
      });
      tap(queryAndAssert(element, '.cancel'));
      await flush();
      element.messageText = '';
      element.editing = true;
      await flush();
      pressAndReleaseKeyOn(element.textarea!, 27, null, 'Escape');
      await promise;
    });

    test('draft discard removes message from storage', async () => {
      element.messageText = '';
      const eraseMessageDraftSpy = sinon.spy(
        element,
        'eraseDraftCommentFromStorage'
      );

      const promise = mockPromise();
      element.addEventListener('comment-discard', () => {
        assert.isTrue(eraseMessageDraftSpy.called);
        promise.resolve();
      });
      element.handleDiscard({
        ...new Event('click'),
        preventDefault: sinon.stub(),
      });
      await promise;
    });

    test('storage is cleared only after save success', () => {
      element.messageText = 'test';
      const eraseStub = sinon.stub(element, 'eraseDraftCommentFromStorage');
      stubRestApi('getResponseObject').returns(
        Promise.resolve({...(createDraft() as ParsedJSON)})
      );
      const saveDraftStub = sinon
        .stub(element, 'saveDraft')
        .returns(Promise.resolve({...new Response(), ok: false}));

      const savePromise = element.save();
      assert.isFalse(eraseStub.called);
      return savePromise.then(() => {
        assert.isFalse(eraseStub.called);

        saveDraftStub.restore();
        sinon
          .stub(element, 'saveDraft')
          .returns(Promise.resolve({...new Response(), ok: true}));
        return element.save().then(() => {
          assert.isTrue(eraseStub.called);
        });
      });
    });

    test('computeSaveDisabled', () => {
      const comment = {unresolved: true};
      const msgComment = {message: 'test', unresolved: true};
      assert.equal(element.computeSaveDisabled('', comment, false), true);
      assert.equal(element.computeSaveDisabled('test', comment, false), false);
      assert.equal(element.computeSaveDisabled('', msgComment, false), true);
      assert.equal(
        element.computeSaveDisabled('test', msgComment, false),
        false
      );
      assert.equal(
        element.computeSaveDisabled('test2', msgComment, false),
        false
      );
      assert.equal(element.computeSaveDisabled('test', comment, true), false);
      assert.equal(element.computeSaveDisabled('', comment, true), true);
      assert.equal(element.computeSaveDisabled('', comment, false), true);
    });

    test('ctrl+s saves comment', async () => {
      const promise = mockPromise();
      const stub = sinon.stub(element, 'save').callsFake(() => {
        assert.isTrue(stub.called);
        stub.restore();
        promise.resolve();
        return Promise.resolve();
      });
      element.messageText = 'is that the horse from horsing around??';
      element.editing = true;
      await flush();
      pressAndReleaseKeyOn(
        element.textarea!.$.textarea.textarea,
        83,
        'ctrl',
        's'
      );
      await promise;
    });

    test('draft saving/editing', async () => {
      const dispatchEventStub = sinon.stub(element, 'dispatchEvent');
      const fireEditStub = sinon.stub(element, 'fireEdit');
      const clock: SinonFakeTimers = sinon.useFakeTimers();
      const tickAndFlush = async (repetitions: number) => {
        for (let i = 1; i <= repetitions; i++) {
          clock.tick(1000);
          await flush();
        }
      };

      element.draft = true;
      await flush();
      tap(queryAndAssert(element, '.edit'));
      assert.isTrue(fireEditStub.called);
      tickAndFlush(1);
      element.messageText = 'good news, everyone!';
      tickAndFlush(1);
      assert.equal(dispatchEventStub.lastCall.args[0].type, 'comment-update');
      assert.isTrue(dispatchEventStub.calledTwice);

      element.messageText = 'good news, everyone!';
      await flush();
      assert.isTrue(dispatchEventStub.calledTwice);

      tap(queryAndAssert(element, '.save'));

      assert.isTrue(
        element.disabled,
        'Element should be disabled when creating draft.'
      );

      let draft = await element.xhrPromise!;
      const evt = dispatchEventStub.lastCall.args[0] as CustomEvent<{
        comment: DraftInfo;
      }>;
      assert.equal(evt.type, 'comment-save');

      const expectedDetail = {
        comment: {
          ...createComment(),
          __draft: true,
          __draftID: 'temp_draft_id',
          id: 'baf0414d_40572e03' as UrlEncodedCommentId,
          line: 5,
          message: 'saved!',
          path: '/path/to/file',
          updated: '2015-12-08 21:52:36.177000000' as Timestamp,
        },
        patchNum: 1 as PatchSetNum,
      };

      assert.deepEqual(evt.detail, expectedDetail);
      assert.isFalse(
        element.disabled,
        'Element should be enabled when done creating draft.'
      );
      assert.equal(draft.message, 'saved!');
      assert.isFalse(element.editing);
      tap(queryAndAssert(element, '.edit'));
      assert.isTrue(fireEditStub.calledTwice);
      element.messageText =
        'You’ll be delivering a package to Chapek 9, ' +
        'a world where humans are killed on sight.';
      tap(queryAndAssert(element, '.save'));
      assert.isTrue(
        element.disabled,
        'Element should be disabled when updating draft.'
      );
      draft = await element.xhrPromise!;
      assert.isFalse(
        element.disabled,
        'Element should be enabled when done updating draft.'
      );
      assert.equal(draft.message, 'saved!');
      assert.isFalse(element.editing);
      dispatchEventStub.restore();
    });

    test('draft prevent save when disabled', async () => {
      const saveStub = sinon.stub(element, 'save').returns(Promise.resolve());
      sinon.stub(element, '_fireEdit');
      element.showActions = true;
      element.draft = true;
      await flush();
      tap(element.$.header);
      tap(queryAndAssert(element, '.edit'));
      element.messageText = 'good news, everyone!';
      await flush();

      element.disabled = true;
      tap(queryAndAssert(element, '.save'));
      assert.isFalse(saveStub.called);

      element.disabled = false;
      tap(queryAndAssert(element, '.save'));
      assert.isTrue(saveStub.calledOnce);
    });

    test('proper event fires on resolve, comment is not saved', async () => {
      const save = sinon.stub(element, 'save');
      const promise = mockPromise();
      element.addEventListener('comment-update', e => {
        assert.isTrue(e.detail.comment.unresolved);
        assert.isFalse(save.called);
        promise.resolve();
      });
      tap(queryAndAssert(element, '.resolve input'));
      await promise;
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
      test('getSavingMessage', () => {
        assert.equal(element.getSavingMessage(0), 'All changes saved');
        assert.equal(element.getSavingMessage(1), 'Saving 1 draft...');
        assert.equal(element.getSavingMessage(2), 'Saving 2 drafts...');
        assert.equal(element.getSavingMessage(3), 'Saving 3 drafts...');
      });

      test('show{Start,End}Request', () => {
        const updateStub = sinon.stub(element, 'updateRequestToast');
        element.numPendingDraftRequests.number = 1;

        element.showStartRequest();
        assert.isTrue(updateStub.calledOnce);
        assert.equal(updateStub.lastCall.args[0], 2);
        assert.equal(element.numPendingDraftRequests.number, 2);

        element.showEndRequest();
        assert.isTrue(updateStub.calledTwice);
        assert.equal(updateStub.lastCall.args[0], 1);
        assert.equal(element.numPendingDraftRequests.number, 1);

        element.showEndRequest();
        assert.isTrue(updateStub.calledThrice);
        assert.equal(updateStub.lastCall.args[0], 0);
        assert.equal(element.numPendingDraftRequests.number, 0);
      });
    });

    test('cancelling an unsaved draft discards, persists in storage', async () => {
      const clock: SinonFakeTimers = sinon.useFakeTimers();
      const tickAndFlush = async (repetitions: number) => {
        for (let i = 1; i <= repetitions; i++) {
          clock.tick(1000);
          await flush();
        }
      };
      const discardSpy = sinon.spy(element, 'fireDiscard');
      const storeStub = stubStorage('setDraftComment');
      const eraseStub = stubStorage('eraseDraftComment');
      element.comment!.id = undefined; // set id undefined for draft
      element.messageText = 'test text';
      tickAndFlush(1);

      assert.isTrue(storeStub.called);
      assert.equal(storeStub.lastCall.args[1], 'test text');
      element.handleCancel({
        ...new Event('click'),
        preventDefault: sinon.stub(),
      });
      await flush();
      assert.isTrue(discardSpy.called);
      assert.isFalse(eraseStub.called);
    });

    test('cancelling edit on a saved draft does not store', () => {
      element.comment!.id = 'foo' as UrlEncodedCommentId;
      const discardSpy = sinon.spy(element, 'fireDiscard');
      const storeStub = stubStorage('setDraftComment');
      element.comment!.id = undefined; // set id undefined for draft
      element.messageText = 'test text';
      flush();

      assert.isFalse(storeStub.called);
      element.handleCancel({...new Event('click'), preventDefault: () => {}});
      assert.isTrue(discardSpy.called);
    });

    test('deleting text from saved draft and saving deletes the draft', () => {
      element.comment = {
        ...createComment(),
        id: 'foo' as UrlEncodedCommentId,
        message: 'test',
      };
      element.messageText = '';
      const discardStub = sinon.stub(element, 'discardDraft');

      element.save();
      assert.isTrue(discardStub.called);
    });

    test('handleFix fires create-fix event', async () => {
      const promise = mockPromise();
      element.addEventListener(
        'create-fix-comment',
        (e: CreateFixCommentEvent) => {
          assert.deepEqual(e.detail, element.getEventPayload());
          promise.resolve();
        }
      );
      element.isRobotComment = true;
      element.comments = [element.comment!];
      await flush();

      tap(queryAndAssert(element, '.fix'));
      await promise;
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

    test('handleShowFix fires open-fix-preview event', async () => {
      const promise = mockPromise();
      element.addEventListener('open-fix-preview', e => {
        assert.deepEqual(e.detail, element.getEventPayload());
        promise.resolve();
      });
      element.comment = {
        ...createComment(),
        fix_suggestions: [{...createFixSuggestionInfo()}],
      };
      element.isRobotComment = true;
      await flush();

      tap(queryAndAssert(element, '.show-fix'));
      await promise;
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

    test('show tip when no cached record', async () => {
      element = draftFixture.instantiate() as GrComment;
      const respectfulGetStub = stubStorage('getRespectfulTipVisibility');
      const respectfulSetStub = stubStorage('setRespectfulTipVisibility');
      respectfulGetStub.returns(null);
      // fake random
      element.getRandomNum = () => 0;
      element.comment = {__editing: true, __draft: true};
      await flush();
      assert.isTrue(respectfulGetStub.called);
      assert.isTrue(respectfulSetStub.called);
      assert.isTrue(!!queryAndAssert(element, '.respectfulReviewTip'));
    });

    test('add 14-day delays once dismissed', async () => {
      element = draftFixture.instantiate() as GrComment;
      const respectfulGetStub = stubStorage('getRespectfulTipVisibility');
      const respectfulSetStub = stubStorage('setRespectfulTipVisibility');
      respectfulGetStub.returns(null);
      // fake random
      element.getRandomNum = () => 0;
      element.comment = {__editing: true, __draft: true};
      await flush();
      assert.isTrue(respectfulGetStub.called);
      assert.isTrue(respectfulSetStub.called);
      assert.isTrue(respectfulSetStub.lastCall.args[0] === undefined);
      assert.isTrue(!!queryAndAssert(element, '.respectfulReviewTip'));

      tap(queryAndAssert(element, '.respectfulReviewTip .close'));
      flush();
      assert.isTrue(respectfulSetStub.lastCall.args[0] === 14);
    });

    test('do not show tip when fall out of probability', async () => {
      element = draftFixture.instantiate() as GrComment;
      const respectfulGetStub = stubStorage('getRespectfulTipVisibility');
      const respectfulSetStub = stubStorage('setRespectfulTipVisibility');
      respectfulGetStub.returns(null);
      // fake random
      element.getRandomNum = () => 3;
      element.comment = {__editing: true, __draft: true};
      await flush();
      assert.isTrue(respectfulGetStub.called);
      assert.isFalse(respectfulSetStub.called);
      assert.isNotOk(query(element, '.respectfulReviewTip'));
    });

    test('show tip when editing changed to true', async () => {
      element = draftFixture.instantiate() as GrComment;
      const respectfulGetStub = stubStorage('getRespectfulTipVisibility');
      const respectfulSetStub = stubStorage('setRespectfulTipVisibility');
      respectfulGetStub.returns(null);
      // fake random
      element.getRandomNum = () => 0;
      element.comment = {__editing: false};
      await flush();
      assert.isFalse(respectfulGetStub.called);
      assert.isFalse(respectfulSetStub.called);
      assert.isNotOk(query(element, '.respectfulReviewTip'));

      element.editing = true;
      await flush();
      assert.isTrue(respectfulGetStub.called);
      assert.isTrue(respectfulSetStub.called);
      assert.isTrue(!!queryAndAssert(element, '.respectfulReviewTip'));
    });

    test('no tip when cached record', async () => {
      element = draftFixture.instantiate() as GrComment;
      const respectfulGetStub = stubStorage('getRespectfulTipVisibility');
      const respectfulSetStub = stubStorage('setRespectfulTipVisibility');
      respectfulGetStub.returns({updated: 0});
      // fake random
      element.getRandomNum = () => 0;
      element.comment = {__editing: true, __draft: true};
      await flush();
      assert.isTrue(respectfulGetStub.called);
      assert.isFalse(respectfulSetStub.called);
      assert.isNotOk(query(element, '.respectfulReviewTip'));
    });
  });
});
