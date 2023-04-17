/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-thread-list';
import {CommentSide, SpecialFilePath} from '../../../constants/constants';
import {CommentTabState} from '../../../types/events';
import {
  compareThreads,
  GrThreadList,
  __testOnly_SortDropdownState,
} from './gr-thread-list';
import {queryAll, stubFlags} from '../../../test/test-utils';
import {getUserId} from '../../../utils/account-util';
import {
  createAccountDetailWithId,
  createComment,
  createCommentThread,
  createDraft,
  createParsedChange,
  createThread,
} from '../../../test/test-data-generators';
import {
  AccountId,
  EmailAddress,
  NumericChangeId,
  Timestamp,
} from '../../../api/rest-api';
import {
  RobotId,
  UrlEncodedCommentId,
  RevisionPatchSetNum,
  CommentThread,
  isDraft,
  SavingState,
} from '../../../types/common';
import {query, queryAndAssert} from '../../../utils/common-util';
import {GrAccountLabel} from '../../shared/gr-account-label/gr-account-label';
import {GrDropdownList} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {fixture, html, assert} from '@open-wc/testing';
import {GrCommentThread} from '../../shared/gr-comment-thread/gr-comment-thread';

suite('gr-thread-list tests', () => {
  let element: GrThreadList;

  setup(async () => {
    element = await fixture(html`<gr-thread-list></gr-thread-list>`);
    element.changeNum = 123 as NumericChangeId;
    element.change = createParsedChange();
    element.account = createAccountDetailWithId();
    element.threads = [
      {
        comments: [
          {
            path: '/COMMIT_MSG',
            author: {
              _account_id: 1000001 as AccountId,
              name: 'user',
              username: 'user',
              email: 'abcd' as EmailAddress,
            },
            patch_set: 4 as RevisionPatchSetNum,
            id: 'ecf0b9fa_fe1a5f62' as UrlEncodedCommentId,
            line: 5,
            updated: '2015-12-01 15:15:15.000000000' as Timestamp,
            message: 'test',
            unresolved: true,
          },
          {
            id: '503008e2_0ab203ee' as UrlEncodedCommentId,
            path: '/COMMIT_MSG',
            line: 5,
            in_reply_to: 'ecf0b9fa_fe1a5f62' as UrlEncodedCommentId,
            updated: '2015-12-01 15:16:15.000000000' as Timestamp,
            message: 'draft',
            unresolved: true,
            savingState: SavingState.OK,
            patch_set: '2' as RevisionPatchSetNum,
          },
        ],
        patchNum: 4 as RevisionPatchSetNum,
        path: '/COMMIT_MSG',
        line: 5,
        rootId: 'ecf0b9fa_fe1a5f62' as UrlEncodedCommentId,
        commentSide: CommentSide.REVISION,
      },
      {
        comments: [
          {
            path: 'test.txt',
            author: {
              _account_id: 1000002 as AccountId,
              name: 'user',
              username: 'user',
              email: 'abcd' as EmailAddress,
            },
            patch_set: 3 as RevisionPatchSetNum,
            id: '09a9fb0a_1484e6cf' as UrlEncodedCommentId,
            updated: '2015-12-02 15:16:15.000000000' as Timestamp,
            message: 'Some comment on another patchset.',
            unresolved: false,
          },
        ],
        patchNum: 3 as RevisionPatchSetNum,
        path: 'test.txt',
        rootId: '09a9fb0a_1484e6cf' as UrlEncodedCommentId,
        commentSide: CommentSide.REVISION,
      },
      {
        comments: [
          {
            path: '/COMMIT_MSG',
            author: {
              _account_id: 1000002 as AccountId,
              name: 'user',
              username: 'user',
              email: 'abcd' as EmailAddress,
            },
            patch_set: 2 as RevisionPatchSetNum,
            id: '8caddf38_44770ec1' as UrlEncodedCommentId,
            updated: '2015-12-03 15:16:15.000000000' as Timestamp,
            message: 'Another unresolved comment',
            unresolved: false,
          },
        ],
        patchNum: 2 as RevisionPatchSetNum,
        path: '/COMMIT_MSG',
        rootId: '8caddf38_44770ec1' as UrlEncodedCommentId,
        commentSide: CommentSide.REVISION,
      },
      {
        comments: [
          {
            path: '/COMMIT_MSG',
            author: {
              _account_id: 1000003 as AccountId,
              name: 'user',
              username: 'user',
              email: 'abcd' as EmailAddress,
            },
            patch_set: 2 as RevisionPatchSetNum,
            id: 'scaddf38_44770ec1' as UrlEncodedCommentId,
            line: 4,
            updated: '2015-12-04 15:16:15.000000000' as Timestamp,
            message: 'Yet another unresolved comment',
            unresolved: true,
          },
        ],
        patchNum: 2 as RevisionPatchSetNum,
        path: '/COMMIT_MSG',
        line: 4,
        rootId: 'scaddf38_44770ec1' as UrlEncodedCommentId,
        commentSide: CommentSide.REVISION,
      },
      {
        comments: [
          {
            id: 'zcf0b9fa_fe1a5f62' as UrlEncodedCommentId,
            path: '/COMMIT_MSG',
            line: 6,
            updated: '2015-12-05 15:16:15.000000000' as Timestamp,
            message: 'resolved draft',
            unresolved: false,
            savingState: SavingState.OK,
            patch_set: '2' as RevisionPatchSetNum,
          },
        ],
        patchNum: 4 as RevisionPatchSetNum,
        path: '/COMMIT_MSG',
        line: 6,
        rootId: 'zcf0b9fa_fe1a5f62' as UrlEncodedCommentId,
        commentSide: CommentSide.REVISION,
      },
      {
        comments: [
          {
            id: 'patchset_level_1' as UrlEncodedCommentId,
            path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
            updated: '2015-12-06 15:16:15.000000000' as Timestamp,
            message: 'patchset comment 1',
            unresolved: false,
            patch_set: '2' as RevisionPatchSetNum,
          },
        ],
        patchNum: 2 as RevisionPatchSetNum,
        path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
        rootId: 'patchset_level_1' as UrlEncodedCommentId,
        commentSide: CommentSide.REVISION,
      },
      {
        comments: [
          {
            id: 'patchset_level_2' as UrlEncodedCommentId,
            path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
            updated: '2015-12-07 15:16:15.000000000' as Timestamp,
            message: 'patchset comment 2',
            unresolved: false,
            patch_set: '3' as RevisionPatchSetNum,
          },
        ],
        patchNum: 3 as RevisionPatchSetNum,
        path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
        rootId: 'patchset_level_2' as UrlEncodedCommentId,
        commentSide: CommentSide.REVISION,
      },
      {
        comments: [
          {
            path: '/COMMIT_MSG',
            author: {
              _account_id: 1000000 as AccountId,
              name: 'user',
              username: 'user',
              email: 'abcd' as EmailAddress,
            },
            patch_set: 4 as RevisionPatchSetNum,
            id: 'rc1' as UrlEncodedCommentId,
            line: 5,
            updated: '2015-12-08 15:16:15.000000000' as Timestamp,
            message: 'test',
            unresolved: true,
            robot_id: 'rc1' as RobotId,
          },
        ],
        patchNum: 4 as RevisionPatchSetNum,
        path: '/COMMIT_MSG',
        line: 5,
        rootId: 'rc1' as UrlEncodedCommentId,
        commentSide: CommentSide.REVISION,
      },
      {
        comments: [
          {
            path: '/COMMIT_MSG',
            author: {
              _account_id: 1000000 as AccountId,
              name: 'user',
              username: 'user',
              email: 'abcd' as EmailAddress,
            },
            patch_set: 4 as RevisionPatchSetNum,
            id: 'rc2' as UrlEncodedCommentId,
            line: 7,
            updated: '2015-12-09 15:16:15.000000000' as Timestamp,
            message: 'test',
            unresolved: true,
            robot_id: 'rc2' as RobotId,
          },
          {
            path: '/COMMIT_MSG',
            author: {
              _account_id: 1000000 as AccountId,
              name: 'user',
              username: 'user',
              email: 'abcd' as EmailAddress,
            },
            patch_set: 4 as RevisionPatchSetNum,
            id: 'c2_1' as UrlEncodedCommentId,
            line: 5,
            updated: '2015-12-10 15:16:15.000000000' as Timestamp,
            message: 'test',
            unresolved: true,
          },
        ],
        patchNum: 4 as RevisionPatchSetNum,
        path: '/COMMIT_MSG',
        line: 7,
        rootId: 'rc2' as UrlEncodedCommentId,
        commentSide: CommentSide.REVISION,
      },
    ];
    await element.updateComplete;
  });

  suite('sort threads', () => {
    test('sort all threads', () => {
      element.sortDropdownValue = __testOnly_SortDropdownState.FILES;
      assert.equal(element.getDisplayedThreads().length, 9);
      const expected: UrlEncodedCommentId[] = [
        'patchset_level_2' as UrlEncodedCommentId, // Posted on Patchset 3
        'patchset_level_1' as UrlEncodedCommentId, // Posted on Patchset 2
        '8caddf38_44770ec1' as UrlEncodedCommentId, // File level on COMMIT_MSG
        'scaddf38_44770ec1' as UrlEncodedCommentId, // Line 4 on COMMIT_MSG
        'rc1' as UrlEncodedCommentId, // Line 5 on COMMIT_MESSAGE newer
        'ecf0b9fa_fe1a5f62' as UrlEncodedCommentId, // Line 5 on COMMIT_MESSAGE older
        'zcf0b9fa_fe1a5f62' as UrlEncodedCommentId, // Line 6 on COMMIT_MSG
        'rc2' as UrlEncodedCommentId, // Line 7 on COMMIT_MSG
        '09a9fb0a_1484e6cf' as UrlEncodedCommentId, // File level on test.txt
      ];
      const actual = element.getDisplayedThreads().map(t => t.rootId);
      assert.sameOrderedMembers(actual, expected);
    });

    test('respects special cases for ordering', async () => {
      element.sortDropdownValue = __testOnly_SortDropdownState.FILES;
      element.threads = [
        {
          ...createThread(createComment({path: '/app/test.cc'})),
          path: '/app/test.cc',
        },
        {
          ...createThread(createComment({path: '/app/test.h'})),
          path: '/app/test.h',
        },
        {
          ...createThread(
            createComment({path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS})
          ),
          path: SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
        },
      ];
      await element.updateComplete;

      const paths = Array.from(
        queryAll<GrCommentThread>(element, 'gr-comment-thread')
      ).map(threadElement => threadElement.thread?.path);

      // Patchset comment is always first, then we have a special case where .h
      // files should appear above other files of the same name regardless of
      // their alphabetical ordering.
      assert.sameOrderedMembers(paths, [
        SpecialFilePath.PATCHSET_LEVEL_COMMENTS,
        '/app/test.h',
        '/app/test.cc',
      ]);
    });

    test('sort all threads by timestamp', () => {
      element.sortDropdownValue = __testOnly_SortDropdownState.TIMESTAMP;
      assert.equal(element.getDisplayedThreads().length, 9);
      const expected: UrlEncodedCommentId[] = [
        'rc2' as UrlEncodedCommentId,
        'rc1' as UrlEncodedCommentId,
        'patchset_level_2' as UrlEncodedCommentId,
        'patchset_level_1' as UrlEncodedCommentId,
        'zcf0b9fa_fe1a5f62' as UrlEncodedCommentId,
        'scaddf38_44770ec1' as UrlEncodedCommentId,
        '8caddf38_44770ec1' as UrlEncodedCommentId,
        '09a9fb0a_1484e6cf' as UrlEncodedCommentId,
        'ecf0b9fa_fe1a5f62' as UrlEncodedCommentId,
      ];
      const actual = element.getDisplayedThreads().map(t => t.rootId);
      assert.sameOrderedMembers(actual, expected);
    });
  });

  test('renders', async () => {
    await element.updateComplete;
    assert.shadowDom.equal(
      element,
      /* HTML */ `
        <div class="header">
          <span class="sort-text">Sort By:</span>
          <gr-dropdown-list id="sortDropdown"></gr-dropdown-list>
          <span class="separator"></span>
          <span class="filter-text">Filter By:</span>
          <gr-dropdown-list id="filterDropdown"></gr-dropdown-list>
          <span class="author-text">From:</span>
          <gr-account-label
            deselected=""
            selectionchipstyle=""
            nostatusicons=""
          ></gr-account-label>
          <gr-account-label
            deselected=""
            selectionchipstyle=""
            nostatusicons=""
          ></gr-account-label>
          <gr-account-label
            deselected=""
            selectionchipstyle=""
            nostatusicons=""
          ></gr-account-label>
          <gr-account-label
            deselected=""
            selectionchipstyle=""
            nostatusicons=""
          ></gr-account-label>
          <gr-account-label
            deselected=""
            selectionchipstyle=""
            nostatusicons=""
          ></gr-account-label>
        </div>
        <div id="threads" part="threads">
          <gr-comment-thread
            show-file-name=""
            show-file-path=""
          ></gr-comment-thread>
          <gr-comment-thread show-file-path=""></gr-comment-thread>
          <div class="thread-separator"></div>
          <gr-comment-thread
            show-file-name=""
            show-file-path=""
          ></gr-comment-thread>
          <gr-comment-thread show-file-path=""></gr-comment-thread>
          <div class="thread-separator"></div>
          <gr-comment-thread
            has-draft=""
            show-file-name=""
            show-file-path=""
          ></gr-comment-thread>
          <gr-comment-thread show-file-path=""></gr-comment-thread>
          <gr-comment-thread show-file-path=""></gr-comment-thread>
          <div class="thread-separator"></div>
          <gr-comment-thread
            show-file-name=""
            show-file-path=""
          ></gr-comment-thread>
          <div class="thread-separator"></div>
          <gr-comment-thread
            has-draft=""
            show-file-name=""
            show-file-path=""
          ></gr-comment-thread>
        </div>
      `
    );
  });

  test('renders empty', async () => {
    element.threads = [];
    await element.updateComplete;
    assert.dom.equal(
      queryAndAssert(element, 'div#threads'),
      /* HTML */ `
        <div id="threads" part="threads">
          <div><span>No comments</span></div>
        </div>
      `
    );
  });

  test('tapping single author chips', async () => {
    element.account = createAccountDetailWithId(1);
    await element.updateComplete;
    const chips = Array.from(
      queryAll<GrAccountLabel>(element, 'gr-account-label')
    );
    const authors = chips.map(chip => getUserId(chip.account!)).sort();
    assert.deepEqual(authors, [
      1 as AccountId,
      1000000 as AccountId,
      1000001 as AccountId,
      1000002 as AccountId,
      1000003 as AccountId,
    ]);
    assert.equal(element.threads.length, 9);
    assert.equal(element.getDisplayedThreads().length, 9);

    const chip = chips.find(chip => chip.account!._account_id === 1000001);
    chip!.click();
    await element.updateComplete;

    assert.equal(element.threads.length, 9);
    assert.equal(element.getDisplayedThreads().length, 1);
    assert.equal(
      element.getDisplayedThreads()[0].comments[0].author?._account_id,
      1000001 as AccountId
    );

    chip!.click();
    await element.updateComplete;
    assert.equal(element.threads.length, 9);
    assert.equal(element.getDisplayedThreads().length, 9);
  });

  test('tapping single author with only drafts', async () => {
    element.account = createAccountDetailWithId(1);
    element.threads = [createThread(createDraft())];
    await element.updateComplete;
    const chips = Array.from(
      queryAll<GrAccountLabel>(element, 'gr-account-label')
    );
    const authors = chips.map(chip => getUserId(chip.account!)).sort();
    assert.deepEqual(authors, [1 as AccountId]);
    assert.equal(element.threads.length, 1);
    assert.equal(element.getDisplayedThreads().length, 1);

    const chip = chips.find(chip => chip.account!._account_id === 1);
    chip!.click();
    await element.updateComplete;

    assert.equal(element.threads.length, 1);
    assert.equal(element.getDisplayedThreads().length, 1);
    assert.isTrue(isDraft(element.getDisplayedThreads()[0].comments[0]));

    chip!.click();
    await element.updateComplete;
    assert.equal(element.threads.length, 1);
    assert.equal(element.getDisplayedThreads().length, 1);
  });

  test('tapping multiple author chips', async () => {
    element.account = createAccountDetailWithId(1);
    await element.updateComplete;
    const chips = Array.from(
      queryAll<GrAccountLabel>(element, 'gr-account-label')
    );

    chips.find(chip => chip.account?._account_id === 1000001)!.click();
    chips.find(chip => chip.account?._account_id === 1000002)!.click();
    await element.updateComplete;

    assert.equal(element.threads.length, 9);
    assert.equal(element.getDisplayedThreads().length, 3);
    assert.equal(
      element.getDisplayedThreads()[0].comments[0].author?._account_id,
      1000002 as AccountId
    );
    assert.equal(
      element.getDisplayedThreads()[1].comments[0].author?._account_id,
      1000002 as AccountId
    );
    assert.equal(
      element.getDisplayedThreads()[2].comments[0].author?._account_id,
      1000001 as AccountId
    );
  });

  test('show all comments', async () => {
    const filterDropdown = queryAndAssert<GrDropdownList>(
      element,
      '#filterDropdown'
    );
    filterDropdown.value = CommentTabState.SHOW_ALL;
    await filterDropdown.updateComplete;
    await element.updateComplete;
    assert.equal(element.getDisplayedThreads().length, 9);
  });

  test('unresolved shows all unresolved comments', async () => {
    const filterDropdown = queryAndAssert<GrDropdownList>(
      element,
      '#filterDropdown'
    );
    filterDropdown.value = CommentTabState.UNRESOLVED;
    await filterDropdown.updateComplete;
    await element.updateComplete;
    assert.equal(element.getDisplayedThreads().length, 4);
  });

  test('toggle drafts only shows threads with draft comments', async () => {
    const filterDropdown = queryAndAssert<GrDropdownList>(
      element,
      '#filterDropdown'
    );
    filterDropdown.value = CommentTabState.DRAFTS;
    await filterDropdown.updateComplete;
    await element.updateComplete;
    assert.equal(element.getDisplayedThreads().length, 2);
  });

  suite('mention threads', () => {
    let mentionedThreads: CommentThread[];
    setup(async () => {
      stubFlags('isEnabled').returns(true);
      mentionedThreads = [
        createCommentThread([
          {
            ...createComment(),
            message: 'random text with no emails',
          },
        ]),
        // Resolved thread does not contribute to the count
        createCommentThread([
          {
            ...createComment(),
            message: '@abcd@def.com please take a look',
          },
          {
            ...createComment(),
            message: '@abcd@def.com please take a look again at this',
          },
        ]),
        createCommentThread([
          {
            ...createComment(),
            message: '@abcd@def.com this is important',
            unresolved: true,
          },
        ]),
      ];
      element.account!.email = 'abcd@def.com' as EmailAddress;
      element.threads.push(...mentionedThreads);
      element.requestUpdate();
      await element.updateComplete;
    });

    test('mentions filter', async () => {
      const filterDropdown = queryAndAssert<GrDropdownList>(
        element,
        '#filterDropdown'
      );
      filterDropdown.value = CommentTabState.MENTIONS;
      await filterDropdown.updateComplete;
      await element.updateComplete;
      assert.deepEqual(element.getDisplayedThreads(), [mentionedThreads[2]]);
    });
  });

  suite('hideDropdown', () => {
    test('header hidden for hideDropdown=true', async () => {
      element.hideDropdown = true;
      await element.updateComplete;
      assert.isUndefined(query(element, '.header'));
    });

    test('header shown for hideDropdown=false', async () => {
      element.hideDropdown = false;
      await element.updateComplete;
      assert.isDefined(query(element, '.header'));
    });
  });

  suite('empty thread', () => {
    setup(async () => {
      element.threads = [];
      await element.updateComplete;
    });

    test('default empty message should show', () => {
      const threadsEl = queryAndAssert(element, '#threads');
      assert.isTrue(threadsEl.textContent?.trim().includes('No comments'));
    });
  });
});

suite('compareThreads', () => {
  let t1: CommentThread;
  let t2: CommentThread;

  const sortPredicate = (thread1: CommentThread, thread2: CommentThread) =>
    compareThreads(thread1, thread2);

  const checkOrder = (expected: CommentThread[]) => {
    assert.sameOrderedMembers([t1, t2].sort(sortPredicate), expected);
    assert.sameOrderedMembers([t2, t1].sort(sortPredicate), expected);
  };

  setup(() => {
    t1 = createThread({});
    t2 = createThread({});
  });

  test('patchset-level before file comments', () => {
    t1.path = SpecialFilePath.PATCHSET_LEVEL_COMMENTS;
    t2.path = SpecialFilePath.COMMIT_MESSAGE;
    checkOrder([t1, t2]);
  });

  test('paths lexicographically', () => {
    t1.path = 'a.txt';
    t2.path = 'b.txt';
    checkOrder([t1, t2]);
  });

  test('patchsets in reverse order', () => {
    t1.patchNum = 2 as RevisionPatchSetNum;
    t2.patchNum = 3 as RevisionPatchSetNum;
    checkOrder([t2, t1]);
  });

  test('file level comment before line', () => {
    t1.line = 123;
    t2.line = 'FILE';
    checkOrder([t2, t1]);
  });

  test('comments sorted by line', () => {
    t1.line = 123;
    t2.line = 321;
    checkOrder([t1, t2]);
  });
});
