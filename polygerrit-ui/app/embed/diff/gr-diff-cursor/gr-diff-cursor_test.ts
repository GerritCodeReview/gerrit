/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import '../gr-diff/gr-diff';
import './gr-diff-cursor';
import {fixture, html, assert} from '@open-wc/testing';
import {
  mockPromise,
  queryAll,
  queryAndAssert,
  waitUntil,
} from '../../../test/test-utils';
import {createDiff} from '../../../test/test-data-generators';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {GrDiffCursor} from './gr-diff-cursor';
import {waitForEventOnce} from '../../../utils/event-util';
import {DiffInfo, DiffViewMode, Side} from '../../../api/diff';
import {GrDiff} from '../gr-diff/gr-diff';
import {assertIsDefined} from '../../../utils/common-util';

suite('gr-diff-cursor tests', () => {
  let cursor: GrDiffCursor;
  let diffElement: GrDiff;
  let diff: DiffInfo;

  setup(async () => {
    diffElement = await fixture(html`<gr-diff></gr-diff>`);
    cursor = new GrDiffCursor();

    // Register the diff with the cursor.
    cursor.replaceDiffs([diffElement]);

    diffElement.loggedIn = false;
    diffElement.path = 'some/path.ts';
    const promise = mockPromise();
    const setupDone = () => {
      cursor._updateStops();
      cursor.moveToFirstChunk();
      diffElement.removeEventListener('render', setupDone);
      promise.resolve();
    };
    diffElement.addEventListener('render', setupDone);

    diff = createDiff();
    diffElement.prefs = createDefaultDiffPrefs();
    diffElement.renderPrefs = {use_lit_components: true};
    diffElement.diff = diff;
    await promise;
  });

  test('diff cursor functionality (side-by-side)', () => {
    assert.isOk(cursor.diffRow);

    const deltaRows = queryAll<HTMLTableRowElement>(
      diffElement,
      '.section.delta tr.diff-row'
    );
    assert.equal(cursor.diffRow, deltaRows[0]);

    cursor.moveDown();

    assert.notEqual(cursor.diffRow, deltaRows[0]);
    assert.equal(cursor.diffRow, deltaRows[1]);

    cursor.moveUp();

    assert.notEqual(cursor.diffRow, deltaRows[1]);
    assert.equal(cursor.diffRow, deltaRows[0]);
  });

  test('moveToFirstChunk', async () => {
    const diff: DiffInfo = {
      meta_a: {
        name: 'lorem-ipsum.txt',
        content_type: 'text/plain',
        lines: 3,
      },
      meta_b: {
        name: 'lorem-ipsum.txt',
        content_type: 'text/plain',
        lines: 3,
      },
      intraline_status: 'OK',
      change_type: 'MODIFIED',
      diff_header: [
        'diff --git a/lorem-ipsum.txt b/lorem-ipsum.txt',
        'index b2adcf4..554ae49 100644',
        '--- a/lorem-ipsum.txt',
        '+++ b/lorem-ipsum.txt',
      ],
      content: [
        {b: ['new line 1']},
        {ab: ['unchanged line']},
        {a: ['old line 2']},
        {ab: ['more unchanged lines']},
      ],
    };

    diffElement.diff = diff;
    // The file comment button, if present, is a cursor stop. Ensure
    // moveToFirstChunk() works correctly even if the button is not shown.
    diffElement.prefs!.show_file_comment_button = false;
    await waitForEventOnce(diffElement, 'render');

    cursor._updateStops();

    const chunks = [
      ...queryAll(diffElement, '.section.delta'),
    ] as HTMLElement[];
    assert.equal(chunks.length, 2);

    const rows = [
      ...queryAll(diffElement, '.section.delta tr.diff-row'),
    ] as HTMLTableRowElement[];
    assert.equal(rows.length, 2);

    // Verify it works on fresh diff.
    cursor.moveToFirstChunk();
    assert.ok(cursor.diffRow);
    assert.equal(cursor.diffRow, rows[0]);
    assert.equal(cursor.side, Side.RIGHT);

    // Verify it works from other cursor positions.
    cursor.moveToNextChunk();
    assert.ok(cursor.diffRow);
    assert.equal(cursor.diffRow, rows[1]);
    assert.equal(cursor.side, Side.LEFT);

    cursor.moveToFirstChunk();
    assert.ok(cursor.diffRow);
    assert.equal(cursor.diffRow, rows[0]);
    assert.equal(cursor.side, Side.RIGHT);
  });

  test('moveToLastChunk', async () => {
    const diff: DiffInfo = {
      meta_a: {
        name: 'lorem-ipsum.txt',
        content_type: 'text/plain',
        lines: 3,
      },
      meta_b: {
        name: 'lorem-ipsum.txt',
        content_type: 'text/plain',
        lines: 3,
      },
      intraline_status: 'OK',
      change_type: 'MODIFIED',
      diff_header: [
        'diff --git a/lorem-ipsum.txt b/lorem-ipsum.txt',
        'index b2adcf4..554ae49 100644',
        '--- a/lorem-ipsum.txt',
        '+++ b/lorem-ipsum.txt',
      ],
      content: [
        {ab: ['unchanged line']},
        {a: ['old line 2']},
        {ab: ['more unchanged lines']},
        {b: ['new line 3']},
      ],
    };

    diffElement.diff = diff;
    await waitForEventOnce(diffElement, 'render');
    cursor._updateStops();

    const chunks = [
      ...queryAll(diffElement, '.section.delta'),
    ] as HTMLElement[];
    assert.equal(chunks.length, 2);

    const rows = [
      ...queryAll(diffElement, '.section.delta tr.diff-row'),
    ] as HTMLTableRowElement[];
    assert.equal(rows.length, 2);

    // Verify it works on fresh diff.
    cursor.moveToLastChunk();
    assert.ok(cursor.diffRow);
    assert.equal(cursor.diffRow, rows[1]);
    assert.equal(cursor.side, Side.RIGHT);

    // Verify it works from other cursor positions.
    cursor.moveToPreviousChunk();
    assert.ok(cursor.diffRow);
    assert.equal(cursor.diffRow, rows[0]);
    assert.equal(cursor.side, Side.LEFT);

    cursor.moveToLastChunk();
    assert.ok(cursor.diffRow);
    assert.equal(cursor.diffRow, rows[1]);
    assert.equal(cursor.side, Side.RIGHT);
  });

  test('cursor scroll behavior', () => {
    assert.equal(cursor.cursorManager.scrollMode, 'keep-visible');

    diffElement.dispatchEvent(new Event('render-start'));
    assert.isTrue(cursor.cursorManager.focusOnMove);

    window.dispatchEvent(new Event('scroll'));
    assert.equal(cursor.cursorManager.scrollMode, 'never');
    assert.isFalse(cursor.cursorManager.focusOnMove);

    diffElement.dispatchEvent(new Event('render-content'));
    assert.isTrue(cursor.cursorManager.focusOnMove);

    cursor.reInitCursor();
    assert.equal(cursor.cursorManager.scrollMode, 'keep-visible');
  });

  test('moves to selected line', () => {
    const moveToNumStub = sinon.stub(cursor, 'moveToLineNumber');

    diffElement.dispatchEvent(
      new CustomEvent('line-selected', {
        detail: {number: '123', side: Side.RIGHT, path: 'some/file'},
      })
    );

    assert.isTrue(moveToNumStub.called);
    assert.equal(moveToNumStub.lastCall.args[0], 123);
    assert.equal(moveToNumStub.lastCall.args[1], Side.RIGHT);
    assert.equal(moveToNumStub.lastCall.args[2], 'some/file');
  });

  suite('unified diff', () => {
    setup(async () => {
      diffElement.viewMode = DiffViewMode.UNIFIED;
      await waitForEventOnce(diffElement, 'render');
      cursor.reInitCursor();
    });

    test('diff cursor functionality (unified)', () => {
      assert.isOk(cursor.diffRow);

      const rows = [
        ...queryAll(diffElement, '.section.delta tr.diff-row'),
      ] as HTMLTableRowElement[];
      assert.equal(cursor.diffRow, rows[0]);

      cursor.moveDown();

      assert.notEqual(cursor.diffRow, rows[0]);
      assert.equal(cursor.diffRow, rows[1]);

      cursor.moveUp();

      assert.notEqual(cursor.diffRow, rows[1]);
      assert.equal(cursor.diffRow, rows[0]);
    });
  });

  test('cursor side functionality', () => {
    // The side only applies to side-by-side mode, which should be the default
    // mode.
    assert.equal(diffElement.viewMode, 'SIDE_BY_SIDE');

    const rows = [
      ...queryAll(diffElement, '.section tr.diff-row'),
    ] as HTMLTableRowElement[];
    assert.equal(rows.length, 50);
    const deltaRows = [
      ...queryAll(diffElement, '.section.delta tr.diff-row'),
    ] as HTMLTableRowElement[];
    assert.equal(deltaRows.length, 14);
    const indexFirstDelta = rows.indexOf(deltaRows[0]);
    const rowBeforeFirstDelta = rows[indexFirstDelta - 1];

    // Because the first delta in this diff is on the right, it should be set
    // to the right side.
    assert.equal(cursor.side, Side.RIGHT);
    assert.equal(cursor.diffRow, deltaRows[0]);
    const firstIndex = cursor.cursorManager.index;

    // Move the side to the left. Because this delta only has a right side, we
    // should be moved up to the previous line where there is content on the
    // right. The previous row is part of the previous section.
    cursor.moveLeft();

    assert.equal(cursor.side, Side.LEFT);
    assert.notEqual(cursor.diffRow, rows[0]);
    assert.equal(cursor.diffRow, rowBeforeFirstDelta);
    assert.equal(cursor.cursorManager.index, firstIndex - 1);

    // If we move down, we should skip everything in the first delta because
    // we are on the left side and the first delta has no content on the left.
    cursor.moveDown();

    assert.equal(cursor.side, Side.LEFT);
    assert.notEqual(cursor.diffRow, rowBeforeFirstDelta);
    assert.notEqual(cursor.diffRow, rows[0]);
    assert.isTrue(cursor.cursorManager.index > firstIndex);
  });

  test('chunk skip functionality', () => {
    const deltaChunks = [...queryAll(diffElement, 'tbody.section.delta')];

    // We should be initialized to the first chunk. Since this chunk only has
    // content on the right side, our side should be right.
    assert.equal(cursor.diffRow, deltaChunks[0].querySelector('tr'));
    assert.equal(cursor.side, Side.RIGHT);

    // Move to the next chunk.
    cursor.moveToNextChunk();

    // Since this chunk only has content on the left side. we should have been
    // automatically moved over.
    assert.equal(cursor.diffRow, deltaChunks[1].querySelector('tr'));
    assert.equal(cursor.side, Side.LEFT);
  });

  suite('moved chunks without line range)', () => {
    setup(async () => {
      const promise = mockPromise();
      const renderHandler = function () {
        diffElement.removeEventListener('render', renderHandler);
        cursor.reInitCursor();
        promise.resolve();
      };
      diffElement.addEventListener('render', renderHandler);
      diffElement.diff = {
        ...diff,
        content: [
          {
            ab: ['Lorem ipsum dolor sit amet, suspendisse inceptos vehicula, '],
          },
          {
            b: [
              'Nullam neque, ligula ac, id blandit.',
              'Sagittis tincidunt torquent, tempor nunc amet.',
              'At rhoncus id.',
            ],
            move_details: {changed: false},
          },
          {
            ab: ['Sem nascetur, erat ut, non in.'],
          },
          {
            a: [
              'Nullam neque, ligula ac, id blandit.',
              'Sagittis tincidunt torquent, tempor nunc amet.',
              'At rhoncus id.',
            ],
            move_details: {changed: false},
          },
          {
            ab: ['Arcu eget, rhoncus amet cursus, ipsum elementum.'],
          },
        ],
      };
      await promise;
    });

    test('renders moveControls with simple descriptions', () => {
      const [movedIn, movedOut] = [
        ...queryAll<HTMLElement>(diffElement, '.dueToMove tr.moveControls'),
      ];
      assert.include(movedIn.innerText, 'Moved in');
      assert.include(movedOut.innerText, 'Moved out');
    });
  });

  suite('moved chunks (moveDetails)', () => {
    setup(async () => {
      const promise = mockPromise();
      const renderHandler = function () {
        diffElement.removeEventListener('render', renderHandler);
        cursor.reInitCursor();
        promise.resolve();
      };
      diffElement.addEventListener('render', renderHandler);
      diffElement.diff = {
        ...diff,
        content: [
          {
            ab: ['Lorem ipsum dolor sit amet, suspendisse inceptos vehicula, '],
          },
          {
            b: [
              'Nullam neque, ligula ac, id blandit.',
              'Sagittis tincidunt torquent, tempor nunc amet.',
              'At rhoncus id.',
            ],
            move_details: {changed: false, range: {start: 4, end: 6}},
          },
          {
            ab: ['Sem nascetur, erat ut, non in.'],
          },
          {
            a: [
              'Nullam neque, ligula ac, id blandit.',
              'Sagittis tincidunt torquent, tempor nunc amet.',
              'At rhoncus id.',
            ],
            move_details: {changed: false, range: {start: 2, end: 4}},
          },
          {
            ab: ['Arcu eget, rhoncus amet cursus, ipsum elementum.'],
          },
        ],
      };
      await promise;
    });

    test('renders moveControls with simple descriptions', () => {
      const [movedIn, movedOut] = [
        ...queryAll<HTMLElement>(diffElement, '.dueToMove tr.moveControls'),
      ];
      assert.include(movedIn.innerText, 'Moved from lines 4 - 6');
      assert.include(movedOut.innerText, 'Moved to lines 2 - 4');
    });

    test('startLineAnchor of movedIn chunk fires events', async () => {
      const [movedIn] = [...queryAll(diffElement, '.dueToMove .moveControls')];
      const [startLineAnchor] = movedIn.querySelectorAll('a');

      const promise = mockPromise();
      const onMovedLinkClicked = (e: CustomEvent) => {
        assert.deepEqual(e.detail, {lineNum: 4, side: Side.LEFT});
        promise.resolve();
      };
      assert.equal(startLineAnchor.textContent, '4');
      startLineAnchor.addEventListener(
        'moved-link-clicked',
        onMovedLinkClicked
      );
      startLineAnchor.click();
      await promise;
    });

    test('endLineAnchor of movedOut fires events', async () => {
      const [, movedOut] = [
        ...queryAll(diffElement, '.dueToMove .moveControls'),
      ];
      const [, endLineAnchor] = movedOut.querySelectorAll('a');

      const promise = mockPromise();
      const onMovedLinkClicked = (e: CustomEvent) => {
        assert.deepEqual(e.detail, {lineNum: 4, side: Side.RIGHT});
        promise.resolve();
      };
      assert.equal(endLineAnchor.textContent, '4');
      endLineAnchor.addEventListener('moved-link-clicked', onMovedLinkClicked);
      endLineAnchor.click();
      await promise;
    });
  });

  test('initialLineNumber not provided', async () => {
    let scrollBehaviorDuringMove;
    const moveToNumStub = sinon.stub(cursor, 'moveToLineNumber');
    const moveToChunkStub = sinon
      .stub(cursor, 'moveToFirstChunk')
      .callsFake(() => {
        scrollBehaviorDuringMove = cursor.cursorManager.scrollMode;
      });
    diffElement.diff = createDiff();
    await diffElement.updateComplete;
    await waitForEventOnce(diffElement, 'render');
    cursor.reInitCursor();
    assert.isFalse(moveToNumStub.called);
    assert.isTrue(moveToChunkStub.called);
    assert.equal(scrollBehaviorDuringMove, 'never');
    assert.equal(cursor.cursorManager.scrollMode, 'keep-visible');
  });

  test('initialLineNumber provided', async () => {
    let scrollBehaviorDuringMove;
    const moveToNumStub = sinon
      .stub(cursor, 'moveToLineNumber')
      .callsFake(() => {
        scrollBehaviorDuringMove = cursor.cursorManager.scrollMode;
      });
    const moveToChunkStub = sinon.stub(cursor, 'moveToFirstChunk');
    cursor.initialLineNumber = 10;
    cursor.side = Side.RIGHT;

    diffElement.diff = createDiff();
    await diffElement.updateComplete;
    await waitForEventOnce(diffElement, 'render');
    cursor.reInitCursor();
    assert.isFalse(moveToChunkStub.called);
    assert.isTrue(moveToNumStub.called);
    assert.equal(moveToNumStub.lastCall.args[0], 10);
    assert.equal(moveToNumStub.lastCall.args[1], Side.RIGHT);
    assert.equal(scrollBehaviorDuringMove, 'keep-visible');
    assert.equal(cursor.cursorManager.scrollMode, 'keep-visible');
  });

  test('getTargetDiffElement', () => {
    cursor.initialLineNumber = 1;
    assert.isTrue(!!cursor.diffRow);
    assert.equal(cursor.getTargetDiffElement(), diffElement);
  });

  suite('createCommentInPlace', () => {
    setup(() => {
      diffElement.loggedIn = true;
    });

    test('adds new draft for selected line on the left', async () => {
      cursor.moveToLineNumber(2, Side.LEFT);
      const promise = mockPromise();
      diffElement.addEventListener('create-comment', e => {
        const {lineNum, range, side} = e.detail;
        assert.equal(lineNum, 2);
        assert.equal(range, undefined);
        assert.equal(side, Side.LEFT);
        promise.resolve();
      });
      cursor.createCommentInPlace();
      await promise;
    });

    test('adds draft for selected line on the right', async () => {
      cursor.moveToLineNumber(4, Side.RIGHT);
      const promise = mockPromise();
      diffElement.addEventListener('create-comment', e => {
        const {lineNum, range, side} = e.detail;
        assert.equal(lineNum, 4);
        assert.equal(range, undefined);
        assert.equal(side, Side.RIGHT);
        promise.resolve();
      });
      cursor.createCommentInPlace();
      await promise;
    });

    test('creates comment for range if selected', async () => {
      const someRange = {
        start_line: 2,
        start_character: 3,
        end_line: 6,
        end_character: 1,
      };
      diffElement.highlights.selectedRange = {
        side: Side.RIGHT,
        range: someRange,
      };
      const promise = mockPromise();
      diffElement.addEventListener('create-comment', e => {
        const {lineNum, range, side} = e.detail;
        assert.equal(lineNum, 6);
        assert.equal(range, someRange);
        assert.equal(side, Side.RIGHT);
        promise.resolve();
      });
      cursor.createCommentInPlace();
      await promise;
    });

    test('ignores call if nothing is selected', () => {
      const createRangeCommentStub = sinon.stub(
        diffElement,
        'createRangeComment'
      );
      const addDraftAtLineStub = sinon.stub(diffElement, 'addDraftAtLine');
      cursor.diffRow = undefined;
      cursor.createCommentInPlace();
      assert.isFalse(createRangeCommentStub.called);
      assert.isFalse(addDraftAtLineStub.called);
    });
  });

  test('getAddress', () => {
    // It should initialize to the first chunk: line 5 of the revision.
    assert.deepEqual(cursor.getAddress(), {leftSide: false, number: 5});

    // Revision line 4 is up.
    cursor.moveUp();
    assert.deepEqual(cursor.getAddress(), {leftSide: false, number: 4});

    // Base line 4 is left.
    cursor.moveLeft();
    assert.deepEqual(cursor.getAddress(), {leftSide: true, number: 4});

    // Moving to the next chunk takes it back to the start.
    cursor.moveToNextChunk();
    assert.deepEqual(cursor.getAddress(), {leftSide: false, number: 5});

    // The following chunk is a removal starting on line 10 of the base.
    cursor.moveToNextChunk();
    assert.deepEqual(cursor.getAddress(), {leftSide: true, number: 10});

    // Should be null if there is no selection.
    cursor.cursorManager.unsetCursor();
    assert.isNotOk(cursor.getAddress());
  });

  test('_findRowByNumberAndFile', () => {
    // Get the first ab row after the first chunk.
    const rows = [...queryAll<HTMLTableRowElement>(diffElement, 'tr')];
    const row = rows[9];
    assert.ok(row);

    // It should be line 8 on the right, but line 5 on the left.
    assert.equal(cursor._findRowByNumberAndFile(8, Side.RIGHT), row);
    assert.equal(cursor._findRowByNumberAndFile(5, Side.LEFT), row);
  });

  test('expand context updates stops', async () => {
    const spy = sinon.spy(cursor, '_updateStops');
    const controls = queryAndAssert(diffElement, 'gr-context-controls');
    const showContext = queryAndAssert<HTMLElement>(controls, '.showContext');
    showContext.click();
    await waitForEventOnce(diffElement, 'render');
    await waitUntil(() => spy.called);
    assert.isTrue(spy.called);
  });

  test('updates stops when loading changes', () => {
    const spy = sinon.spy(cursor, '_updateStops');
    diffElement.dispatchEvent(new Event('loading-changed'));
    assert.isTrue(spy.called);
  });

  suite('multi diff', () => {
    let diffElements: GrDiff[];

    setup(async () => {
      diffElements = [
        await fixture(html`<gr-diff></gr-diff>`),
        await fixture(html`<gr-diff></gr-diff>`),
        await fixture(html`<gr-diff></gr-diff>`),
      ];
      cursor = new GrDiffCursor();

      // Register the diff with the cursor.
      cursor.replaceDiffs(diffElements);

      for (const el of diffElements) {
        el.prefs = createDefaultDiffPrefs();
      }
    });

    function getTargetDiffIndex() {
      // Mocha has a bug where when `assert.equals` fails, it will try to
      // JSON.stringify the operands, which fails when they are cyclic structures
      // like GrDiffElement. The failure is difficult to attribute to a specific
      // assertion because of the async nature assertion errors are handled and
      // can cause the test simply timing out, causing a lot of debugging headache.
      // Working with indices circumvents the problem.
      const target = cursor.getTargetDiffElement();
      assertIsDefined(target);
      return diffElements.indexOf(target);
    }

    test('do not skip loading diffs', async () => {
      diffElements[0].diff = createDiff();
      diffElements[2].diff = createDiff();
      await waitForEventOnce(diffElements[0], 'render');
      await waitForEventOnce(diffElements[2], 'render');

      const lastLine = diffElements[0].diff.meta_b?.lines;
      assertIsDefined(lastLine);

      // Goto second last line of the first diff
      cursor.moveToLineNumber(lastLine - 1, Side.RIGHT);
      assert.equal(
        cursor.getTargetLineElement()!.textContent,
        `${lastLine - 1}`
      );

      // Can move down until we reach the loading file
      cursor.moveDown();
      assert.equal(getTargetDiffIndex(), 0);
      assert.equal(
        cursor.getTargetLineElement()!.textContent,
        lastLine.toString()
      );

      // Cannot move down while still loading the diff we would switch to
      cursor.moveDown();
      assert.equal(getTargetDiffIndex(), 0);
      assert.equal(
        cursor.getTargetLineElement()!.textContent,
        lastLine.toString()
      );

      // Diff 1 finishing to load
      diffElements[1].diff = createDiff();
      await waitForEventOnce(diffElements[1], 'render');

      // Now we can go down
      cursor.moveDown();
      assert.equal(getTargetDiffIndex(), 1);
      assert.equal(cursor.getTargetLineElement()!.textContent, 'File');
    });
  });
});
