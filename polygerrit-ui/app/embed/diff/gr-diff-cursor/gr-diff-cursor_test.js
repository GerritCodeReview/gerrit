/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '../gr-diff/gr-diff.js';
import './gr-diff-cursor.js';
import {fixture, html} from '@open-wc/testing-helpers';
import {mockPromise} from '../../../test/test-utils.js';
import {createDiff} from '../../../test/test-data-generators.js';
import {createDefaultDiffPrefs} from '../../../constants/constants.js';
import {GrDiffCursor} from './gr-diff-cursor.js';
import {waitForEventOnce} from '../../../utils/event-util.js';

suite('gr-diff-cursor tests', () => {
  let cursor;
  let diffElement;
  let diff;

  setup(async () => {
    diffElement = await fixture(html`<gr-diff></gr-diff>`);
    cursor = new GrDiffCursor();

    // Register the diff with the cursor.
    cursor.replaceDiffs([diffElement]);

    diffElement.loggedIn = false;
    diffElement.comments = {
      left: [],
      right: [],
      meta: {},
    };
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
    diffElement.diff = diff;
    await promise;
  });

  test('diff cursor functionality (side-by-side)', () => {
    // The cursor has been initialized to the first delta.
    assert.isOk(cursor.diffRow);

    const firstDeltaRow = diffElement.shadowRoot
        .querySelector('.section.delta .diff-row');
    assert.equal(cursor.diffRow, firstDeltaRow);

    cursor.moveDown();

    assert.notEqual(cursor.diffRow, firstDeltaRow);
    assert.equal(cursor.diffRow, firstDeltaRow.nextSibling);

    cursor.moveUp();

    assert.notEqual(cursor.diffRow, firstDeltaRow.nextSibling);
    assert.equal(cursor.diffRow, firstDeltaRow);
  });

  test('moveToFirstChunk', async () => {
    const diff = {
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
    diffElement.prefs.show_file_comment_button = false;
    await waitForEventOnce(diffElement, 'render');

    cursor._updateStops();

    const chunks = Array.from(diffElement.root.querySelectorAll(
        '.section.delta'));
    assert.equal(chunks.length, 2);

    // Verify it works on fresh diff.
    cursor.moveToFirstChunk();
    assert.equal(chunks.indexOf(cursor.diffRow.parentElement), 0);
    assert.equal(cursor.side, 'right');

    // Verify it works from other cursor positions.
    cursor.moveToNextChunk();
    assert.equal(chunks.indexOf(cursor.diffRow.parentElement), 1);
    assert.equal(cursor.side, 'left');
    cursor.moveToFirstChunk();
    assert.equal(chunks.indexOf(cursor.diffRow.parentElement), 0);
    assert.equal(cursor.side, 'right');
  });

  test('moveToLastChunk', async () => {
    const diff = {
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

    const chunks = Array.from(diffElement.root.querySelectorAll(
        '.section.delta'));
    assert.equal(chunks.length, 2);

    // Verify it works on fresh diff.
    cursor.moveToLastChunk();
    assert.equal(chunks.indexOf(cursor.diffRow.parentElement), 1);
    assert.equal(cursor.side, 'right');

    // Verify it works from other cursor positions.
    cursor.moveToPreviousChunk();
    assert.equal(chunks.indexOf(cursor.diffRow.parentElement), 0);
    assert.equal(cursor.side, 'left');
    cursor.moveToLastChunk();
    assert.equal(chunks.indexOf(cursor.diffRow.parentElement), 1);
    assert.equal(cursor.side, 'right');
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
          detail: {number: '123', side: 'right', path: 'some/file'},
        }));

    assert.isTrue(moveToNumStub.called);
    assert.equal(moveToNumStub.lastCall.args[0], '123');
    assert.equal(moveToNumStub.lastCall.args[1], 'right');
    assert.equal(moveToNumStub.lastCall.args[2], 'some/file');
  });

  suite('unified diff', () => {
    setup(async () => {
      diffElement.viewMode = 'UNIFIED_DIFF';
      await waitForEventOnce(diffElement, 'render');
      cursor.reInitCursor();
    });

    test('diff cursor functionality (unified)', () => {
      // The cursor has been initialized to the first delta.
      assert.isOk(cursor.diffRow);

      let firstDeltaRow = diffElement.shadowRoot
          .querySelector('.section.delta .diff-row');
      assert.equal(cursor.diffRow, firstDeltaRow);

      firstDeltaRow = diffElement.shadowRoot
          .querySelector('.section.delta .diff-row');
      assert.equal(cursor.diffRow, firstDeltaRow);

      cursor.moveDown();

      assert.notEqual(cursor.diffRow, firstDeltaRow);
      assert.equal(cursor.diffRow, firstDeltaRow.nextSibling);

      cursor.moveUp();

      assert.notEqual(cursor.diffRow, firstDeltaRow.nextSibling);
      assert.equal(cursor.diffRow, firstDeltaRow);
    });
  });

  test('cursor side functionality', () => {
    // The side only applies to side-by-side mode, which should be the default
    // mode.
    assert.equal(diffElement.viewMode, 'SIDE_BY_SIDE');

    const firstDeltaSection = diffElement.shadowRoot
        .querySelector('.section.delta');
    const firstDeltaRow = firstDeltaSection.querySelector('.diff-row');

    // Because the first delta in this diff is on the right, it should be set
    // to the right side.
    assert.equal(cursor.side, 'right');
    assert.equal(cursor.diffRow, firstDeltaRow);
    const firstIndex = cursor.cursorManager.index;

    // Move the side to the left. Because this delta only has a right side, we
    // should be moved up to the previous line where there is content on the
    // right. The previous row is part of the previous section.
    cursor.moveLeft();

    assert.equal(cursor.side, 'left');
    assert.notEqual(cursor.diffRow, firstDeltaRow);
    assert.equal(cursor.cursorManager.index, firstIndex - 1);
    assert.equal(cursor.diffRow.parentElement,
        firstDeltaSection.previousSibling);

    // If we move down, we should skip everything in the first delta because
    // we are on the left side and the first delta has no content on the left.
    cursor.moveDown();

    assert.equal(cursor.side, 'left');
    assert.notEqual(cursor.diffRow, firstDeltaRow);
    assert.isTrue(cursor.cursorManager.index > firstIndex);
    assert.equal(cursor.diffRow.parentElement,
        firstDeltaSection.nextSibling);
  });

  test('chunk skip functionality', () => {
    const chunks = diffElement.root.querySelectorAll(
        '.section.delta');
    const indexOfChunk = function(chunk) {
      return Array.prototype.indexOf.call(chunks, chunk);
    };

    // We should be initialized to the first chunk. Since this chunk only has
    // content on the right side, our side should be right.
    let currentIndex = indexOfChunk(cursor.diffRow.parentElement);
    assert.equal(currentIndex, 0);
    assert.equal(cursor.side, 'right');

    // Move to the next chunk.
    cursor.moveToNextChunk();

    // Since this chunk only has content on the left side. we should have been
    // automatically moved over.
    const previousIndex = currentIndex;
    currentIndex = indexOfChunk(cursor.diffRow.parentElement);
    assert.equal(currentIndex, previousIndex + 1);
    assert.equal(cursor.side, 'left');
  });

  suite('moved chunks without line range)', () => {
    setup(async () => {
      const promise = mockPromise();
      const renderHandler = function() {
        diffElement.removeEventListener('render', renderHandler);
        cursor.reInitCursor();
        promise.resolve();
      };
      diffElement.addEventListener('render', renderHandler);
      diffElement.diff = {...diff, content: [
        {
          ab: [
            'Lorem ipsum dolor sit amet, suspendisse inceptos vehicula, ',
          ],
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
          ab: [
            'Sem nascetur, erat ut, non in.',
          ],
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
          ab: [
            'Arcu eget, rhoncus amet cursus, ipsum elementum.',
          ],
        },
      ]};
      await promise;
    });

    test('renders moveControls with simple descriptions', () => {
      const [movedIn, movedOut] = diffElement.root
          .querySelectorAll('.dueToMove .moveControls');
      assert.equal(movedIn.textContent, 'Moved in');
      assert.equal(movedOut.textContent, 'Moved out');
    });
  });

  suite('moved chunks (moveDetails)', () => {
    setup(async () => {
      const promise = mockPromise();
      const renderHandler = function() {
        diffElement.removeEventListener('render', renderHandler);
        cursor.reInitCursor();
        promise.resolve();
      };
      diffElement.addEventListener('render', renderHandler);
      diffElement.diff = {...diff, content: [
        {
          ab: [
            'Lorem ipsum dolor sit amet, suspendisse inceptos vehicula, ',
          ],
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
          ab: [
            'Sem nascetur, erat ut, non in.',
          ],
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
          ab: [
            'Arcu eget, rhoncus amet cursus, ipsum elementum.',
          ],
        },
      ]};
      await promise;
    });

    test('renders moveControls with simple descriptions', () => {
      const [movedIn, movedOut] = diffElement.root
          .querySelectorAll('.dueToMove .moveControls');
      assert.equal(movedIn.textContent, 'Moved from lines 4 - 6');
      assert.equal(movedOut.textContent, 'Moved to lines 2 - 4');
    });

    test('startLineAnchor of movedIn chunk fires events', async () => {
      const [movedIn] = diffElement.root
          .querySelectorAll('.dueToMove .moveControls');
      const [startLineAnchor] = movedIn.querySelectorAll('a');

      const promise = mockPromise();
      const onMovedLinkClicked = e => {
        assert.deepEqual(e.detail, {lineNum: 4, side: 'left'});
        promise.resolve();
      };
      assert.equal(startLineAnchor.textContent, '4');
      startLineAnchor
          .addEventListener('moved-link-clicked', onMovedLinkClicked);
      MockInteractions.click(startLineAnchor);
      await promise;
    });

    test('endLineAnchor of movedOut fires events', async () => {
      const [, movedOut] = diffElement.root
          .querySelectorAll('.dueToMove .moveControls');
      const [, endLineAnchor] = movedOut.querySelectorAll('a');

      const promise = mockPromise();
      const onMovedLinkClicked = e => {
        assert.deepEqual(e.detail, {lineNum: 4, side: 'right'});
        promise.resolve();
      };
      assert.equal(endLineAnchor.textContent, '4');
      endLineAnchor.addEventListener('moved-link-clicked', onMovedLinkClicked);
      MockInteractions.click(endLineAnchor);
      await promise;
    });
  });

  test('initialLineNumber not provided', async () => {
    let scrollBehaviorDuringMove;
    const moveToNumStub = sinon.stub(cursor, 'moveToLineNumber');
    const moveToChunkStub = sinon.stub(cursor, 'moveToFirstChunk')
        .callsFake(() => {
          scrollBehaviorDuringMove = cursor.cursorManager.scrollMode;
        });

    diffElement._diffChanged(createDiff());
    await waitForEventOnce(diffElement, 'render');
    cursor.reInitCursor();
    assert.isFalse(moveToNumStub.called);
    assert.isTrue(moveToChunkStub.called);
    assert.equal(scrollBehaviorDuringMove, 'never');
    assert.equal(cursor.cursorManager.scrollMode, 'keep-visible');
  });

  test('initialLineNumber provided', async () => {
    let scrollBehaviorDuringMove;
    const moveToNumStub = sinon.stub(cursor, 'moveToLineNumber')
        .callsFake(() => {
          scrollBehaviorDuringMove = cursor.cursorManager.scrollMode;
        });
    const moveToChunkStub = sinon.stub(cursor, 'moveToFirstChunk');
    cursor.initialLineNumber = 10;
    cursor.side = 'right';

    diffElement._diffChanged(createDiff());
    await waitForEventOnce(diffElement, 'render');
    cursor.reInitCursor();
    assert.isFalse(moveToChunkStub.called);
    assert.isTrue(moveToNumStub.called);
    assert.equal(moveToNumStub.lastCall.args[0], 10);
    assert.equal(moveToNumStub.lastCall.args[1], 'right');
    assert.equal(scrollBehaviorDuringMove, 'keep-visible');
    assert.equal(cursor.cursorManager.scrollMode, 'keep-visible');
  });

  test('getTargetDiffElement', () => {
    cursor.initialLineNumber = 1;
    assert.isTrue(!!cursor.diffRow);
    assert.equal(
        cursor.getTargetDiffElement(),
        diffElement
    );
  });

  suite('createCommentInPlace', () => {
    setup(() => {
      diffElement.loggedIn = true;
    });

    test('adds new draft for selected line on the left', async () => {
      cursor.moveToLineNumber(2, 'left');
      const promise = mockPromise();
      diffElement.addEventListener('create-comment', e => {
        const {lineNum, range, side} = e.detail;
        assert.equal(lineNum, 2);
        assert.equal(range, undefined);
        assert.equal(side, 'left');
        promise.resolve();
      });
      cursor.createCommentInPlace();
      await promise;
    });

    test('adds draft for selected line on the right', async () => {
      cursor.moveToLineNumber(4, 'right');
      const promise = mockPromise();
      diffElement.addEventListener('create-comment', e => {
        const {lineNum, range, side} = e.detail;
        assert.equal(lineNum, 4);
        assert.equal(range, undefined);
        assert.equal(side, 'right');
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
        side: 'right',
        range: someRange,
      };
      const promise = mockPromise();
      diffElement.addEventListener('create-comment', e => {
        const {lineNum, range, side} = e.detail;
        assert.equal(lineNum, 6);
        assert.equal(range, someRange);
        assert.equal(side, 'right');
        promise.resolve();
      });
      cursor.createCommentInPlace();
      await promise;
    });

    test('ignores call if nothing is selected', () => {
      const createRangeCommentStub = sinon.stub(diffElement,
          'createRangeComment');
      const addDraftAtLineStub = sinon.stub(diffElement, 'addDraftAtLine');
      cursor.diffRow = undefined;
      cursor.createCommentInPlace();
      assert.isFalse(createRangeCommentStub.called);
      assert.isFalse(addDraftAtLineStub.called);
    });
  });

  test('getAddress', () => {
    // It should initialize to the first chunk: line 5 of the revision.
    assert.deepEqual(cursor.getAddress(),
        {leftSide: false, number: 5});

    // Revision line 4 is up.
    cursor.moveUp();
    assert.deepEqual(cursor.getAddress(),
        {leftSide: false, number: 4});

    // Base line 4 is left.
    cursor.moveLeft();
    assert.deepEqual(cursor.getAddress(), {leftSide: true, number: 4});

    // Moving to the next chunk takes it back to the start.
    cursor.moveToNextChunk();
    assert.deepEqual(cursor.getAddress(),
        {leftSide: false, number: 5});

    // The following chunk is a removal starting on line 10 of the base.
    cursor.moveToNextChunk();
    assert.deepEqual(cursor.getAddress(),
        {leftSide: true, number: 10});

    // Should be null if there is no selection.
    cursor.cursorManager.unsetCursor();
    assert.isNotOk(cursor.getAddress());
  });

  test('_findRowByNumberAndFile', () => {
    // Get the first ab row after the first chunk.
    const row = diffElement.root.querySelectorAll('tr')[9];

    // It should be line 8 on the right, but line 5 on the left.
    assert.equal(cursor._findRowByNumberAndFile(8, 'right'), row);
    assert.equal(cursor._findRowByNumberAndFile(5, 'left'), row);
  });

  test('expand context updates stops', async () => {
    sinon.spy(cursor, '_updateStops');
    MockInteractions.tap(diffElement.shadowRoot
        .querySelector('gr-context-controls').shadowRoot
        .querySelector('.showContext'));
    await waitForEventOnce(diffElement, 'render');
    assert.isTrue(cursor._updateStops.called);
  });

  test('updates stops when loading changes', () => {
    sinon.spy(cursor, '_updateStops');
    diffElement.dispatchEvent(new Event('loading-changed'));
    assert.isTrue(cursor._updateStops.called);
  });

  suite('multi diff', () => {
    let diffElements;

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
      return diffElements.indexOf(cursor.getTargetDiffElement());
    }

    test('do not skip loading diffs', async () => {
      diffElements[0].diff = createDiff();
      diffElements[2].diff = createDiff();
      await waitForEventOnce(diffElements[0], 'render');
      await waitForEventOnce(diffElements[2], 'render');

      const lastLine = diffElements[0].diff.meta_b.lines;

      // Goto second last line of the first diff
      cursor.moveToLineNumber(lastLine - 1, 'right');
      assert.equal(
          cursor.getTargetLineElement().textContent, lastLine - 1);

      // Can move down until we reach the loading file
      cursor.moveDown();
      assert.equal(getTargetDiffIndex(), 0);
      assert.equal(cursor.getTargetLineElement().textContent, lastLine);

      // Cannot move down while still loading the diff we would switch to
      cursor.moveDown();
      assert.equal(getTargetDiffIndex(), 0);
      assert.equal(cursor.getTargetLineElement().textContent, lastLine);

      // Diff 1 finishing to load
      diffElements[1].diff = createDiff();
      await waitForEventOnce(diffElements[1], 'render');

      // Now we can go down
      cursor.moveDown();
      assert.equal(getTargetDiffIndex(), 1);
      assert.equal(cursor.getTargetLineElement().textContent, 'File');
    });
  });
});

