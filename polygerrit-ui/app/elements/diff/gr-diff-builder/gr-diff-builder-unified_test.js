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
import '../gr-diff/gr-diff-group.js';
import './gr-diff-builder.js';
import './gr-diff-builder-unified.js';
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line.js';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group.js';
import {GrDiffBuilderUnified} from './gr-diff-builder-unified.js';

suite('GrDiffBuilderUnified tests', () => {
  let prefs;
  let outputEl;
  let diffBuilder;

  setup(()=> {
    prefs = {
      line_length: 10,
      show_tabs: true,
      tab_size: 4,
    };
    outputEl = document.createElement('div');
    diffBuilder = new GrDiffBuilderUnified({}, prefs, outputEl, []);
  });

  suite('buildSectionElement for BOTH group', () => {
    let lines;
    let group;

    setup(() => {
      lines = [
        new GrDiffLine(GrDiffLineType.BOTH, 1, 2),
        new GrDiffLine(GrDiffLineType.BOTH, 2, 3),
        new GrDiffLine(GrDiffLineType.BOTH, 3, 4),
      ];
      lines[0].text = 'def hello_world():';
      lines[1].text = '  print "Hello World";';
      lines[2].text = '  return True';

      group = new GrDiffGroup({type: GrDiffGroupType.BOTH, lines});
    });

    test('creates the section', () => {
      const sectionEl = diffBuilder.buildSectionElement(group);
      assert.isTrue(sectionEl.classList.contains('section'));
      assert.isTrue(sectionEl.classList.contains('both'));
    });

    test('creates each unchanged row once', () => {
      const sectionEl = diffBuilder.buildSectionElement(group);
      const rowEls = sectionEl.querySelectorAll('.diff-row');

      assert.equal(rowEls.length, 3);

      assert.equal(
          rowEls[0].querySelector('.lineNum.left').textContent,
          lines[0].beforeNumber);
      assert.equal(
          rowEls[0].querySelector('.lineNum.right').textContent,
          lines[0].afterNumber);
      assert.equal(
          rowEls[0].querySelector('.content').textContent, lines[0].text);

      assert.equal(
          rowEls[1].querySelector('.lineNum.left').textContent,
          lines[1].beforeNumber);
      assert.equal(
          rowEls[1].querySelector('.lineNum.right').textContent,
          lines[1].afterNumber);
      assert.equal(
          rowEls[1].querySelector('.content').textContent, lines[1].text);

      assert.equal(
          rowEls[2].querySelector('.lineNum.left').textContent,
          lines[2].beforeNumber);
      assert.equal(
          rowEls[2].querySelector('.lineNum.right').textContent,
          lines[2].afterNumber);
      assert.equal(
          rowEls[2].querySelector('.content').textContent, lines[2].text);
    });
  });

  suite('buildSectionElement for moved chunks', () => {
    test('creates a moved out group', () => {
      const lines = [
        new GrDiffLine(GrDiffLineType.REMOVE, 15),
        new GrDiffLine(GrDiffLineType.REMOVE, 16),
      ];
      lines[0].text = 'def hello_world():';
      lines[1].text = '  print "Hello World"';
      const group = new GrDiffGroup({type: GrDiffGroupType.DELTA, lines});
      group.moveDetails = {changed: false};

      const sectionEl = diffBuilder.buildSectionElement(group);

      const rowEls = sectionEl.querySelectorAll('tr');
      const moveControlsRow = rowEls[0];
      const cells = moveControlsRow.querySelectorAll('td');
      assert.isTrue(sectionEl.classList.contains('dueToMove'));
      assert.equal(rowEls.length, 3);
      assert.isTrue(moveControlsRow.classList.contains('movedOut'));
      assert.equal(cells.length, 3);
      assert.isTrue(cells[2].classList.contains('moveHeader'));
      assert.equal(cells[2].textContent, 'Moved out');
    });

    test('creates a moved in group', () => {
      const lines = [
        new GrDiffLine(GrDiffLineType.ADD, 37),
        new GrDiffLine(GrDiffLineType.ADD, 38),
      ];
      lines[0].text = 'def hello_world():';
      lines[1].text = '  print "Hello World"';
      const group = new GrDiffGroup({type: GrDiffGroupType.DELTA, lines});
      group.moveDetails = {changed: false};

      const sectionEl = diffBuilder.buildSectionElement(group);

      const rowEls = sectionEl.querySelectorAll('tr');
      const moveControlsRow = rowEls[0];
      const cells = moveControlsRow.querySelectorAll('td');
      assert.isTrue(sectionEl.classList.contains('dueToMove'));
      assert.equal(rowEls.length, 3);
      assert.isTrue(moveControlsRow.classList.contains('movedIn'));
      assert.equal(cells.length, 3);
      assert.isTrue(cells[2].classList.contains('moveHeader'));
      assert.equal(cells[2].textContent, 'Moved in');
    });
  });

  suite('buildSectionElement for DELTA group', () => {
    let lines;
    let group;

    setup(() => {
      lines = [
        new GrDiffLine(GrDiffLineType.REMOVE, 1),
        new GrDiffLine(GrDiffLineType.REMOVE, 2),
        new GrDiffLine(GrDiffLineType.ADD, 2),
        new GrDiffLine(GrDiffLineType.ADD, 3),
      ];
      lines[0].text = 'def hello_world():';
      lines[1].text = '  print "Hello World"';
      lines[2].text = 'def hello_universe()';
      lines[3].text = '  print "Hello Universe"';

      group = new GrDiffGroup({type: GrDiffGroupType.DELTA, lines});
    });

    test('creates the section', () => {
      const sectionEl = diffBuilder.buildSectionElement(group);
      assert.isTrue(sectionEl.classList.contains('section'));
      assert.isTrue(sectionEl.classList.contains('delta'));
    });

    test('creates the section with class if ignoredWhitespaceOnly', () => {
      group.ignoredWhitespaceOnly = true;
      const sectionEl = diffBuilder.buildSectionElement(group);
      assert.isTrue(sectionEl.classList.contains('ignoredWhitespaceOnly'));
    });

    test('creates the section with class if dueToRebase', () => {
      group.dueToRebase = true;
      const sectionEl = diffBuilder.buildSectionElement(group);
      assert.isTrue(sectionEl.classList.contains('dueToRebase'));
    });

    test('creates first the removed and then the added rows', () => {
      const sectionEl = diffBuilder.buildSectionElement(group);
      const rowEls = sectionEl.querySelectorAll('.diff-row');

      assert.equal(rowEls.length, 4);

      assert.equal(
          rowEls[0].querySelector('.lineNum.left').textContent,
          lines[0].beforeNumber);
      assert.isNotOk(rowEls[0].querySelector('.lineNum.right'));
      assert.equal(
          rowEls[0].querySelector('.content').textContent, lines[0].text);

      assert.equal(
          rowEls[1].querySelector('.lineNum.left').textContent,
          lines[1].beforeNumber);
      assert.isNotOk(rowEls[1].querySelector('.lineNum.right'));
      assert.equal(
          rowEls[1].querySelector('.content').textContent, lines[1].text);

      assert.isNotOk(rowEls[2].querySelector('.lineNum.left'));
      assert.equal(
          rowEls[2].querySelector('.lineNum.right').textContent,
          lines[2].afterNumber);
      assert.equal(
          rowEls[2].querySelector('.content').textContent, lines[2].text);

      assert.isNotOk(rowEls[3].querySelector('.lineNum.left'));
      assert.equal(
          rowEls[3].querySelector('.lineNum.right').textContent,
          lines[3].afterNumber);
      assert.equal(
          rowEls[3].querySelector('.content').textContent, lines[3].text);
    });

    test('creates only the added rows if only ignored whitespace', () => {
      group.ignoredWhitespaceOnly = true;
      const sectionEl = diffBuilder.buildSectionElement(group);
      const rowEls = sectionEl.querySelectorAll('.diff-row');

      assert.equal(rowEls.length, 2);

      assert.isNotOk(rowEls[0].querySelector('.lineNum.left'));
      assert.equal(
          rowEls[0].querySelector('.lineNum.right').textContent,
          lines[2].afterNumber);
      assert.equal(
          rowEls[0].querySelector('.content').textContent, lines[2].text);

      assert.isNotOk(rowEls[1].querySelector('.lineNum.left'));
      assert.equal(
          rowEls[1].querySelector('.lineNum.right').textContent,
          lines[3].afterNumber);
      assert.equal(
          rowEls[1].querySelector('.content').textContent, lines[3].text);
    });
  });
});

