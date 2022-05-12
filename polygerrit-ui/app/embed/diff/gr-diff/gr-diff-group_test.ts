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
import {GrDiffLine, GrDiffLineType, BLANK_LINE} from './gr-diff-line.js';
import {GrDiffGroup, GrDiffGroupType, hideInContextControl} from './gr-diff-group.js';

suite('gr-diff-group tests', () => {
  test('delta line pairs', () => {
    const l1 = new GrDiffLine(GrDiffLineType.ADD, 0, 128);
    const l2 = new GrDiffLine(GrDiffLineType.ADD, 0, 129);
    const l3 = new GrDiffLine(GrDiffLineType.REMOVE, 64, 0);
    let group = new GrDiffGroup({type: GrDiffGroupType.DELTA, lines: [
      l1, l2, l3,
    ]});
    assert.deepEqual(group.lines, [l1, l2, l3]);
    assert.deepEqual(group.adds, [l1, l2]);
    assert.deepEqual(group.removes, [l3]);
    assert.deepEqual(group.lineRange, {
      left: {start_line: 64, end_line: 64},
      right: {start_line: 128, end_line: 129},
    });

    let pairs = group.getSideBySidePairs();
    assert.deepEqual(pairs, [
      {left: l3, right: l1},
      {left: BLANK_LINE, right: l2},
    ]);

    group = new GrDiffGroup({type: GrDiffGroupType.DELTA, lines: [l1, l2, l3]});
    assert.deepEqual(group.lines, [l1, l2, l3]);
    assert.deepEqual(group.adds, [l1, l2]);
    assert.deepEqual(group.removes, [l3]);

    pairs = group.getSideBySidePairs();
    assert.deepEqual(pairs, [
      {left: l3, right: l1},
      {left: BLANK_LINE, right: l2},
    ]);
  });

  test('group/header line pairs', () => {
    const l1 = new GrDiffLine(GrDiffLineType.BOTH, 64, 128);
    const l2 = new GrDiffLine(GrDiffLineType.BOTH, 65, 129);
    const l3 = new GrDiffLine(GrDiffLineType.BOTH, 66, 130);

    const group = new GrDiffGroup({
      type: GrDiffGroupType.BOTH, lines: [l1, l2, l3]});

    assert.deepEqual(group.lines, [l1, l2, l3]);
    assert.deepEqual(group.adds, []);
    assert.deepEqual(group.removes, []);

    assert.deepEqual(group.lineRange, {
      left: {start_line: 64, end_line: 66},
      right: {start_line: 128, end_line: 130},
    });

    const pairs = group.getSideBySidePairs();
    assert.deepEqual(pairs, [
      {left: l1, right: l1},
      {left: l2, right: l2},
      {left: l3, right: l3},
    ]);
  });

  test('adding delta lines to non-delta group', () => {
    const l1 = new GrDiffLine(GrDiffLineType.ADD);
    const l2 = new GrDiffLine(GrDiffLineType.REMOVE);
    const l3 = new GrDiffLine(GrDiffLineType.BOTH);

    assert.throws(() =>
      new GrDiffGroup({type: GrDiffGroupType.BOTH, lines: [l1, l2, l3]}));
  });

  suite('hideInContextControl', () => {
    let groups;
    setup(() => {
      groups = [
        new GrDiffGroup({type: GrDiffGroupType.BOTH, lines: [
          new GrDiffLine(GrDiffLineType.BOTH, 5, 7),
          new GrDiffLine(GrDiffLineType.BOTH, 6, 8),
          new GrDiffLine(GrDiffLineType.BOTH, 7, 9),
        ]}),
        new GrDiffGroup({type: GrDiffGroupType.DELTA, lines: [
          new GrDiffLine(GrDiffLineType.REMOVE, 8),
          new GrDiffLine(GrDiffLineType.ADD, 0, 10),
          new GrDiffLine(GrDiffLineType.REMOVE, 9),
          new GrDiffLine(GrDiffLineType.ADD, 0, 11),
          new GrDiffLine(GrDiffLineType.REMOVE, 10),
          new GrDiffLine(GrDiffLineType.ADD, 0, 12),
          new GrDiffLine(GrDiffLineType.REMOVE, 11),
          new GrDiffLine(GrDiffLineType.ADD, 0, 13),
        ]}),
        new GrDiffGroup({type: GrDiffGroupType.BOTH, lines: [
          new GrDiffLine(GrDiffLineType.BOTH, 12, 14),
          new GrDiffLine(GrDiffLineType.BOTH, 13, 15),
          new GrDiffLine(GrDiffLineType.BOTH, 14, 16),
        ]}),
      ];
    });

    test('hides hidden groups in context control', () => {
      const collapsedGroups = hideInContextControl(groups, 3, 7);
      assert.equal(collapsedGroups.length, 3);

      assert.equal(collapsedGroups[0], groups[0]);

      assert.equal(collapsedGroups[1].type, GrDiffGroupType.CONTEXT_CONTROL);
      assert.equal(collapsedGroups[1].contextGroups.length, 1);
      assert.equal(collapsedGroups[1].contextGroups[0], groups[1]);

      assert.equal(collapsedGroups[2], groups[2]);
    });

    test('splits partially hidden groups', () => {
      const collapsedGroups = hideInContextControl(groups, 4, 8);
      assert.equal(collapsedGroups.length, 4);
      assert.equal(collapsedGroups[0], groups[0]);

      assert.equal(collapsedGroups[1].type, GrDiffGroupType.DELTA);
      assert.deepEqual(collapsedGroups[1].adds, [groups[1].adds[0]]);
      assert.deepEqual(collapsedGroups[1].removes, [groups[1].removes[0]]);

      assert.equal(collapsedGroups[2].type, GrDiffGroupType.CONTEXT_CONTROL);
      assert.equal(collapsedGroups[2].contextGroups.length, 2);

      assert.equal(
          collapsedGroups[2].contextGroups[0].type,
          GrDiffGroupType.DELTA);
      assert.deepEqual(
          collapsedGroups[2].contextGroups[0].adds,
          groups[1].adds.slice(1));
      assert.deepEqual(
          collapsedGroups[2].contextGroups[0].removes,
          groups[1].removes.slice(1));

      assert.equal(
          collapsedGroups[2].contextGroups[1].type,
          GrDiffGroupType.BOTH);
      assert.deepEqual(
          collapsedGroups[2].contextGroups[1].lines,
          [groups[2].lines[0]]);

      assert.equal(collapsedGroups[3].type, GrDiffGroupType.BOTH);
      assert.deepEqual(collapsedGroups[3].lines, groups[2].lines.slice(1));
    });

    suite('with skip chunks', () => {
      setup(() => {
        const skipGroup = new GrDiffGroup({
          type: GrDiffGroupType.BOTH,
          skip: 60,
          offsetLeft: 8,
          offsetRight: 10});
        groups = [
          new GrDiffGroup({type: GrDiffGroupType.BOTH, lines: [
            new GrDiffLine(GrDiffLineType.BOTH, 5, 7),
            new GrDiffLine(GrDiffLineType.BOTH, 6, 8),
            new GrDiffLine(GrDiffLineType.BOTH, 7, 9),
          ]}),
          skipGroup,
          new GrDiffGroup({type: GrDiffGroupType.BOTH, lines: [
            new GrDiffLine(GrDiffLineType.BOTH, 68, 70),
            new GrDiffLine(GrDiffLineType.BOTH, 69, 71),
            new GrDiffLine(GrDiffLineType.BOTH, 70, 72),
          ]}),
        ];
      });

      test('refuses to split skip group when closer to before', () => {
        const collapsedGroups = hideInContextControl(groups, 4, 10);
        assert.deepEqual(groups, collapsedGroups);
      });
    });

    test('groups unchanged if the hidden range is empty', () => {
      assert.deepEqual(
          hideInContextControl(groups, 0, 0), groups);
    });

    test('groups unchanged if there is only 1 line to hide', () => {
      assert.deepEqual(
          hideInContextControl(groups, 3, 4), groups);
    });
  });

  suite('isTotal', () => {
    test('is total for add', () => {
      const lines = [];
      for (let idx = 0; idx < 10; idx++) {
        lines.push(new GrDiffLine(GrDiffLineType.ADD));
      }
      const group = new GrDiffGroup({type: GrDiffGroupType.DELTA, lines});
      assert.isTrue(group.isTotal(group));
    });

    test('is total for remove', () => {
      const lines = [];
      for (let idx = 0; idx < 10; idx++) {
        lines.push(new GrDiffLine(GrDiffLineType.REMOVE));
      }
      const group = new GrDiffGroup({type: GrDiffGroupType.DELTA, lines});
      assert.isTrue(group.isTotal(group));
    });

    test('not total for empty', () => {
      const group = new GrDiffGroup({type: GrDiffGroupType.BOTH});
      assert.isFalse(group.isTotal(group));
    });

    test('not total for non-delta', () => {
      const lines = [];
      for (let idx = 0; idx < 10; idx++) {
        lines.push(new GrDiffLine(GrDiffLineType.BOTH));
      }
      const group = new GrDiffGroup({type: GrDiffGroupType.DELTA, lines});
      assert.isFalse(group.isTotal(group));
    });
  });
});

