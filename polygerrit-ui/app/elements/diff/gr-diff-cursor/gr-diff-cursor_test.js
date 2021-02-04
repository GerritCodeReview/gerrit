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
import {html} from '@polymer/polymer/lib/utils/html-tag.js';
import {listenOnce} from '../../../test/test-utils.js';
import {getMockDiffResponse} from '../../../test/mocks/diff-response.js';
import {createDefaultDiffPrefs} from '../../../constants/constants.js';

const basicFixture = fixtureFromTemplate(html`
  <gr-diff></gr-diff>
  <gr-diff-cursor></gr-diff-cursor>
`);

const emptyFixture = fixtureFromElement('div');

suite('gr-diff-cursor tests', () => {
  let cursorElement;
  let diffElement;
  let diff;

  setup(done => {
    const fixtureElems = basicFixture.instantiate();
    diffElement = fixtureElems[0];
    cursorElement = fixtureElems[1];

    // Register the diff with the cursor.
    cursorElement.push('diffs', diffElement);

    diffElement.loggedIn = false;
    diffElement.patchRange = {basePatchNum: 1, patchNum: 2};
    diffElement.comments = {
      left: [],
      right: [],
      meta: {patchRange: undefined},
    };
    diffElement.path = 'some/path.ts';
    const setupDone = () => {
      cursorElement._updateStops();
      cursorElement.moveToFirstChunk();
      diffElement.removeEventListener('render', setupDone);
      done();
    };
    diffElement.addEventListener('render', setupDone);

    diff = getMockDiffResponse();
    diffElement.prefs = createDefaultDiffPrefs();
    diffElement.diff = diff;
  });

  test('diff cursor functionality (side-by-side)', () => {
    // The cursor has been initialized to the first delta.
    assert.isOk(cursorElement.diffRow);

    const firstDeltaRow = diffElement.shadowRoot
        .querySelector('.section.delta .diff-row');
    assert.equal(cursorElement.diffRow, firstDeltaRow);

    cursorElement.moveDown();

    assert.notEqual(cursorElement.diffRow, firstDeltaRow);
    assert.equal(cursorElement.diffRow, firstDeltaRow.nextSibling);

    cursorElement.moveUp();

    assert.notEqual(cursorElement.diffRow, firstDeltaRow.nextSibling);
    assert.equal(cursorElement.diffRow, firstDeltaRow);
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
    await flush();
    cursorElement._updateStops();

    const chunks = Array.from(diffElement.root.querySelectorAll(
        '.section.delta'));
    assert.equal(chunks.length, 2);

    // Verify it works on fresh diff.
    cursorElement.moveToFirstChunk();
    assert.equal(chunks.indexOf(cursorElement.diffRow.parentElement), 0);
    assert.equal(cursorElement.side, 'right');

    // Verify it works from other cursor positions.
    cursorElement.moveToNextChunk();
    assert.equal(chunks.indexOf(cursorElement.diffRow.parentElement), 1);
    assert.equal(cursorElement.side, 'left');
    cursorElement.moveToFirstChunk();
    assert.equal(chunks.indexOf(cursorElement.diffRow.parentElement), 0);
    assert.equal(cursorElement.side, 'right');
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
    await flush();
    cursorElement._updateStops();

    const chunks = Array.from(diffElement.root.querySelectorAll(
        '.section.delta'));
    assert.equal(chunks.length, 2);

    // Verify it works on fresh diff.
    cursorElement.moveToLastChunk();
    assert.equal(chunks.indexOf(cursorElement.diffRow.parentElement), 1);
    assert.equal(cursorElement.side, 'right');

    // Verify it works from other cursor positions.
    cursorElement.moveToPreviousChunk();
    assert.equal(chunks.indexOf(cursorElement.diffRow.parentElement), 0);
    assert.equal(cursorElement.side, 'left');
    cursorElement.moveToLastChunk();
    assert.equal(chunks.indexOf(cursorElement.diffRow.parentElement), 1);
    assert.equal(cursorElement.side, 'right');
  });

  test('cursor scroll behavior', () => {
    assert.equal(cursorElement._scrollMode, 'keep-visible');

    diffElement.dispatchEvent(new Event('render-start'));
    assert.isTrue(cursorElement._focusOnMove);

    window.dispatchEvent(new Event('scroll'));
    assert.equal(cursorElement._scrollMode, 'never');
    assert.isFalse(cursorElement._focusOnMove);

    diffElement.dispatchEvent(new Event('render-content'));
    assert.isTrue(cursorElement._focusOnMove);

    cursorElement.reInitCursor();
    assert.equal(cursorElement._scrollMode, 'keep-visible');
  });

  test('moves to selected line', () => {
    const moveToNumStub = sinon.stub(cursorElement, 'moveToLineNumber');

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
    setup(done => {
      // We must allow the diff to re-render after setting the viewMode.
      const renderHandler = function() {
        diffElement.removeEventListener('render', renderHandler);
        cursorElement.reInitCursor();
        done();
      };
      diffElement.addEventListener('render', renderHandler);
      diffElement.viewMode = 'UNIFIED_DIFF';
    });

    test('diff cursor functionality (unified)', () => {
      // The cursor has been initialized to the first delta.
      assert.isOk(cursorElement.diffRow);

      let firstDeltaRow = diffElement.shadowRoot
          .querySelector('.section.delta .diff-row');
      assert.equal(cursorElement.diffRow, firstDeltaRow);

      firstDeltaRow = diffElement.shadowRoot
          .querySelector('.section.delta .diff-row');
      assert.equal(cursorElement.diffRow, firstDeltaRow);

      cursorElement.moveDown();

      assert.notEqual(cursorElement.diffRow, firstDeltaRow);
      assert.equal(cursorElement.diffRow, firstDeltaRow.nextSibling);

      cursorElement.moveUp();

      assert.notEqual(cursorElement.diffRow, firstDeltaRow.nextSibling);
      assert.equal(cursorElement.diffRow, firstDeltaRow);
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
    assert.equal(cursorElement.side, 'right');
    assert.equal(cursorElement.diffRow, firstDeltaRow);
    const firstIndex = cursorElement.$.cursorManager.index;

    // Move the side to the left. Because this delta only has a right side, we
    // should be moved up to the previous line where there is content on the
    // right. The previous row is part of the previous section.
    cursorElement.moveLeft();

    assert.equal(cursorElement.side, 'left');
    assert.notEqual(cursorElement.diffRow, firstDeltaRow);
    assert.equal(cursorElement.$.cursorManager.index, firstIndex - 1);
    assert.equal(cursorElement.diffRow.parentElement,
        firstDeltaSection.previousSibling);

    // If we move down, we should skip everything in the first delta because
    // we are on the left side and the first delta has no content on the left.
    cursorElement.moveDown();

    assert.equal(cursorElement.side, 'left');
    assert.notEqual(cursorElement.diffRow, firstDeltaRow);
    assert.isTrue(cursorElement.$.cursorManager.index > firstIndex);
    assert.equal(cursorElement.diffRow.parentElement,
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
    let currentIndex = indexOfChunk(cursorElement.diffRow.parentElement);
    assert.equal(currentIndex, 0);
    assert.equal(cursorElement.side, 'right');

    // Move to the next chunk.
    cursorElement.moveToNextChunk();

    // Since this chunk only has content on the left side. we should have been
    // automatically moved over.
    const previousIndex = currentIndex;
    currentIndex = indexOfChunk(cursorElement.diffRow.parentElement);
    assert.equal(currentIndex, previousIndex + 1);
    assert.equal(cursorElement.side, 'left');
  });

  suite('moved chunks without line range)', () => {
    setup(done => {
      const renderHandler = function() {
        diffElement.removeEventListener('render', renderHandler);
        cursorElement.reInitCursor();
        done();
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
    });

    test('renders moveControls with simple descriptions', () => {
      const [movedIn, movedOut] = diffElement.root
          .querySelectorAll('.dueToMove .moveControls');
      assert.equal(movedIn.textContent, 'Moved in');
      assert.equal(movedOut.textContent, 'Moved out');
    });
  });

  suite('moved chunks (moveDetails)', () => {
    setup(done => {
      const renderHandler = function() {
        diffElement.removeEventListener('render', renderHandler);
        cursorElement.reInitCursor();
        done();
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
    });

    test('renders moveControls with simple descriptions', () => {
      const [movedIn, movedOut] = diffElement.root
          .querySelectorAll('.dueToMove .moveControls');
      assert.equal(movedIn.textContent, 'Moved from lines 4 - 6');
      assert.equal(movedOut.textContent, 'Moved to lines 2 - 4');
    });

    test('startLineAnchor of movedIn chunk fires events', done => {
      const [movedIn] = diffElement.root
          .querySelectorAll('.dueToMove .moveControls');
      const [startLineAnchor] = movedIn.querySelectorAll('a');

      const onMovedLinkClicked = e => {
        assert.deepEqual(e.detail, {line: 4, side: 'left'});
        done();
      };
      assert.equal(startLineAnchor.textContent, '4');
      startLineAnchor
          .addEventListener('moved-link-clicked', onMovedLinkClicked);
      MockInteractions.click(startLineAnchor);
    });

    test('endLineAnchor of movedOut fires events', done => {
      const [, movedOut] = diffElement.root
          .querySelectorAll('.dueToMove .moveControls');
      const [, endLineAnchor] = movedOut.querySelectorAll('a');

      const onMovedLinkClicked = e => {
        assert.deepEqual(e.detail, {line: 4, side: 'right'});
        done();
      };
      assert.equal(endLineAnchor.textContent, '4');
      endLineAnchor.addEventListener('moved-link-clicked', onMovedLinkClicked);
      MockInteractions.click(endLineAnchor);
    });
  });

  test('navigate to next unreviewed file via moveToNextChunk', () => {
    const cursorManager =
        cursorElement.shadowRoot.querySelector('#cursorManager');
    cursorManager.index = cursorManager.stops.length - 1;
    const dispatchEventStub = sinon.stub(cursorElement, 'dispatchEvent');
    cursorElement.moveToNextChunk(/* opt_clipToTop = */false,
        /* opt_navigateToNextFile = */true);
    assert.isTrue(dispatchEventStub.called);
    assert.equal(dispatchEventStub.getCall(1).args[0].type, 'show-alert');

    cursorElement.moveToNextChunk(/* opt_clipToTop = */false,
        /* opt_navigateToNextFile = */true);
    assert.equal(dispatchEventStub.getCall(2).args[0].type,
        'navigate-to-next-unreviewed-file');
  });

  test('initialLineNumber not provided', done => {
    let scrollBehaviorDuringMove;
    const moveToNumStub = sinon.stub(cursorElement, 'moveToLineNumber');
    const moveToChunkStub = sinon.stub(cursorElement, 'moveToFirstChunk')
        .callsFake(
            () => { scrollBehaviorDuringMove = cursorElement._scrollMode; });

    function renderHandler() {
      diffElement.removeEventListener('render', renderHandler);
      cursorElement.reInitCursor();
      assert.isFalse(moveToNumStub.called);
      assert.isTrue(moveToChunkStub.called);
      assert.equal(scrollBehaviorDuringMove, 'never');
      assert.equal(cursorElement._scrollMode, 'keep-visible');
      done();
    }
    diffElement.addEventListener('render', renderHandler);
    diffElement._diffChanged(getMockDiffResponse());
  });

  test('initialLineNumber provided', done => {
    let scrollBehaviorDuringMove;
    const moveToNumStub = sinon.stub(cursorElement, 'moveToLineNumber')
        .callsFake(
            () => { scrollBehaviorDuringMove = cursorElement._scrollMode; });
    const moveToChunkStub = sinon.stub(cursorElement, 'moveToFirstChunk');
    function renderHandler() {
      diffElement.removeEventListener('render', renderHandler);
      cursorElement.reInitCursor();
      assert.isFalse(moveToChunkStub.called);
      assert.isTrue(moveToNumStub.called);
      assert.equal(moveToNumStub.lastCall.args[0], 10);
      assert.equal(moveToNumStub.lastCall.args[1], 'right');
      assert.equal(scrollBehaviorDuringMove, 'keep-visible');
      assert.equal(cursorElement._scrollMode, 'keep-visible');
      done();
    }
    diffElement.addEventListener('render', renderHandler);
    cursorElement.initialLineNumber = 10;
    cursorElement.side = 'right';

    diffElement._diffChanged(getMockDiffResponse());
  });

  test('getTargetDiffElement', () => {
    cursorElement.initialLineNumber = 1;
    assert.isTrue(!!cursorElement.diffRow);
    assert.equal(
        cursorElement.getTargetDiffElement(),
        diffElement
    );
  });

  suite('createCommentInPlace', () => {
    setup(() => {
      diffElement.loggedIn = true;
    });

    test('adds new draft for selected line on the left', done => {
      cursorElement.moveToLineNumber(2, 'left');
      diffElement.addEventListener('create-comment', e => {
        const {lineNum, range, side} = e.detail;
        assert.equal(lineNum, 2);
        assert.equal(range, undefined);
        assert.equal(side, 'left');
        done();
      });
      cursorElement.createCommentInPlace();
    });

    test('adds draft for selected line on the right', done => {
      cursorElement.moveToLineNumber(4, 'right');
      diffElement.addEventListener('create-comment', e => {
        const {lineNum, range, side} = e.detail;
        assert.equal(lineNum, 4);
        assert.equal(range, undefined);
        assert.equal(side, 'right');
        done();
      });
      cursorElement.createCommentInPlace();
    });

    test('creates comment for range if selected', done => {
      const someRange = {
        start_line: 2,
        start_character: 3,
        end_line: 6,
        end_character: 1,
      };
      diffElement.$.highlights.selectedRange = {
        side: 'right',
        range: someRange,
      };
      diffElement.addEventListener('create-comment', e => {
        const {lineNum, range, side} = e.detail;
        assert.equal(lineNum, 6);
        assert.equal(range, someRange);
        assert.equal(side, 'right');
        done();
      });
      cursorElement.createCommentInPlace();
    });

    test('ignores call if nothing is selected', () => {
      const createRangeCommentStub = sinon.stub(diffElement,
          'createRangeComment');
      const addDraftAtLineStub = sinon.stub(diffElement, 'addDraftAtLine');
      cursorElement.diffRow = undefined;
      cursorElement.createCommentInPlace();
      assert.isFalse(createRangeCommentStub.called);
      assert.isFalse(addDraftAtLineStub.called);
    });
  });

  test('getAddress', () => {
    // It should initialize to the first chunk: line 5 of the revision.
    assert.deepEqual(cursorElement.getAddress(),
        {leftSide: false, number: 5});

    // Revision line 4 is up.
    cursorElement.moveUp();
    assert.deepEqual(cursorElement.getAddress(),
        {leftSide: false, number: 4});

    // Base line 4 is left.
    cursorElement.moveLeft();
    assert.deepEqual(cursorElement.getAddress(), {leftSide: true, number: 4});

    // Moving to the next chunk takes it back to the start.
    cursorElement.moveToNextChunk();
    assert.deepEqual(cursorElement.getAddress(),
        {leftSide: false, number: 5});

    // The following chunk is a removal starting on line 10 of the base.
    cursorElement.moveToNextChunk();
    assert.deepEqual(cursorElement.getAddress(),
        {leftSide: true, number: 10});

    // Should be null if there is no selection.
    cursorElement.$.cursorManager.unsetCursor();
    assert.isNotOk(cursorElement.getAddress());
  });

  test('_findRowByNumberAndFile', () => {
    // Get the first ab row after the first chunk.
    const row = diffElement.root.querySelectorAll('tr')[8];

    // It should be line 8 on the right, but line 5 on the left.
    assert.equal(cursorElement._findRowByNumberAndFile(8, 'right'), row);
    assert.equal(cursorElement._findRowByNumberAndFile(5, 'left'), row);
  });

  test('expand context updates stops', done => {
    sinon.spy(cursorElement, '_updateStops');
    MockInteractions.tap(diffElement.shadowRoot
        .querySelector('.showContext'));
    flush(() => {
      assert.isTrue(cursorElement._updateStops.called);
      done();
    });
  });

  test('updates stops when loading changes', () => {
    sinon.spy(cursorElement, '_updateStops');
    diffElement.dispatchEvent(new Event('loading-changed'));
    assert.isTrue(cursorElement._updateStops.called);
  });

  suite('gr-diff-cursor event tests', () => {
    let someEmptyDiv;

    setup(() => {
      someEmptyDiv = emptyFixture.instantiate();
    });

    teardown(() => sinon.restore());

    test('ready is fired after component is rendered', done => {
      const cursorElement = document.createElement('gr-diff-cursor');
      cursorElement.addEventListener('ready', () => {
        done();
      });
      someEmptyDiv.appendChild(cursorElement);
    });
  });

  suite('multi diff', () => {
    const multiDiffFixture = fixtureFromTemplate(html`
      <gr-diff></gr-diff>
      <gr-diff></gr-diff>
      <gr-diff></gr-diff>
      <gr-diff-cursor></gr-diff-cursor>
    `);

    let diffElements;

    setup(() => {
      const fixtureElems = multiDiffFixture.instantiate();
      diffElements = fixtureElems.slice(0, 3);
      cursorElement = fixtureElems[3];

      // Register the diff with the cursor.
      cursorElement.push('diffs', ...diffElements);

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
      return diffElements.indexOf(cursorElement.getTargetDiffElement());
    }

    test('do not skip loading diffs', async () => {
      const diffRenderedPromises =
          diffElements.map(diffEl => listenOnce(diffEl, 'render'));

      diffElements[0].diff = getMockDiffResponse();
      diffElements[2].diff = getMockDiffResponse();
      await Promise.all([diffRenderedPromises[0], diffRenderedPromises[2]]);

      const lastLine = diffElements[0].diff.meta_b.lines;

      // Goto second last line of the first diff
      cursorElement.moveToLineNumber(lastLine - 1, 'right');
      assert.equal(
          cursorElement.getTargetLineElement().textContent, lastLine - 1);

      // Can move down until we reach the loading file
      cursorElement.moveDown();
      assert.equal(getTargetDiffIndex(), 0);
      assert.equal(cursorElement.getTargetLineElement().textContent, lastLine);

      // Cannot move down while still loading the diff we would switch to
      cursorElement.moveDown();
      assert.equal(getTargetDiffIndex(), 0);
      assert.equal(cursorElement.getTargetLineElement().textContent, lastLine);

      // Diff 1 finishing to load
      diffElements[1].diff = getMockDiffResponse();
      await diffRenderedPromises[1];

      // Now we can go down
      cursorElement.moveDown();
      assert.equal(getTargetDiffIndex(), 1);
      assert.equal(cursorElement.getTargetLineElement().textContent, 'File');
    });
  });
});

