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
import {CommentSide} from '../../../constants/constants';
import {
  queryAndAssert,
  stubRestApi,
  stubStorage,
  query,
  isVisible,
  mockPromise,
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
  createDraft,
  createFixSuggestionInfo,
  createRobotComment,
} from '../../../test/test-data-generators';
import {SinonFakeTimers} from 'sinon';
import {CreateFixCommentEvent} from '../../../types/events';
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
          avatars: [{ url: 'abc', height: 32, width: 32 }],
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
      tap(queryAndAssert(element, '#header'));
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
      const stub = sinon.stub();
      element.addEventListener('comment-anchor-tap', stub);
      flush();
      const dateEl = queryAndAssert(element, '.date');
      assert.ok(dateEl);
      tap(dateEl);

      assert.isTrue(stub.called);
      assert.deepEqual(stub.lastCall.args[0].detail, {
        side: 'PARENT',
        number: element.comment!.line,
      });
    });

    test('comment message sets messageText only when empty', () => {
      element.changeNum = 1 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      element.messageText = '';
      element.comment = {
        ...createComment(),
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        line: 5,
        path: 'test',
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
        ...createComment(),
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        line: 5,
        path: 'test',
        __draft: true,
        message: 'hello world',
      };
      // messageText was empty so overwrite the message now
      assert.equal(element.messageText, 'hello world');

      element.comment!.message = 'new message';
      // messageText was already set so do not overwrite it
      assert.equal(element.messageText, 'hello world');
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

    test('delete comment button for admins with draft is hidden', () => {
      element.isAdmin = false;
      element.comment = createDraft();
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

    suite('gr-comment draft tests', () => {
      let element: GrComment;

      setup(() => {
        element = draftFixture.instantiate() as GrComment;
        element.changeNum = 42 as NumericChangeId;
        element.patchNum = 1 as PatchSetNum;
        element.editing = false;
        element.comment = {
          ...createComment(),
          __draft: true,
          path: '/path/to/file',
          line: 5,
        };
      });

      test('author name fallback to email', async () => {
        const comment = {
          ...createComment(),
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

      test('isSaveDisabled', () => {
        element.saving = false;
        element.resolved = false;
        element.comment = { ...createComment(), unresolved: true };
        element.messageText = 'asdf';
        assert.isFalse(element.isSaveDisabled());

        element.messageText = '';
        assert.isTrue(element.isSaveDisabled());

        element.resolved = true;
        assert.isFalse(element.isSaveDisabled());

        element.saving = true;
        assert.isTrue(element.isSaveDisabled());
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

      test('save', async () => {
        // TODO: Write a test for save.
      });

      test('cancel', async () => {
        // TODO: Write a test for cancel.
      });

      test('discard', async () => {
        // TODO: Write a test for discard.
      });

      test('collapse', async () => {
        // TODO: Write a test for collapsed and initiallyCollapsed.
      });

      test('resolved comment state indicated by checkbox', () => {
        sinon.stub(element, 'save');
        element.comment = { ...createComment(), unresolved: false };
        assert.isTrue(
          (queryAndAssert(element, '.resolve input') as HTMLInputElement).checked
        );
        element.comment = { ...createComment(), unresolved: true };
        assert.isFalse(
          (queryAndAssert(element, '.resolve input') as HTMLInputElement).checked
        );
      });

      test('resolved checkbox saves with tap when !editing', () => {
        element.editing = false;
        const save = sinon.stub(element, 'save');

        element.comment = { ...createComment(), unresolved: false };
        assert.isTrue(
          (queryAndAssert(element, '.resolve input') as HTMLInputElement).checked
        );
        element.comment = { ...createComment(), unresolved: true };
        assert.isFalse(
          (queryAndAssert(element, '.resolve input') as HTMLInputElement).checked
        );
        assert.isFalse(save.called);
        tap(queryAndAssert(element, '#resolvedCheckbox'));
        assert.isTrue(
          (queryAndAssert(element, '.resolve input') as HTMLInputElement).checked
        );
        assert.isTrue(save.called);
      });

      test('cancelling an unsaved draft discards, persists in storage', async () => {
        const clock: SinonFakeTimers = sinon.useFakeTimers();
        const tickAndFlush = async (repetitions: number) => {
          for (let i = 1; i <= repetitions; i++) {
            clock.tick(1000);
            await flush();
          }
        };
        const storeStub = stubStorage('setDraftComment');
        const eraseStub = stubStorage('eraseDraftComment');
        element.comment!.id = undefined; // set id undefined for draft
        element.messageText = 'test text';
        tickAndFlush(1);

        assert.isTrue(storeStub.called);
        assert.equal(storeStub.lastCall.args[1], 'test text');
        element.cancel();
        await flush();
        assert.isFalse(eraseStub.called);
      });

      test('deleting text from saved draft and saving deletes the draft', () => {
        element.comment = {
          ...createComment(),
          id: 'foo' as UrlEncodedCommentId,
          message: 'test',
        };
        element.messageText = '';
        const discardStub = sinon.stub(element, 'discard');

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
        element.comment = createRobotComment();
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
          },
          {
            __draft: true,
            id: '123_asdf' as UrlEncodedCommentId,
            updated: '2017-04-05 15:36:17.000000000' as Timestamp,
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
          ...createRobotComment(),
          fix_suggestions: [{ ...createFixSuggestionInfo() }],
        };
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
        element.comment = createDraft();
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
        element.comment = createDraft();
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
        element.comment = createDraft();
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
        element.comment = createComment();
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
        respectfulGetStub.returns({ updated: 0 });
        // fake random
        element.getRandomNum = () => 0;
        element.comment = createDraft();
        await flush();
        assert.isTrue(respectfulGetStub.called);
        assert.isFalse(respectfulSetStub.called);
        assert.isNotOk(query(element, '.respectfulReviewTip'));
      });
    });
  });
});