/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup-karma';
import '../gr-diff/gr-diff-group';
import './gr-diff-builder';
import './gr-diff-builder-unified';
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {GrDiffBuilderUnified} from './gr-diff-builder-unified';
import {DiffPreferencesInfo} from '../../../api/diff';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {createDiff} from '../../../test/test-data-generators';
import {queryAndAssert} from '../../../utils/common-util';

suite('GrDiffBuilderUnified tests', () => {
  let prefs: DiffPreferencesInfo;
  let outputEl: HTMLElement;
  let diffBuilder: GrDiffBuilderUnified;

  setup(() => {
    prefs = {
      ...createDefaultDiffPrefs(),
      line_length: 10,
      show_tabs: true,
      tab_size: 4,
    };
    outputEl = document.createElement('div');
    diffBuilder = new GrDiffBuilderUnified(createDiff(), prefs, outputEl, []);
  });

  suite('buildSectionElement for BOTH group', () => {
    let lines: GrDiffLine[];
    let group: GrDiffGroup;

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
        queryAndAssert(rowEls[0], '.lineNum.left').textContent,
        lines[0].beforeNumber.toString()
      );
      assert.equal(
        queryAndAssert(rowEls[0], '.lineNum.right').textContent,
        lines[0].afterNumber.toString()
      );
      assert.equal(
        queryAndAssert(rowEls[0], '.content').textContent,
        lines[0].text
      );

      assert.equal(
        queryAndAssert(rowEls[1], '.lineNum.left').textContent,
        lines[1].beforeNumber.toString()
      );
      assert.equal(
        queryAndAssert(rowEls[1], '.lineNum.right').textContent,
        lines[1].afterNumber.toString()
      );
      assert.equal(
        queryAndAssert(rowEls[1], '.content').textContent,
        lines[1].text
      );

      assert.equal(
        queryAndAssert(rowEls[2], '.lineNum.left').textContent,
        lines[2].beforeNumber.toString()
      );
      assert.equal(
        queryAndAssert(rowEls[2], '.lineNum.right').textContent,
        lines[2].afterNumber.toString()
      );
      assert.equal(
        queryAndAssert(rowEls[2], '.content').textContent,
        lines[2].text
      );
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
      const group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines,
        moveDetails: {changed: false},
      });

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
      const group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines,
        moveDetails: {changed: false},
      });

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
    let lines: GrDiffLine[];
    let group: GrDiffGroup;

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
    });

    test('creates the section', () => {
      group = new GrDiffGroup({type: GrDiffGroupType.DELTA, lines});
      const sectionEl = diffBuilder.buildSectionElement(group);
      assert.isTrue(sectionEl.classList.contains('section'));
      assert.isTrue(sectionEl.classList.contains('delta'));
    });

    test('creates the section with class if ignoredWhitespaceOnly', () => {
      group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines,
        ignoredWhitespaceOnly: true,
      });
      const sectionEl = diffBuilder.buildSectionElement(group);
      assert.isTrue(sectionEl.classList.contains('ignoredWhitespaceOnly'));
    });

    test('creates the section with class if dueToRebase', () => {
      group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines,
        dueToRebase: true,
      });
      const sectionEl = diffBuilder.buildSectionElement(group);
      assert.isTrue(sectionEl.classList.contains('dueToRebase'));
    });

    test('creates first the removed and then the added rows', () => {
      group = new GrDiffGroup({type: GrDiffGroupType.DELTA, lines});
      const sectionEl = diffBuilder.buildSectionElement(group);
      const rowEls = sectionEl.querySelectorAll('.diff-row');

      assert.equal(rowEls.length, 4);

      assert.equal(
        queryAndAssert(rowEls[0], '.lineNum.left').textContent,
        lines[0].beforeNumber.toString()
      );
      assert.isNotOk(rowEls[0].querySelector('.lineNum.right'));
      assert.equal(
        queryAndAssert(rowEls[0], '.content').textContent,
        lines[0].text
      );

      assert.equal(
        queryAndAssert(rowEls[1], '.lineNum.left').textContent,
        lines[1].beforeNumber.toString()
      );
      assert.isNotOk(rowEls[1].querySelector('.lineNum.right'));
      assert.equal(
        queryAndAssert(rowEls[1], '.content').textContent,
        lines[1].text
      );

      assert.isNotOk(rowEls[2].querySelector('.lineNum.left'));
      assert.equal(
        queryAndAssert(rowEls[2], '.lineNum.right').textContent,
        lines[2].afterNumber.toString()
      );
      assert.equal(
        queryAndAssert(rowEls[2], '.content').textContent,
        lines[2].text
      );

      assert.isNotOk(rowEls[3].querySelector('.lineNum.left'));
      assert.equal(
        queryAndAssert(rowEls[3], '.lineNum.right').textContent,
        lines[3].afterNumber.toString()
      );
      assert.equal(
        queryAndAssert(rowEls[3], '.content').textContent,
        lines[3].text
      );
    });

    test('creates only the added rows if only ignored whitespace', () => {
      group = new GrDiffGroup({
        type: GrDiffGroupType.DELTA,
        lines,
        ignoredWhitespaceOnly: true,
      });
      const sectionEl = diffBuilder.buildSectionElement(group);
      const rowEls = sectionEl.querySelectorAll('.diff-row');

      assert.equal(rowEls.length, 2);

      assert.isNotOk(rowEls[0].querySelector('.lineNum.left'));
      assert.equal(
        queryAndAssert(rowEls[0], '.lineNum.right').textContent,
        lines[2].afterNumber.toString()
      );
      assert.equal(
        queryAndAssert(rowEls[0], '.content').textContent,
        lines[2].text
      );

      assert.isNotOk(rowEls[1].querySelector('.lineNum.left'));
      assert.equal(
        queryAndAssert(rowEls[1], '.lineNum.right').textContent,
        lines[3].afterNumber.toString()
      );
      assert.equal(
        queryAndAssert(rowEls[1], '.content').textContent,
        lines[3].text
      );
    });
  });
});
