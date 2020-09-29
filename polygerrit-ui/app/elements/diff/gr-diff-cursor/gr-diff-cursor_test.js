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
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import {getMockDiffResponse} from '../../../test/mocks/diff-response.js';
import {html} from '@polymer/polymer/lib/utils/html-tag.js';

const basicFixture = fixtureFromTemplate(html`
  <gr-diff></gr-diff>
  <gr-diff-cursor></gr-diff-cursor>
  <gr-rest-api-interface></gr-rest-api-interface>
`);

const emptyFixture = fixtureFromElement('div');

suite('gr-diff-cursor tests', () => {
  let cursorElement;
  let diffElement;

  setup(done => {
    const fixtureElems = basicFixture.instantiate();
    diffElement = fixtureElems[0];
    cursorElement = fixtureElems[1];
    const restAPI = fixtureElems[2];

    // Register the diff with the cursor.
    cursorElement.push('diffs', diffElement);

    diffElement.loggedIn = false;
    diffElement.patchRange = {basePatchNum: 1, patchNum: 2};
    diffElement.comments = {
      left: [],
      right: [],
      meta: {patchRange: undefined},
    };
    const setupDone = () => {
      cursorElement._updateStops();
      cursorElement.moveToFirstChunk();
      diffElement.removeEventListener('render', setupDone);
      done();
    };
    diffElement.addEventListener('render', setupDone);

    restAPI.getDiffPreferences().then(prefs => {
      diffElement.prefs = prefs;
      diffElement.diff = getMockDiffResponse();
    });
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

  test('moveToLastChunk', () => {
    const chunks = Array.from(diffElement.root.querySelectorAll(
        '.section.delta'));
    assert.isAbove(chunks.length, 1);
    assert.equal(chunks.indexOf(cursorElement.diffRow.parentElement), 0);

    cursorElement.moveToLastChunk();

    assert.equal(chunks.indexOf(cursorElement.diffRow.parentElement),
        chunks.length - 1);
  });

  test('cursor scroll behavior', () => {
    assert.equal(cursorElement._scrollMode, 'keep-visible');

    cursorElement._handleDiffRenderStart();
    assert.isTrue(cursorElement._focusOnMove);

    cursorElement._handleWindowScroll();
    assert.equal(cursorElement._scrollMode, 'never');
    assert.isFalse(cursorElement._focusOnMove);

    cursorElement._handleDiffRenderContent();
    assert.isTrue(cursorElement._focusOnMove);

    cursorElement.reInitCursor();
    assert.equal(cursorElement._scrollMode, 'keep-visible');
  });

  test('moves to selected line', () => {
    const moveToNumStub = sinon.stub(cursorElement, 'moveToLineNumber');

    cursorElement._handleDiffLineSelected(
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
        const {lineNum, range, side, patchNum} = e.detail;
        assert.equal(lineNum, 2);
        assert.equal(range, undefined);
        assert.equal(patchNum, 1);
        assert.equal(side, 'left');
        done();
      });
      cursorElement.createCommentInPlace();
    });

    test('adds draft for selected line on the right', done => {
      cursorElement.moveToLineNumber(4, 'right');
      diffElement.addEventListener('create-comment', e => {
        const {lineNum, range, side, patchNum} = e.detail;
        assert.equal(lineNum, 4);
        assert.equal(range, undefined);
        assert.equal(patchNum, 2);
        assert.equal(side, 'right');
        done();
      });
      cursorElement.createCommentInPlace();
    });

    test('createCommentInPlace creates comment for range if selected', done => {
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
        const {lineNum, range, side, patchNum} = e.detail;
        assert.equal(lineNum, 6);
        assert.equal(range, someRange);
        assert.equal(patchNum, 2);
        assert.equal(side, 'right');
        done();
      });
      cursorElement.createCommentInPlace();
    });

    test('createCommentInPlace ignores call if nothing is selected', () => {
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
});

