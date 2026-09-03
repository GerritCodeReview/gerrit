/**
 * @license
 * Copyright 2026 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {assert, fixture, html} from '@open-wc/testing';
import '../../../test/common-test-setup';
import './gr-diff-markdown-viewer';
import {GrDiffMarkdownViewer} from './gr-diff-markdown-viewer';
import {DiffInfo} from '../../../types/diff';
import {CommentSide, DiffViewMode} from '../../../constants/constants';
import {
  createAccountDetailWithId,
  createComment,
  createCommentThread,
  createDiff,
  createPatchRange,
} from '../../../test/test-data-generators';
import {testResolver} from '../../../test/common-test-setup';
import {
  CommentsModel,
  commentsModelToken,
} from '../../../models/comments/comments-model';
import {UserModel, userModelToken} from '../../../models/user/user-model';
import {
  CommentThread,
  DraftInfo,
  RevisionPatchSetNum,
} from '../../../types/common';
import sinon from 'sinon';

suite('gr-diff-markdown-viewer tests', () => {
  let element: GrDiffMarkdownViewer;
  let commentsModel: CommentsModel;
  let userModel: UserModel;

  setup(async () => {
    commentsModel = testResolver(commentsModelToken);
    userModel = testResolver(userModelToken);
    userModel.setAccount(createAccountDetailWithId(1));

    element = await fixture<GrDiffMarkdownViewer>(
      html`<gr-diff-markdown-viewer></gr-diff-markdown-viewer>`
    );
  });

  test('renders empty when no diff is provided', () => {
    const grid = element.shadowRoot!.querySelector('.diff-grid');
    assert.isNotNull(grid);
    assert.equal(grid.children.length, 0);
  });

  test('renders aligned markdown rows', async () => {
    const diff: DiffInfo = {
      ...createDiff(),
      content: [
        {ab: ['# Title', '']},
        {a: ['Old paragraph.'], b: ['New paragraph.']},
        {b: ['', '- Added bullet']},
      ],
    };

    element.diff = diff;
    await element.updateComplete;

    const grid = element.shadowRoot!.querySelector('.diff-grid');
    assert.isNotNull(grid);
    // At least 3 rows = 6 cells (left and right)
    const cells = grid.querySelectorAll('.diff-cell');
    assert.isAtLeast(cells.length, 4);

    // Verify left and right column headers
    const leftHeader = element.shadowRoot!.querySelector('.column-header.left');
    const rightHeader = element.shadowRoot!.querySelector(
      '.column-header.right'
    );
    assert.equal(leftHeader!.textContent!.trim(), 'Base');
    assert.equal(rightHeader!.textContent!.trim(), 'Revision');
  });

  test('highlights modified and added cells correctly', async () => {
    const diff: DiffInfo = {
      ...createDiff(),
      content: [{a: ['Deleted text.'], b: ['Added text.']}],
    };

    element.diff = diff;
    await element.updateComplete;

    const modifiedLeft = element.shadowRoot!.querySelector<HTMLElement>(
      '.diff-cell.modified-left'
    );
    const modifiedRight = element.shadowRoot!.querySelector<HTMLElement>(
      '.diff-cell.modified-right'
    );
    assert.isNotNull(modifiedLeft);
    assert.isNotNull(modifiedRight);
    const styleLeft = window.getComputedStyle(modifiedLeft);
    const styleRight = window.getComputedStyle(modifiedRight);
    assert.isOk(styleLeft.backgroundColor);
    assert.notEqual(styleLeft.backgroundColor, 'rgba(0, 0, 0, 0)');
    assert.notEqual(styleLeft.backgroundColor, 'transparent');
    assert.isOk(styleRight.backgroundColor);
    assert.notEqual(styleRight.backgroundColor, 'rgba(0, 0, 0, 0)');
    assert.notEqual(styleRight.backgroundColor, 'transparent');
  });

  test('renders deleted block with red background in side-by-side mode', async () => {
    const diff: DiffInfo = {
      ...createDiff(),
      content: [{a: ['Only deleted paragraph.']}],
    };

    element.diff = diff;
    await element.updateComplete;

    const deletedCell =
      element.shadowRoot!.querySelector<HTMLElement>('.diff-cell.deleted');
    assert.isNotNull(deletedCell);
    const style = window.getComputedStyle(deletedCell);
    assert.isOk(style.backgroundColor);
    assert.notEqual(style.backgroundColor, 'rgba(0, 0, 0, 0)');
    assert.notEqual(style.backgroundColor, 'transparent');
  });

  test('renders unified diff mode correctly', async () => {
    const diff: DiffInfo = {
      ...createDiff(),
      content: [
        {ab: ['# Unchanged title', '']},
        {a: ['Deleted block.'], b: ['Added block.']},
      ],
    };

    element.diff = diff;
    element.viewMode = DiffViewMode.UNIFIED;
    await element.updateComplete;

    const unifiedContainer =
      element.shadowRoot!.querySelector('.unified-container');
    assert.isNotNull(unifiedContainer);

    const headers = element.shadowRoot!.querySelector('.column-headers');
    assert.isNull(headers);

    const unchangedBlock = element.shadowRoot!.querySelector(
      '.unified-block.unchanged'
    );
    assert.isNotNull(unchangedBlock);

    const deletedBlock = element.shadowRoot!.querySelector(
      '.unified-block.deleted'
    );
    assert.isNotNull(deletedBlock);

    const addedBlock = element.shadowRoot!.querySelector(
      '.unified-block.added'
    );
    assert.isNotNull(addedBlock);

    // Switch back to side-by-side (split) mode
    element.viewMode = DiffViewMode.SIDE_BY_SIDE;
    await element.updateComplete;

    assert.isNull(element.shadowRoot!.querySelector('.unified-container'));
    assert.isNotNull(element.shadowRoot!.querySelector('.diff-grid'));
    assert.isNotNull(element.shadowRoot!.querySelector('.column-headers'));
  });

  suite('comment threads rendering and creation', () => {
    const diff: DiffInfo = {
      ...createDiff(),
      content: [
        {ab: ['# Title', '']},
        {a: ['Old paragraph.'], b: ['New paragraph.']},
      ],
    };

    test('renders file-level and block-level threads in side-by-side mode', async () => {
      const fileThread: CommentThread = createCommentThread([
        {...createComment(), line: undefined, message: 'File comment'},
      ]);
      const titleThread: CommentThread = createCommentThread([
        {
          ...createComment(),
          line: 1,
          message: 'Title comment',
          patch_set: 1 as RevisionPatchSetNum,
        },
      ]);
      const baseParagraphThread: CommentThread = createCommentThread([
        {
          ...createComment(),
          line: 3,
          message: 'Old paragraph comment',
          side: CommentSide.PARENT,
        },
      ]);

      element.diff = diff;
      element.patchRange = createPatchRange();
      element.path = 'test.md';
      element.threads = [fileThread, titleThread, baseParagraphThread];
      await element.updateComplete;

      // File level threads section
      const fileSection = element.shadowRoot!.querySelector(
        '.file-level-threads'
      );
      assert.isNotNull(fileSection);
      const fileThreads = fileSection.querySelectorAll('gr-comment-thread');
      assert.equal(fileThreads.length, 1);

      // Block threads inside diff cells
      const cells = element.shadowRoot!.querySelectorAll('.diff-cell');
      // Row 0 left: Title (unchanged, line 1)
      // Row 0 right: Title (unchanged, line 1) -> contains titleThread
      const rightTitleCell = cells[1];
      const rightTitleThreads =
        rightTitleCell.querySelectorAll('gr-comment-thread');
      assert.equal(rightTitleThreads.length, 1);

      // Row 1 left: Old paragraph (modified-left, line 3) -> contains baseParagraphThread
      const leftOldCell = cells[2];
      const leftOldThreads = leftOldCell.querySelectorAll('gr-comment-thread');
      assert.equal(leftOldThreads.length, 1);
    });

    test('clicking add comment button calls commentsModel.addNewDraft', async () => {
      const addDraftSpy = sinon.spy(commentsModel, 'addNewDraft');

      element.diff = diff;
      element.patchRange = createPatchRange();
      element.path = 'test.md';
      await element.updateComplete;

      const cells = element.shadowRoot!.querySelectorAll('.diff-cell');
      // Row 0 right (Title, line 1)
      const rightBtn = cells[1].querySelector<HTMLElement>('.add-comment-btn');
      assert.isNotNull(rightBtn);
      rightBtn.click();

      assert.isTrue(addDraftSpy.calledOnce);
      const draft1: DraftInfo = addDraftSpy.firstCall.firstArg;
      assert.equal(draft1.path, 'test.md');
      assert.equal(draft1.side, CommentSide.REVISION);
      assert.equal(draft1.line, 1);

      // Row 1 left (Old paragraph, line 3)
      const leftBtn = cells[2].querySelector<HTMLElement>('.add-comment-btn');
      assert.isNotNull(leftBtn);
      leftBtn.click();

      assert.isTrue(addDraftSpy.calledTwice);
      const draft2: DraftInfo = addDraftSpy.secondCall.firstArg;
      assert.equal(draft2.path, 'test.md');
      assert.equal(draft2.side, CommentSide.PARENT);
      assert.equal(draft2.line, 3);
    });

    test('prevents commenting when logged out and fires show-auth-required', async () => {
      userModel.setAccount(undefined);
      await element.updateComplete;

      const addDraftSpy = sinon.spy(commentsModel, 'addNewDraft');
      let authFired = false;
      element.addEventListener('show-auth-required', () => {
        authFired = true;
      });

      element.diff = diff;
      element.patchRange = createPatchRange();
      element.path = 'test.md';
      await element.updateComplete;

      const btn =
        element.shadowRoot!.querySelector<HTMLElement>('.add-comment-btn');
      assert.isNotNull(btn);
      btn.click();

      assert.isFalse(addDraftSpy.called);
      assert.isTrue(authFired);
    });

    test('renders threads and handles commenting in unified mode', async () => {
      const addDraftSpy = sinon.spy(commentsModel, 'addNewDraft');

      const thread: CommentThread = createCommentThread([
        {
          ...createComment(),
          line: 1,
          message: 'Unified comment',
          patch_set: 1 as RevisionPatchSetNum,
        },
      ]);

      element.diff = diff;
      element.patchRange = createPatchRange();
      element.path = 'test.md';
      element.threads = [thread];
      element.viewMode = DiffViewMode.UNIFIED;
      await element.updateComplete;

      const unifiedContainer =
        element.shadowRoot!.querySelector('.unified-container');
      assert.isNotNull(unifiedContainer);

      const threads = unifiedContainer.querySelectorAll('gr-comment-thread');
      assert.equal(threads.length, 1);

      const btn =
        unifiedContainer.querySelector<HTMLElement>('.add-comment-btn');
      assert.isNotNull(btn);
      btn.click();

      assert.isTrue(addDraftSpy.calledOnce);
      const draft: DraftInfo = addDraftSpy.firstCall.firstArg;
      assert.equal(draft.path, 'test.md');
      assert.equal(draft.line, 1);
    });

    test('pressing c when cell is hovered creates draft on that line and side', async () => {
      const addDraftSpy = sinon.spy(commentsModel, 'addNewDraft');

      element.diff = diff;
      element.patchRange = createPatchRange();
      element.path = 'test.md';
      await element.updateComplete;

      const cells = element.shadowRoot!.querySelectorAll('.diff-cell');
      // Row 1 left (line 3, Side.LEFT)
      const leftCell = cells[2];
      leftCell.dispatchEvent(new MouseEvent('mouseenter'));

      window.dispatchEvent(
        new KeyboardEvent('keydown', {key: 'c', bubbles: true})
      );

      assert.isTrue(addDraftSpy.calledOnce);
      const draft: DraftInfo = addDraftSpy.firstCall.firstArg;
      assert.equal(draft.path, 'test.md');
      assert.equal(draft.side, CommentSide.PARENT);
      assert.equal(draft.line, 3);
    });

    test('selecting text displays selection action box and clicking creates draft', async () => {
      const addDraftSpy = sinon.spy(commentsModel, 'addNewDraft');

      element.diff = diff;
      element.patchRange = createPatchRange();
      element.path = 'test.md';
      await element.updateComplete;

      const cells = element.shadowRoot!.querySelectorAll('.diff-cell');
      const rightCell = cells[1];
      const textNode = rightCell.querySelector('.cell-content')?.firstChild;
      assert.isNotNull(textNode);

      const range = document.createRange();
      range.selectNodeContents(textNode!);
      const selection = window.getSelection()!;
      selection.removeAllRanges();
      selection.addRange(range);

      document.dispatchEvent(new Event('selectionchange'));
      await element.updateComplete;

      const actionBox = element.shadowRoot!.querySelector(
        '.selection-action-box'
      );
      assert.isNotNull(actionBox);
      const actionBoxStyle = window.getComputedStyle(actionBox);
      assert.equal(actionBoxStyle.zIndex, '500');

      const commentBtn = actionBox.querySelector<HTMLElement>(
        '.selection-comment-btn'
      );
      assert.isNotNull(commentBtn);
      const btnStyle = window.getComputedStyle(commentBtn);
      assert.isOk(btnStyle.backgroundColor);
      assert.notEqual(btnStyle.backgroundColor, 'transparent');
      assert.notEqual(btnStyle.backgroundColor, 'rgba(0, 0, 0, 0)');

      commentBtn.click();

      assert.isTrue(addDraftSpy.calledOnce);
      const draft: DraftInfo = addDraftSpy.firstCall.firstArg;
      assert.equal(draft.path, 'test.md');
      assert.equal(draft.side, CommentSide.REVISION);
      assert.equal(draft.line, 1);
    });

    test('text selection is preserved after selectionchange event', async () => {
      element.diff = diff;
      element.patchRange = createPatchRange();
      element.path = 'test.md';
      await element.updateComplete;

      const cells = element.shadowRoot!.querySelectorAll('.diff-cell');
      const rightCell = cells[1];
      const textNode = rightCell.querySelector('.cell-content')?.firstChild;
      assert.isNotNull(textNode);

      const range = document.createRange();
      range.selectNodeContents(textNode!);
      const selection = window.getSelection()!;
      selection.removeAllRanges();
      selection.addRange(range);

      document.dispatchEvent(new Event('selectionchange'));
      await element.updateComplete;

      assert.isFalse(selection.isCollapsed);
      assert.equal(selection.rangeCount, 1);
      assert.isTrue(
        element.shadowRoot!.contains(selection.getRangeAt(0).startContainer)
      );
    });

    test('pressing c with active text selection creates draft', async () => {
      const addDraftSpy = sinon.spy(commentsModel, 'addNewDraft');

      element.diff = diff;
      element.patchRange = createPatchRange();
      element.path = 'test.md';
      await element.updateComplete;

      const cells = element.shadowRoot!.querySelectorAll('.diff-cell');
      const leftCell = cells[2];
      const textNode = leftCell.querySelector('.cell-content')?.firstChild;
      assert.isNotNull(textNode);

      const range = document.createRange();
      range.selectNodeContents(textNode!);
      const selection = window.getSelection()!;
      selection.removeAllRanges();
      selection.addRange(range);

      document.dispatchEvent(new Event('selectionchange'));
      await element.updateComplete;

      window.dispatchEvent(
        new KeyboardEvent('keydown', {key: 'c', bubbles: true})
      );

      assert.isTrue(addDraftSpy.calledOnce);
      const draft: DraftInfo = addDraftSpy.firstCall.firstArg;
      assert.equal(draft.path, 'test.md');
      assert.equal(draft.side, CommentSide.PARENT);
      assert.equal(draft.line, 3);
    });

    test('createCommentFromSelectionOrHover creates comment on selection', async () => {
      const addDraftSpy = sinon.spy(commentsModel, 'addNewDraft');

      element.diff = diff;
      element.patchRange = createPatchRange();
      element.path = 'test.md';
      await element.updateComplete;

      const cells = element.shadowRoot!.querySelectorAll('.diff-cell');
      const rightCell = cells[1];
      const textNode = rightCell.querySelector('.cell-content')?.firstChild;
      assert.isNotNull(textNode);

      const range = document.createRange();
      range.selectNodeContents(textNode!);
      const selection = window.getSelection()!;
      selection.removeAllRanges();
      selection.addRange(range);

      element.createCommentFromSelectionOrHover();

      assert.isTrue(addDraftSpy.calledOnce);
      const draft: DraftInfo = addDraftSpy.firstCall.firstArg;
      assert.equal(draft.path, 'test.md');
      assert.equal(draft.side, CommentSide.REVISION);
      assert.equal(draft.line, 1);
    });
  });
});
