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

import '../../../test/common-test-setup-karma.js';
import './gr-comment-thread.js';
import {GerritNav} from '../../core/gr-navigation/gr-navigation.js';
import {SpecialFilePath, Side} from '../../../constants/constants.js';
import {
  sortComments,
  UIComment,
  UIRobot,
  isDraft,
  UIDraft,
} from '../../../utils/comment-util.js';
import {GrCommentThread} from './gr-comment-thread.js';
import {
  PatchSetNum,
  NumericChangeId,
  UrlEncodedCommentId,
  Timestamp,
  RobotId,
  RobotRunId,
  RepoName,
  ConfigInfo,
  EmailAddress,
} from '../../../types/common.js';
import {GrComment} from '../gr-comment/gr-comment.js';
import {LineNumber} from '../../diff/gr-diff/gr-diff-line.js';
import {
  tap,
  pressAndReleaseKeyOn,
} from '@polymer/iron-test-helpers/mock-interactions';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';
import {stubRestApi} from '../../../test/test-utils';

const basicFixture = fixtureFromElement('gr-comment-thread');

const withCommentFixture = fixtureFromElement('gr-comment-thread');

suite('gr-comment-thread tests', () => {
  suite('basic test', () => {
    let element: GrCommentThread;

    setup(() => {
      stubRestApi('getLoggedIn').returns(Promise.resolve(false));

      element = basicFixture.instantiate();
      element.patchNum = 3 as PatchSetNum;
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
      const comments: UIComment[] = [
        {
          message: 'i like you, too',
          in_reply_to: 'sallys_confession' as UrlEncodedCommentId,
          __date: new Date('2015-12-25'),
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
          message: 'i like you, too' as UrlEncodedCommentId,
          in_reply_to: 'sallys_confession' as UrlEncodedCommentId,
          __date: new Date('2015-12-25'),
        },
      ]);
    });

    test('addOrEditDraft w/ edit draft', () => {
      element.comments = [
        {
          id: 'jacks_reply' as UrlEncodedCommentId,
          message: 'i like you, too',
          in_reply_to: 'sallys_confession' as UrlEncodedCommentId,
          updated: '2015-12-25 15:00:20.396000000' as Timestamp,
          __draft: true,
        },
      ];
      const commentElStub = sinon
        .stub(element, '_commentElWithDraftID')
        .callsFake(() => {
          return new GrComment();
        });
      const addDraftStub = sinon.stub(element, 'addDraft');

      element.addOrEditDraft(123);

      assert.isTrue(commentElStub.called);
      assert.isFalse(addDraftStub.called);
    });

    test('addOrEditDraft w/o edit draft', () => {
      element.comments = [];
      const commentElStub = sinon
        .stub(element, '_commentElWithDraftID')
        .callsFake(() => {
          return new GrComment();
        });
      const addDraftStub = sinon.stub(element, 'addDraft');

      element.addOrEditDraft(123);

      assert.isFalse(commentElStub.called);
      assert.isTrue(addDraftStub.called);
    });

    test('_shouldDisableAction', () => {
      let showActions = true;
      const lastComment: UIComment = {};
      assert.equal(
        element._shouldDisableAction(showActions, lastComment),
        false
      );
      showActions = false;
      assert.equal(
        element._shouldDisableAction(showActions, lastComment),
        true
      );
      showActions = true;
      lastComment.__draft = true;
      assert.equal(
        element._shouldDisableAction(showActions, lastComment),
        true
      );
      const robotComment: UIRobot = {
        id: '1234' as UrlEncodedCommentId,
        updated: '1234' as Timestamp,
        robot_id: 'robot_id' as RobotId,
        robot_run_id: 'robot_run_id' as RobotRunId,
        properties: {},
        fix_suggestions: [],
      };
      assert.equal(
        element._shouldDisableAction(showActions, robotComment),
        false
      );
    });

    test('_hideActions', () => {
      let showActions = true;
      const lastComment: UIComment = {};
      assert.equal(element._hideActions(showActions, lastComment), false);
      showActions = false;
      assert.equal(element._hideActions(showActions, lastComment), true);
      showActions = true;
      lastComment.__draft = true;
      assert.equal(element._hideActions(showActions, lastComment), true);
      const robotComment: UIRobot = {
        id: '1234' as UrlEncodedCommentId,
        updated: '1234' as Timestamp,
        robot_id: 'robot_id' as RobotId,
        robot_run_id: 'robot_run_id' as RobotRunId,
        properties: {},
        fix_suggestions: [],
      };
      assert.equal(element._hideActions(showActions, robotComment), true);
    });

    test('setting project name loads the project config', done => {
      const projectName = 'foo/bar/baz' as RepoName;
      const getProjectStub = stubRestApi('getProjectConfig').returns(
        Promise.resolve({} as ConfigInfo)
      );
      element.projectName = projectName;
      flush(() => {
        assert.isTrue(getProjectStub.calledWithExactly(projectName as never));
        done();
      });
    });

    test('optionally show file path', () => {
      // Path info doesn't exist when showFilePath is false. Because it's in a
      // dom-if it is not yet in the dom.
      assert.isNotOk(element.shadowRoot?.querySelector('.pathInfo'));

      const commentStub = sinon.stub(GerritNav, 'getUrlForComment');
      element.changeNum = 123 as NumericChangeId;
      element.projectName = 'test project' as RepoName;
      element.path = 'path/to/file';
      element.patchNum = 3 as PatchSetNum;
      element.lineNum = 5;
      element.comments = [{id: 'comment_id' as UrlEncodedCommentId}];
      element.showFilePath = true;
      flush();
      assert.isOk(element.shadowRoot?.querySelector('.pathInfo'));
      assert.notEqual(
        getComputedStyle(element.shadowRoot!.querySelector('.pathInfo')!)
          .display,
        'none'
      );
      assert.isTrue(
        commentStub.calledWithExactly(
          element.changeNum,
          element.projectName,
          'comment_id' as UrlEncodedCommentId
        )
      );
    });

    test('_computeDisplayPath', () => {
      let path = 'path/to/file';
      assert.equal(element._computeDisplayPath(path), 'path/to/file');

      element.lineNum = 5;
      assert.equal(element._computeDisplayPath(path), 'path/to/file');

      element.patchNum = 3 as PatchSetNum;
      path = SpecialFilePath.PATCHSET_LEVEL_COMMENTS;
      assert.equal(element._computeDisplayPath(path), 'Patchset');
    });

    test('_computeDisplayLine', () => {
      element.lineNum = 5;
      assert.equal(element._computeDisplayLine(), '#5');

      element.path = SpecialFilePath.COMMIT_MESSAGE;
      element.lineNum = 5;
      assert.equal(element._computeDisplayLine(), '#5');

      element.lineNum = undefined;
      element.path = SpecialFilePath.PATCHSET_LEVEL_COMMENTS;
      assert.equal(element._computeDisplayLine(), '');
    });
  });
});

suite('comment action tests with unresolved thread', () => {
  let element: GrCommentThread;

  setup(() => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    stubRestApi('saveDiffDraft').returns(
      Promise.resolve(({
        headers: {} as Headers,
        redirected: false,
        status: 200,
        statusText: '',
        type: '' as ResponseType,
        url: '',
        ok: true,
        text() {
          return Promise.resolve(
            ")]}'\n" +
              JSON.stringify({
                id: '7afa4931_de3d65bd',
                path: '/path/to/file.txt',
                line: 5,
                in_reply_to: 'baf0414d_60047215' as UrlEncodedCommentId,
                updated: '2015-12-21 02:01:10.850000000',
                message: 'Done',
              })
          );
        },
      } as unknown) as Response)
    );
    stubRestApi('deleteDiffDraft').returns(
      Promise.resolve(({ok: true} as unknown) as Response)
    );
    element = withCommentFixture.instantiate();
    element.patchNum = 1 as PatchSetNum;
    element.changeNum = 1 as NumericChangeId;
    element.comments = [
      {
        author: {
          name: 'Mr. Peanutbutter',
          email: ('tenn1sballchaser@aol.com' as EmailAddress) as EmailAddress,
        },
        id: 'baf0414d_60047215' as UrlEncodedCommentId,
        line: 5,
        message: 'is this a crossover episode!?',
        updated: '2015-12-08 19:48:33.843000000' as Timestamp,
        path: '/path/to/file.txt',
        unresolved: true,
        patch_set: 3 as PatchSetNum,
      },
    ];
    flush();
  });

  test('reply', () => {
    const commentEl = element.shadowRoot?.querySelector('gr-comment');
    const reportStub = sinon.stub(element.reporting, 'recordDraftInteraction');
    assert.ok(commentEl);

    const replyBtn = element.$.replyBtn;
    tap(replyBtn);
    flush();

    const drafts = element._orderedComments.filter(c => isDraft(c));
    assert.equal(drafts.length, 1);
    assert.notOk(drafts[0].message, 'message should be empty');
    assert.equal(
      drafts[0].in_reply_to,
      ('baf0414d_60047215' as UrlEncodedCommentId) as UrlEncodedCommentId
    );
    assert.isTrue(reportStub.calledOnce);
  });

  test('quote reply', () => {
    const commentEl = element.shadowRoot?.querySelector('gr-comment');
    const reportStub = sinon.stub(element.reporting, 'recordDraftInteraction');
    assert.ok(commentEl);

    const quoteBtn = element.$.quoteBtn;
    tap(quoteBtn);
    flush();

    const drafts = element._orderedComments.filter(c => isDraft(c));
    assert.equal(drafts.length, 1);
    assert.equal(drafts[0].message, '> is this a crossover episode!?\n\n');
    assert.equal(
      drafts[0].in_reply_to,
      ('baf0414d_60047215' as UrlEncodedCommentId) as UrlEncodedCommentId
    );
    assert.isTrue(reportStub.calledOnce);
  });

  test('quote reply multiline', () => {
    const reportStub = sinon.stub(element.reporting, 'recordDraftInteraction');
    element.comments = [
      {
        author: {
          name: 'Mr. Peanutbutter',
          email: ('tenn1sballchaser@aol.com' as EmailAddress) as EmailAddress,
        },
        id: 'baf0414d_60047215' as UrlEncodedCommentId,
        path: 'test',
        line: 5,
        message: 'is this a crossover episode!?\nIt might be!',
        updated: '2015-12-08 19:48:33.843000000' as Timestamp,
      },
    ];
    flush();

    const commentEl = element.shadowRoot?.querySelector('gr-comment');
    assert.ok(commentEl);

    const quoteBtn = element.$.quoteBtn;
    tap(quoteBtn);
    flush();

    const drafts = element._orderedComments.filter(c => isDraft(c));
    assert.equal(drafts.length, 1);
    assert.equal(
      drafts[0].message,
      '> is this a crossover episode!?\n> It might be!\n\n'
    );
    assert.equal(
      drafts[0].in_reply_to,
      'baf0414d_60047215' as UrlEncodedCommentId
    );
    assert.isTrue(reportStub.calledOnce);
  });

  test('ack', done => {
    const reportStub = sinon.stub(element.reporting, 'recordDraftInteraction');
    element.changeNum = 42 as NumericChangeId;
    element.patchNum = 1 as PatchSetNum;

    const commentEl = element.shadowRoot?.querySelector('gr-comment');
    assert.ok(commentEl);

    const ackBtn = element.shadowRoot?.querySelector('#ackBtn');
    assert.isOk(ackBtn);
    tap(ackBtn!);
    flush(() => {
      const drafts = element.comments.filter(c => isDraft(c));
      assert.equal(drafts.length, 1);
      assert.equal(drafts[0].message, 'Ack');
      assert.equal(
        drafts[0].in_reply_to,
        'baf0414d_60047215' as UrlEncodedCommentId
      );
      assert.equal(drafts[0].unresolved, false);
      assert.isTrue(reportStub.calledOnce);
      done();
    });
  });

  test('done', done => {
    const reportStub = sinon.stub(element.reporting, 'recordDraftInteraction');
    element.changeNum = 42 as NumericChangeId;
    element.patchNum = 1 as PatchSetNum;
    const commentEl = element.shadowRoot?.querySelector('gr-comment');
    assert.ok(commentEl);

    const doneBtn = element.shadowRoot?.querySelector('#doneBtn');
    assert.isOk(doneBtn);
    tap(doneBtn!);
    flush(() => {
      const drafts = element.comments.filter(c => isDraft(c));
      assert.equal(drafts.length, 1);
      assert.equal(drafts[0].message, 'Done');
      assert.equal(
        drafts[0].in_reply_to,
        'baf0414d_60047215' as UrlEncodedCommentId
      );
      assert.isFalse(drafts[0].unresolved);
      assert.isTrue(reportStub.calledOnce);
      done();
    });
  });

  test('save', done => {
    element.changeNum = 42 as NumericChangeId;
    element.patchNum = 1 as PatchSetNum;
    element.path = '/path/to/file.txt';
    const commentEl = element.shadowRoot?.querySelector('gr-comment');
    assert.ok(commentEl);

    const saveOrDiscardStub = sinon.stub();
    element.addEventListener('thread-changed', saveOrDiscardStub);
    element.shadowRoot?.querySelector('gr-comment')?._fireSave();

    flush(() => {
      assert.isTrue(saveOrDiscardStub.called);
      assert.equal(
        saveOrDiscardStub.lastCall.args[0].detail.rootId,
        'baf0414d_60047215'
      );
      assert.equal(element.rootId, 'baf0414d_60047215' as UrlEncodedCommentId);
      assert.equal(
        saveOrDiscardStub.lastCall.args[0].detail.path,
        '/path/to/file.txt'
      );
      done();
    });
  });

  test('please fix', done => {
    element.changeNum = 42 as NumericChangeId;
    element.patchNum = 1 as PatchSetNum;
    const commentEl = element.shadowRoot?.querySelector('gr-comment');
    assert.ok(commentEl);
    commentEl!.addEventListener('create-fix-comment', () => {
      const drafts = element._orderedComments.filter(c => isDraft(c));
      assert.equal(drafts.length, 1);
      assert.equal(
        drafts[0].message,
        '> is this a crossover episode!?\n\nPlease fix.'
      );
      assert.equal(
        drafts[0].in_reply_to,
        'baf0414d_60047215' as UrlEncodedCommentId
      );
      assert.isTrue(drafts[0].unresolved);
      done();
    });
    commentEl!.dispatchEvent(
      new CustomEvent('create-fix-comment', {
        detail: {comment: commentEl!.comment},
        composed: true,
        bubbles: false,
      })
    );
  });

  test('discard', done => {
    element.changeNum = 42 as NumericChangeId;
    element.patchNum = 1 as PatchSetNum;
    element.path = '/path/to/file.txt';
    assert.isOk(element.comments[0]);
    element.push(
      'comments',
      element._newReply(
        element.comments[0]!.id as UrlEncodedCommentId,
        'it’s pronouced jiff, not giff'
      )
    );
    flush();

    const saveOrDiscardStub = sinon.stub();
    element.addEventListener('thread-changed', saveOrDiscardStub);
    const draftEl = element.root?.querySelectorAll('gr-comment')[1];
    assert.ok(draftEl);
    draftEl!.addEventListener('comment-discard', () => {
      const drafts = element.comments.filter(c => isDraft(c));
      assert.equal(drafts.length, 0);
      assert.isTrue(saveOrDiscardStub.called);
      assert.equal(
        saveOrDiscardStub.lastCall.args[0].detail.rootId,
        element.rootId
      );
      assert.equal(
        saveOrDiscardStub.lastCall.args[0].detail.path,
        element.path
      );
      done();
    });
    draftEl!.dispatchEvent(
      new CustomEvent('comment-discard', {
        detail: {comment: draftEl!.comment},
        composed: true,
        bubbles: false,
      })
    );
  });

  test('discard with a single comment still fires event with previous rootId', done => {
    element.changeNum = 42 as NumericChangeId;
    element.patchNum = 1 as PatchSetNum;
    element.path = '/path/to/file.txt';
    element.comments = [];
    element.addOrEditDraft(1 as LineNumber);
    flush();
    const rootId = element.rootId;
    assert.isOk(rootId);

    const saveOrDiscardStub = sinon.stub();
    element.addEventListener('thread-changed', saveOrDiscardStub);
    const draftEl = element.root?.querySelectorAll('gr-comment')[0];
    assert.ok(draftEl);
    draftEl!.addEventListener('comment-discard', () => {
      assert.equal(element.comments.length, 0);
      assert.isTrue(saveOrDiscardStub.called);
      assert.equal(saveOrDiscardStub.lastCall.args[0].detail.rootId, rootId);
      assert.equal(
        saveOrDiscardStub.lastCall.args[0].detail.path,
        element.path
      );
      done();
    });
    draftEl!.dispatchEvent(
      new CustomEvent('comment-discard', {
        detail: {comment: draftEl!.comment},
        composed: true,
        bubbles: false,
      })
    );
  });

  test(
    'When not editing other comments, local storage not set' + ' after discard',
    done => {
      element.changeNum = 42 as NumericChangeId;
      element.patchNum = 1 as PatchSetNum;
      element.comments = [
        {
          author: {
            name: 'Mr. Peanutbutter',
            email: 'tenn1sballchaser@aol.com' as EmailAddress,
          },
          id: 'baf0414d_60047215' as UrlEncodedCommentId,
          path: 'test',
          line: 5,
          message: 'is this a crossover episode!?',
          updated: '2015-12-08 19:48:31.843000000' as Timestamp,
        },
        {
          author: {
            name: 'Mr. Peanutbutter',
            email: 'tenn1sballchaser@aol.com' as EmailAddress,
          },
          __draftID: '1',
          in_reply_to: 'baf0414d_60047215' as UrlEncodedCommentId,
          path: 'test',
          line: 5,
          message: 'yes',
          updated: '2015-12-08 19:48:32.843000000' as Timestamp,
          __draft: true,
          __editing: true,
        },
        {
          author: {
            name: 'Mr. Peanutbutter',
            email: 'tenn1sballchaser@aol.com' as EmailAddress,
          },
          __draftID: '2',
          in_reply_to: 'baf0414d_60047215' as UrlEncodedCommentId,
          path: 'test',
          line: 5,
          message: 'no',
          updated: '2015-12-08 19:48:33.843000000' as Timestamp,
          __draft: true,
        },
      ];
      const storageStub = sinon.stub(element.storage, 'setDraftComment');
      flush();

      const draftEl = element.root?.querySelectorAll('gr-comment')[1];
      assert.ok(draftEl);
      draftEl!.addEventListener('comment-discard', () => {
        assert.isFalse(storageStub.called);
        storageStub.restore();
        done();
      });
      draftEl!.dispatchEvent(
        new CustomEvent('comment-discard', {
          detail: {comment: draftEl!.comment},
          composed: true,
          bubbles: false,
        })
      );
    }
  );

  test('comment-update', () => {
    const commentEl = element.shadowRoot?.querySelector('gr-comment');
    const updatedComment = {
      id: element.comments[0].id,
      foo: 'bar',
    };
    assert.isOk(commentEl);
    commentEl!.dispatchEvent(
      new CustomEvent('comment-update', {
        detail: {comment: updatedComment},
        composed: true,
        bubbles: true,
      })
    );
    assert.strictEqual(element.comments[0], updatedComment);
  });

  suite('jack and sally comment data test consolidation', () => {
    setup(() => {
      element.comments = [
        {
          id: 'jacks_reply' as UrlEncodedCommentId,
          message: 'i like you, too',
          in_reply_to: 'sallys_confession' as UrlEncodedCommentId,
          updated: '2015-12-25 15:00:20.396000000' as Timestamp,
          unresolved: false,
        },
        {
          id: 'sallys_confession' as UrlEncodedCommentId,
          in_reply_to: 'nonexistent_comment' as UrlEncodedCommentId,
          message: 'i like you, jack',
          updated: '2015-12-24 15:00:20.396000000' as Timestamp,
        },
        {
          id: 'sally_to_dr_finklestein' as UrlEncodedCommentId,
          in_reply_to: 'nonexistent_comment' as UrlEncodedCommentId,
          message: 'i’m running away',
          updated: '2015-10-31 09:00:20.396000000' as Timestamp,
        },
        {
          id: 'sallys_defiance' as UrlEncodedCommentId,
          message: 'i will poison you so i can get away',
          updated: '2015-10-31 15:00:20.396000000' as Timestamp,
        },
      ];
    });

    test('orphan replies', () => {
      assert.equal(4, element._orderedComments.length);
    });

    test('keyboard shortcuts', () => {
      const expandCollapseStub = sinon.stub(element, '_expandCollapseComments');
      pressAndReleaseKeyOn(element, 69, null, 'e');
      assert.isTrue(expandCollapseStub.lastCall.calledWith(false));

      pressAndReleaseKeyOn(element, 69, 'shift', 'e');
      assert.isTrue(expandCollapseStub.lastCall.calledWith(true));
    });

    test('comment in_reply_to is either null or most recent comment', () => {
      element._createReplyComment('dummy', true);
      flush();
      assert.equal(element._orderedComments.length, 5);
      assert.equal(
        element._orderedComments[4].in_reply_to,
        'jacks_reply' as UrlEncodedCommentId
      );
    });

    test('resolvable comments', () => {
      assert.isFalse(element.unresolved);
      element._createReplyComment('dummy', true, true);
      flush();
      assert.isTrue(element.unresolved);
    });

    test('_setInitialExpandedState with unresolved', () => {
      element.unresolved = true;
      element._setInitialExpandedState();
      for (let i = 0; i < element.comments.length; i++) {
        assert.isFalse(element.comments[i].collapsed);
      }
    });

    test('_setInitialExpandedState without unresolved', () => {
      element.unresolved = false;
      element._setInitialExpandedState();
      for (let i = 0; i < element.comments.length; i++) {
        assert.isTrue(element.comments[i].collapsed);
      }
    });

    test('_setInitialExpandedState with robot_ids', () => {
      for (let i = 0; i < element.comments.length; i++) {
        (element.comments[i] as UIRobot).robot_id = '123' as RobotId;
      }
      element._setInitialExpandedState();
      for (let i = 0; i < element.comments.length; i++) {
        assert.isFalse(element.comments[i].collapsed);
      }
    });

    test('_setInitialExpandedState with collapsed state', () => {
      element.comments[0].collapsed = false;
      element.unresolved = false;
      element._setInitialExpandedState();
      assert.isFalse(element.comments[0].collapsed);
      for (let i = 1; i < element.comments.length; i++) {
        assert.isTrue(element.comments[i].collapsed);
      }
    });
  });

  test('_computeHostClass', () => {
    assert.equal(element._computeHostClass(true), 'unresolved');
    assert.equal(element._computeHostClass(false), '');
  });

  test('addDraft sets unresolved state correctly', () => {
    let unresolved = true;
    element.comments = [];
    element.addDraft(undefined, undefined, unresolved);
    assert.equal(element.comments[0].unresolved, true);

    unresolved = false; // comment should get added as actually resolved.
    element.comments = [];
    element.addDraft(undefined, undefined, unresolved);
    assert.equal(element.comments[0].unresolved, false);

    element.comments = [];
    element.addDraft();
    assert.equal(element.comments[0].unresolved, true);
  });

  test('_newDraft with root', () => {
    const draft = element._newDraft();
    assert.equal(draft.patch_set, 3 as PatchSetNum);
  });

  test('_newDraft with no root', () => {
    element.comments = [];
    element.diffSide = Side.RIGHT;
    element.patchNum = 2 as PatchSetNum;
    const draft = element._newDraft();
    assert.equal(draft.patch_set, 2 as PatchSetNum);
  });

  test('new comment gets created', () => {
    element.comments = [];
    element.addOrEditDraft(1);
    assert.equal(element.comments.length, 1);
    // Mock a submitted comment.
    element.comments[0].id = (element.comments[0] as UIDraft)
      .__draftID as UrlEncodedCommentId;
    delete (element.comments[0] as UIDraft).__draft;
    element.addOrEditDraft(1);
    assert.equal(element.comments.length, 2);
  });

  test('unresolved label', () => {
    element.unresolved = false;
    const label = element.shadowRoot?.querySelector('#unresolvedLabel');
    assert.isOk(label);
    assert.isFalse(label!.hasAttribute('hidden'));
    element.unresolved = true;
    assert.isFalse(label!.hasAttribute('hidden'));
  });

  test('draft comments are at the end of orderedComments', () => {
    element.comments = [
      {
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        id: '2' as UrlEncodedCommentId,
        line: 5,
        message: 'Earlier draft',
        updated: '2015-12-08 19:48:33.843000000' as Timestamp,
        __draft: true,
      },
      {
        author: {
          name: 'Mr. Peanutbutter2',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        id: '1' as UrlEncodedCommentId,
        line: 5,
        message: 'This comment was left last but is not a draft',
        updated: '2015-12-10 19:48:33.843000000' as Timestamp,
      },
      {
        author: {
          name: 'Mr. Peanutbutter2',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        id: '3' as UrlEncodedCommentId,
        line: 5,
        message: 'Later draft',
        updated: '2015-12-09 19:48:33.843000000' as Timestamp,
        __draft: true,
      },
    ];
    assert.equal(element._orderedComments[0].id, '1' as UrlEncodedCommentId);
    assert.equal(element._orderedComments[1].id, '2' as UrlEncodedCommentId);
    assert.equal(element._orderedComments[2].id, '3' as UrlEncodedCommentId);
  });

  test('reflects lineNum and commentSide to attributes', () => {
    element.lineNum = 7;
    element.diffSide = Side.LEFT;

    assert.equal(element.getAttribute('line-num'), '7');
    assert.equal(element.getAttribute('diff-side'), Side.LEFT);
  });

  test('reflects range to JSON serialized attribute if set', () => {
    element.range = {
      start_line: 4,
      end_line: 5,
      start_character: 6,
      end_character: 7,
    };

    assert.isOk(element.getAttribute('range'));
    assert.deepEqual(JSON.parse(element.getAttribute('range')!), {
      start_line: 4,
      end_line: 5,
      start_character: 6,
      end_character: 7,
    });
  });

  test('removes range attribute if range is unset', () => {
    element.range = {
      start_line: 4,
      end_line: 5,
      start_character: 6,
      end_character: 7,
    };
    element.range = undefined;

    assert.notOk(element.hasAttribute('range'));
  });
});

suite('comment action tests on resolved comments', () => {
  let element: GrCommentThread;

  setup(() => {
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    stubRestApi('saveDiffDraft').returns(
      Promise.resolve(({
        ok: true,
        text() {
          return Promise.resolve(
            ")]}'\n" +
              JSON.stringify({
                id: '7afa4931_de3d65bd',
                path: '/path/to/file.txt',
                line: 5,
                in_reply_to: 'baf0414d_60047215' as UrlEncodedCommentId,
                updated: '2015-12-21 02:01:10.850000000',
                message: 'Done',
              })
          );
        },
      } as unknown) as Response)
    );
    stubRestApi('deleteDiffDraft').returns(
      Promise.resolve(({ok: true} as unknown) as Response)
    );
    element = withCommentFixture.instantiate();
    element.patchNum = 1 as PatchSetNum;
    element.changeNum = 1 as NumericChangeId;
    element.comments = [
      {
        author: {
          name: 'Mr. Peanutbutter',
          email: 'tenn1sballchaser@aol.com' as EmailAddress,
        },
        id: 'baf0414d_60047215' as UrlEncodedCommentId,
        line: 5,
        message: 'is this a crossover episode!?',
        updated: '2015-12-08 19:48:33.843000000' as Timestamp,
        path: '/path/to/file.txt',
        unresolved: false,
      },
    ];
    flush();
  });

  test('ack and done should be hidden', () => {
    element.changeNum = 42 as NumericChangeId;
    element.patchNum = 1 as PatchSetNum;

    const commentEl = element.shadowRoot?.querySelector('gr-comment');
    assert.ok(commentEl);

    const ackBtn = element.shadowRoot?.querySelector('#ackBtn');
    const doneBtn = element.shadowRoot?.querySelector('#doneBtn');
    assert.equal(ackBtn, null);
    assert.equal(doneBtn, null);
  });

  test('reply and quote button should be visible', () => {
    const commentEl = element.shadowRoot?.querySelector('gr-comment');
    assert.ok(commentEl);

    const replyBtn = element.shadowRoot?.querySelector('#replyBtn');
    const quoteBtn = element.shadowRoot?.querySelector('#quoteBtn');
    assert.ok(replyBtn);
    assert.ok(quoteBtn);
  });
});
