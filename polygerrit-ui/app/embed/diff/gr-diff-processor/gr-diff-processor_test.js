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
import 'lodash/lodash.js';
import './gr-diff-processor.js';
import {GrDiffLineType, FILE} from '../gr-diff/gr-diff-line.js';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group.js';

const basicFixture = fixtureFromElement('gr-diff-processor');

suite('gr-diff-processor tests', () => {
  const WHOLE_FILE = -1;
  const loremIpsum =
      'Lorem ipsum dolor sit amet, ei nonumes vituperata ius. ' +
      'Duo  animal omnesque fabellas et. Id has phaedrum dignissim ' +
      'deterruisset, pro ei petentium comprehensam, ut vis solum dicta. ' +
      'Eos cu aliquam labores qualisque, usu postea inermis te, et solum ' +
      'fugit assum per.';

  let element;

  setup(() => {

  });

  suite('not logged in', () => {
    setup(() => {
      element = basicFixture.instantiate();

      element.context = 4;
    });

    test('process loaded content', () => {
      const content = [
        {
          ab: [
            '<!DOCTYPE html>',
            '<meta charset="utf-8">',
          ],
        },
        {
          a: [
            '  Welcome ',
            '  to the wooorld of tomorrow!',
          ],
          b: [
            '  Hello, world!',
          ],
        },
        {
          ab: [
            'Leela: This is the only place the ship can’t hear us, so ',
            'everyone pretend to shower.',
            'Fry: Same as every day. Got it.',
          ],
        },
      ];

      return element.process(content).then(() => {
        const groups = element.groups;
        groups.shift(); // remove portedThreadsWithoutRangeGroup
        assert.equal(groups.length, 4);

        let group = groups[0];
        assert.equal(group.type, GrDiffGroupType.BOTH);
        assert.equal(group.lines.length, 1);
        assert.equal(group.lines[0].text, '');
        assert.equal(group.lines[0].beforeNumber, FILE);
        assert.equal(group.lines[0].afterNumber, FILE);

        group = groups[1];
        assert.equal(group.type, GrDiffGroupType.BOTH);
        assert.equal(group.lines.length, 2);

        function beforeNumberFn(l) { return l.beforeNumber; }
        function afterNumberFn(l) { return l.afterNumber; }
        function textFn(l) { return l.text; }

        assert.deepEqual(group.lines.map(beforeNumberFn), [1, 2]);
        assert.deepEqual(group.lines.map(afterNumberFn), [1, 2]);
        assert.deepEqual(group.lines.map(textFn), [
          '<!DOCTYPE html>',
          '<meta charset="utf-8">',
        ]);

        group = groups[2];
        assert.equal(group.type, GrDiffGroupType.DELTA);
        assert.equal(group.lines.length, 3);
        assert.equal(group.adds.length, 1);
        assert.equal(group.removes.length, 2);
        assert.deepEqual(group.removes.map(beforeNumberFn), [3, 4]);
        assert.deepEqual(group.adds.map(afterNumberFn), [3]);
        assert.deepEqual(group.removes.map(textFn), [
          '  Welcome ',
          '  to the wooorld of tomorrow!',
        ]);
        assert.deepEqual(group.adds.map(textFn), [
          '  Hello, world!',
        ]);

        group = groups[3];
        assert.equal(group.type, GrDiffGroupType.BOTH);
        assert.equal(group.lines.length, 3);
        assert.deepEqual(group.lines.map(beforeNumberFn), [5, 6, 7]);
        assert.deepEqual(group.lines.map(afterNumberFn), [4, 5, 6]);
        assert.deepEqual(group.lines.map(textFn), [
          'Leela: This is the only place the ship can’t hear us, so ',
          'everyone pretend to shower.',
          'Fry: Same as every day. Got it.',
        ]);
      });
    });

    test('first group is for file', () => {
      const content = [
        {b: ['foo']},
      ];

      return element.process(content).then(() => {
        const groups = element.groups;
        groups.shift(); // remove portedThreadsWithoutRangeGroup

        assert.equal(groups[0].type, GrDiffGroupType.BOTH);
        assert.equal(groups[0].lines.length, 1);
        assert.equal(groups[0].lines[0].text, '');
        assert.equal(groups[0].lines[0].beforeNumber, FILE);
        assert.equal(groups[0].lines[0].afterNumber, FILE);
      });
    });

    suite('context groups', () => {
      test('at the beginning, larger than context', () => {
        element.context = 10;
        const content = [
          {ab: new Array(100)
              .fill('all work and no play make jack a dull boy')},
          {a: ['all work and no play make andybons a dull boy']},
        ];

        return element.process(content).then(() => {
          const groups = element.groups;
          groups.shift(); // remove portedThreadsWithoutRangeGroup

          // group[0] is the file group

          assert.equal(groups[1].type, GrDiffGroupType.CONTEXT_CONTROL);
          assert.instanceOf(groups[1].contextGroups[0], GrDiffGroup);
          assert.equal(groups[1].contextGroups[0].lines.length, 90);
          for (const l of groups[1].contextGroups[0].lines) {
            assert.equal(l.text, 'all work and no play make jack a dull boy');
          }

          assert.equal(groups[2].type, GrDiffGroupType.BOTH);
          assert.equal(groups[2].lines.length, 10);
          for (const l of groups[2].lines) {
            assert.equal(l.text, 'all work and no play make jack a dull boy');
          }
        });
      });

      test('at the beginning with skip chunks', async () => {
        element.context = 10;
        const content = [
          {ab: new Array(20)
              .fill('all work and no play make jack a dull boy')},
          {skip: 43900},
          {ab: new Array(30)
              .fill('some other content')},
          {a: ['some other content']},
        ];

        await element.process(content);

        const groups = element.groups;
        groups.shift(); // remove portedThreadsWithoutRangeGroup

        // group[0] is the file group

        const commonGroup = groups[1];

        // Hidden context before
        assert.equal(commonGroup.type, GrDiffGroupType.CONTEXT_CONTROL);
        assert.instanceOf(commonGroup.contextGroups[0], GrDiffGroup);
        assert.equal(commonGroup.contextGroups[0].lines.length, 20);
        for (const l of commonGroup.contextGroups[0].lines) {
          assert.equal(l.text, 'all work and no play make jack a dull boy');
        }

        // Skipped group
        const skipGroup = commonGroup.contextGroups[1];
        assert.equal(skipGroup.skip, 43900);
        const expectedRange = {
          left: {start_line: 21, end_line: 43920},
          right: {start_line: 21, end_line: 43920},
        };
        assert.deepEqual(skipGroup.lineRange, expectedRange);

        // Hidden context after
        assert.equal(commonGroup.contextGroups[2].lines.length, 20);
        for (const l of commonGroup.contextGroups[2].lines) {
          assert.equal(l.text, 'some other content');
        }

        // Displayed lines
        assert.equal(groups[2].type, GrDiffGroupType.BOTH);
        assert.equal(groups[2].lines.length, 10);
        for (const l of groups[2].lines) {
          assert.equal(l.text, 'some other content');
        }
      });

      test('at the beginning, smaller than context', () => {
        element.context = 10;
        const content = [
          {ab: new Array(5)
              .fill('all work and no play make jack a dull boy')},
          {a: ['all work and no play make andybons a dull boy']},
        ];

        return element.process(content).then(() => {
          const groups = element.groups;
          groups.shift(); // remove portedThreadsWithoutRangeGroup

          // group[0] is the file group

          assert.equal(groups[1].type, GrDiffGroupType.BOTH);
          assert.equal(groups[1].lines.length, 5);
          for (const l of groups[1].lines) {
            assert.equal(l.text, 'all work and no play make jack a dull boy');
          }
        });
      });

      test('at the end, larger than context', () => {
        element.context = 10;
        const content = [
          {a: ['all work and no play make andybons a dull boy']},
          {ab: new Array(100)
              .fill('all work and no play make jill a dull girl')},
        ];

        return element.process(content).then(() => {
          const groups = element.groups;
          groups.shift(); // remove portedThreadsWithoutRangeGroup

          // group[0] is the file group
          // group[1] is the "a" group

          assert.equal(groups[2].type, GrDiffGroupType.BOTH);
          assert.equal(groups[2].lines.length, 10);
          for (const l of groups[2].lines) {
            assert.equal(
                l.text, 'all work and no play make jill a dull girl');
          }

          assert.equal(groups[3].type, GrDiffGroupType.CONTEXT_CONTROL);
          assert.instanceOf(groups[3].contextGroups[0], GrDiffGroup);
          assert.equal(groups[3].contextGroups[0].lines.length, 90);
          for (const l of groups[3].contextGroups[0].lines) {
            assert.equal(
                l.text, 'all work and no play make jill a dull girl');
          }
        });
      });

      test('at the end, smaller than context', () => {
        element.context = 10;
        const content = [
          {a: ['all work and no play make andybons a dull boy']},
          {ab: new Array(5)
              .fill('all work and no play make jill a dull girl')},
        ];

        return element.process(content).then(() => {
          const groups = element.groups;
          groups.shift(); // remove portedThreadsWithoutRangeGroup

          // group[0] is the file group
          // group[1] is the "a" group

          assert.equal(groups[2].type, GrDiffGroupType.BOTH);
          assert.equal(groups[2].lines.length, 5);
          for (const l of groups[2].lines) {
            assert.equal(
                l.text, 'all work and no play make jill a dull girl');
          }
        });
      });

      test('for interleaved ab and common: true chunks', () => {
        element.context = 10;
        const content = [
          {a: ['all work and no play make andybons a dull boy']},
          {ab: new Array(3)
              .fill('all work and no play make jill a dull girl')},
          {
            a: new Array(3).fill(
                'all work and no play make jill a dull girl'),
            b: new Array(3).fill(
                '  all work and no play make jill a dull girl'),
            common: true,
          },
          {ab: new Array(3)
              .fill('all work and no play make jill a dull girl')},
          {
            a: new Array(3).fill(
                'all work and no play make jill a dull girl'),
            b: new Array(3).fill(
                '  all work and no play make jill a dull girl'),
            common: true,
          },
          {ab: new Array(3)
              .fill('all work and no play make jill a dull girl')},
        ];

        return element.process(content).then(() => {
          const groups = element.groups;
          groups.shift(); // remove portedThreadsWithoutRangeGroup

          // group[0] is the file group
          // group[1] is the "a" group

          // The first three interleaved chunks are completely shown because
          // they are part of the context (3 * 3 <= 10)

          assert.equal(groups[2].type, GrDiffGroupType.BOTH);
          assert.equal(groups[2].lines.length, 3);
          for (const l of groups[2].lines) {
            assert.equal(
                l.text, 'all work and no play make jill a dull girl');
          }

          assert.equal(groups[3].type, GrDiffGroupType.DELTA);
          assert.equal(groups[3].lines.length, 6);
          assert.equal(groups[3].adds.length, 3);
          assert.equal(groups[3].removes.length, 3);
          for (const l of groups[3].removes) {
            assert.equal(
                l.text, 'all work and no play make jill a dull girl');
          }
          for (const l of groups[3].adds) {
            assert.equal(
                l.text, '  all work and no play make jill a dull girl');
          }

          assert.equal(groups[4].type, GrDiffGroupType.BOTH);
          assert.equal(groups[4].lines.length, 3);
          for (const l of groups[4].lines) {
            assert.equal(
                l.text, 'all work and no play make jill a dull girl');
          }

          // The next chunk is partially shown, so it results in two groups

          assert.equal(groups[5].type, GrDiffGroupType.DELTA);
          assert.equal(groups[5].lines.length, 2);
          assert.equal(groups[5].adds.length, 1);
          assert.equal(groups[5].removes.length, 1);
          for (const l of groups[5].removes) {
            assert.equal(
                l.text, 'all work and no play make jill a dull girl');
          }
          for (const l of groups[5].adds) {
            assert.equal(
                l.text, '  all work and no play make jill a dull girl');
          }

          assert.equal(groups[6].type, GrDiffGroupType.CONTEXT_CONTROL);
          assert.equal(groups[6].contextGroups.length, 2);

          assert.equal(groups[6].contextGroups[0].lines.length, 4);
          assert.equal(groups[6].contextGroups[0].removes.length, 2);
          assert.equal(groups[6].contextGroups[0].adds.length, 2);
          for (const l of groups[6].contextGroups[0].removes) {
            assert.equal(
                l.text, 'all work and no play make jill a dull girl');
          }
          for (const l of groups[6].contextGroups[0].adds) {
            assert.equal(
                l.text, '  all work and no play make jill a dull girl');
          }

          // The final chunk is completely hidden
          assert.equal(
              groups[6].contextGroups[1].type,
              GrDiffGroupType.BOTH);
          assert.equal(groups[6].contextGroups[1].lines.length, 3);
          for (const l of groups[6].contextGroups[1].lines) {
            assert.equal(
                l.text, 'all work and no play make jill a dull girl');
          }
        });
      });

      test('in the middle, larger than context', () => {
        element.context = 10;
        const content = [
          {a: ['all work and no play make andybons a dull boy']},
          {ab: new Array(100)
              .fill('all work and no play make jill a dull girl')},
          {a: ['all work and no play make andybons a dull boy']},
        ];

        return element.process(content).then(() => {
          const groups = element.groups;
          groups.shift(); // remove portedThreadsWithoutRangeGroup

          // group[0] is the file group
          // group[1] is the "a" group

          assert.equal(groups[2].type, GrDiffGroupType.BOTH);
          assert.equal(groups[2].lines.length, 10);
          for (const l of groups[2].lines) {
            assert.equal(
                l.text, 'all work and no play make jill a dull girl');
          }

          assert.equal(groups[3].type, GrDiffGroupType.CONTEXT_CONTROL);
          assert.instanceOf(groups[3].contextGroups[0], GrDiffGroup);
          assert.equal(groups[3].contextGroups[0].lines.length, 80);
          for (const l of groups[3].contextGroups[0].lines) {
            assert.equal(
                l.text, 'all work and no play make jill a dull girl');
          }

          assert.equal(groups[4].type, GrDiffGroupType.BOTH);
          assert.equal(groups[4].lines.length, 10);
          for (const l of groups[4].lines) {
            assert.equal(
                l.text, 'all work and no play make jill a dull girl');
          }
        });
      });

      test('in the middle, smaller than context', () => {
        element.context = 10;
        const content = [
          {a: ['all work and no play make andybons a dull boy']},
          {ab: new Array(5)
              .fill('all work and no play make jill a dull girl')},
          {a: ['all work and no play make andybons a dull boy']},
        ];

        return element.process(content).then(() => {
          const groups = element.groups;
          groups.shift(); // remove portedThreadsWithoutRangeGroup

          // group[0] is the file group
          // group[1] is the "a" group

          assert.equal(groups[2].type, GrDiffGroupType.BOTH);
          assert.equal(groups[2].lines.length, 5);
          for (const l of groups[2].lines) {
            assert.equal(
                l.text, 'all work and no play make jill a dull girl');
          }
        });
      });
    });

    test('in the middle with skip chunks', async () => {
      element.context = 10;
      const content = [
        {a: ['all work and no play make andybons a dull boy']},
        {ab: new Array(20)
            .fill('all work and no play make jill a dull girl')},
        {skip: 60},
        {ab: new Array(20)
            .fill('all work and no play make jill a dull girl')},
        {a: ['all work and no play make andybons a dull boy']},
      ];

      await element.process(content);

      const groups = element.groups;
      groups.shift(); // remove portedThreadsWithoutRangeGroup

      // group[0] is the file group
      // group[1] is the chunk with a
      // group[2] is the displayed part of ab before

      const commonGroup = groups[3];

      // Hidden context before
      assert.equal(commonGroup.type, GrDiffGroupType.CONTEXT_CONTROL);
      assert.instanceOf(commonGroup.contextGroups[0], GrDiffGroup);
      assert.equal(commonGroup.contextGroups[0].lines.length, 10);
      for (const l of commonGroup.contextGroups[0].lines) {
        assert.equal(
            l.text, 'all work and no play make jill a dull girl');
      }

      // Skipped group
      const skipGroup = commonGroup.contextGroups[1];
      assert.equal(skipGroup.skip, 60);
      const expectedRange = {
        left: {start_line: 22, end_line: 81},
        right: {start_line: 21, end_line: 80},
      };
      assert.deepEqual(skipGroup.lineRange, expectedRange);

      // Hidden context after
      assert.equal(commonGroup.contextGroups[2].lines.length, 10);
      for (const l of commonGroup.contextGroups[2].lines) {
        assert.equal(
            l.text, 'all work and no play make jill a dull girl');
      }
      // group[4] is the displayed part of the second ab
    });

    test('break up common diff chunks', () => {
      element.keyLocations = {
        left: {1: true},
        right: {10: true},
      };

      const content = [
        {
          ab: [
            'Copyright (C) 2015 The Android Open Source Project',
            '',
            'Licensed under the Apache License, Version 2.0 (the "License");',
            'you may not use this file except in compliance with the ' +
                'License.',
            'You may obtain a copy of the License at',
            '',
            'http://www.apache.org/licenses/LICENSE-2.0',
            '',
            'Unless required by applicable law or agreed to in writing, ',
            'software distributed under the License is distributed on an ',
            '"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, ',
            'either express or implied. See the License for the specific ',
            'language governing permissions and limitations under the ' +
                'License.',
          ],
        },
      ];
      const result =
          element._splitCommonChunksWithKeyLocations(content);
      assert.deepEqual(result, [
        {
          ab: ['Copyright (C) 2015 The Android Open Source Project'],
          keyLocation: true,
        },
        {
          ab: [
            '',
            'Licensed under the Apache License, Version 2.0 (the "License");',
            'you may not use this file except in compliance with the ' +
                'License.',
            'You may obtain a copy of the License at',
            '',
            'http://www.apache.org/licenses/LICENSE-2.0',
            '',
            'Unless required by applicable law or agreed to in writing, ',
          ],
          keyLocation: false,
        },
        {
          ab: [
            'software distributed under the License is distributed on an '],
          keyLocation: true,
        },
        {
          ab: [
            '"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, ',
            'either express or implied. See the License for the specific ',
            'language governing permissions and limitations under the ' +
                'License.',
          ],
          keyLocation: false,
        },
      ]);
    });

    test('breaks down shared chunks w/ whole-file', () => {
      const maxGroupSize = 128;
      const size = maxGroupSize * 2 + 5;
      const content = [{
        ab: _.times(size, () => `${Math.random()}`),
      }];
      element.context = -1;
      const result = element._splitLargeChunks(content);
      assert.equal(result.length, 2);
      assert.deepEqual(result[0].ab, content[0].ab.slice(0, maxGroupSize));
      assert.deepEqual(result[1].ab, content[0].ab.slice(maxGroupSize));
    });

    test('breaks down added chunks', () => {
      const maxGroupSize = 128;
      const size = maxGroupSize * 2 + 5;
      const content = _.times(size, () => `${Math.random()}`);
      element.context = 5;
      const splitContent = element._splitLargeChunks([{a: [], b: content}])
          .map(r => r.b);
      assert.equal(splitContent.length, 3);
      assert.deepEqual(splitContent[0], content.slice(0, 5));
      assert.deepEqual(splitContent[1], content.slice(5, maxGroupSize + 5));
      assert.deepEqual(splitContent[2], content.slice(maxGroupSize + 5));
    });

    test('breaks down removed chunks', () => {
      const maxGroupSize = 128;
      const size = maxGroupSize * 2 + 5;
      const content = _.times(size, () => `${Math.random()}`);
      element.context = 5;
      const splitContent = element._splitLargeChunks([{a: content, b: []}])
          .map(r => r.a);
      assert.equal(splitContent.length, 3);
      assert.deepEqual(splitContent[0], content.slice(0, 5));
      assert.deepEqual(splitContent[1], content.slice(5, maxGroupSize + 5));
      assert.deepEqual(splitContent[2], content.slice(maxGroupSize + 5));
    });

    test('does not break down moved chunks', () => {
      const size = 120 * 2 + 5;
      const content = _.times(size, () => `${Math.random()}`);
      element.context = 5;
      const splitContent = element._splitLargeChunks([{
        a: content,
        b: [],
        move_details: {changed: false},
      }]).map(r => r.a);
      assert.equal(splitContent.length, 1);
      assert.deepEqual(splitContent[0], content);
    });

    test('does not break-down common chunks w/ context', () => {
      const content = [{
        ab: _.times(75, () => `${Math.random()}`),
      }];
      element.context = 4;
      const result =
          element._splitCommonChunksWithKeyLocations(content);
      assert.equal(result.length, 1);
      assert.deepEqual(result[0].ab, content[0].ab);
      assert.isFalse(result[0].keyLocation);
    });

    test('intraline normalization', () => {
      // The content and highlights are in the format returned by the Gerrit
      // REST API.
      let content = [
        '      <section class="summary">',
        '        <gr-linked-text content="' +
            '[[_computeCurrentRevisionMessage(change)]]"></gr-linked-text>',
        '      </section>',
      ];
      let highlights = [
        [31, 34], [42, 26],
      ];

      let results = element._convertIntralineInfos(content,
          highlights);
      assert.deepEqual(results, [
        {
          contentIndex: 0,
          startIndex: 31,
        },
        {
          contentIndex: 1,
          startIndex: 0,
          endIndex: 33,
        },
        {
          contentIndex: 1,
          startIndex: 75,
        },
        {
          contentIndex: 2,
          startIndex: 0,
          endIndex: 6,
        },
      ]);
      const lines = element._linesFromRows(
          GrDiffGroupType.BOTH, content, 0, highlights);
      assert.equal(lines.length, 3);
      assert.isTrue(lines[0].hasIntralineInfo);
      assert.equal(lines[0].highlights.length, 1);
      assert.isTrue(lines[1].hasIntralineInfo);
      assert.equal(lines[1].highlights.length, 2);
      assert.isTrue(lines[2].hasIntralineInfo);
      assert.equal(lines[2].highlights.length, 1);

      content = [
        '        this._path = value.path;',
        '',
        '        // When navigating away from the page, there is a ' +
          'possibility that the',
        '        // patch number is no longer a part of the URL ' +
          '(say when navigating to',
        '        // the top-level change info view) and therefore ' +
          'undefined in `params`.',
        '        if (!this._patchRange.patchNum) {',
      ];
      highlights = [
        [14, 17],
        [11, 70],
        [12, 67],
        [12, 67],
        [14, 29],
      ];
      results = element._convertIntralineInfos(content, highlights);
      assert.deepEqual(results, [
        {
          contentIndex: 0,
          startIndex: 14,
          endIndex: 31,
        },
        {
          contentIndex: 2,
          startIndex: 8,
          endIndex: 78,
        },
        {
          contentIndex: 3,
          startIndex: 11,
          endIndex: 78,
        },
        {
          contentIndex: 4,
          startIndex: 11,
          endIndex: 78,
        },
        {
          contentIndex: 5,
          startIndex: 12,
          endIndex: 41,
        },
      ]);
    });

    test('scrolling pauses rendering', () => {
      const contentRow = {
        ab: [
          '',
          '',
        ],
      };
      const content = _.times(200, _.constant(contentRow));
      element._isScrolling = true;
      element.process(content);
      // Just the files group - no more processing during scrolling.
      assert.equal(element.groups.length, 2);

      element._isScrolling = false;
      element.process(content);
      // More groups have been processed. How many does not matter here.
      assert.isAtLeast(element.groups.length, 3);
    });

    test('image diffs', () => {
      const contentRow = {
        ab: [
          '',
          '',
        ],
      };
      const content = _.times(200, _.constant(contentRow));
      element.process(content, true);
      assert.equal(element.groups.length, 2);

      // Image diffs don't process content, just the 'FILE' line.
      assert.equal(element.groups[0].lines.length, 1);
    });

    suite('_processNext', () => {
      let rows;

      setup(() => {
        rows = loremIpsum.split(' ');
      });

      test('WHOLE_FILE', () => {
        element.context = WHOLE_FILE;
        const state = {
          lineNums: {left: 10, right: 100},
          chunkIndex: 1,
        };
        const chunks = [
          {a: ['foo']},
          {ab: rows},
          {a: ['bar']},
        ];
        const result = element._processNext(state, chunks);

        // Results in one, uncollapsed group with all rows.
        assert.equal(result.groups.length, 1);
        assert.equal(result.groups[0].type, GrDiffGroupType.BOTH);
        assert.equal(result.groups[0].lines.length, rows.length);

        // Line numbers are set correctly.
        assert.equal(
            result.groups[0].lines[0].beforeNumber,
            state.lineNums.left + 1);
        assert.equal(
            result.groups[0].lines[0].afterNumber,
            state.lineNums.right + 1);

        assert.equal(result.groups[0].lines[rows.length - 1].beforeNumber,
            state.lineNums.left + rows.length);
        assert.equal(result.groups[0].lines[rows.length - 1].afterNumber,
            state.lineNums.right + rows.length);
      });

      test('WHOLE_FILE with skip chunks still get collapsed', () => {
        element.context = WHOLE_FILE;
        const lineNums = {left: 10, right: 100};
        const state = {
          lineNums,
          chunkIndex: 1,
        };
        const skip = 10000;
        const chunks = [
          {a: ['foo']},
          {skip},
          {ab: rows},
          {a: ['bar']},
        ];
        const result = element._processNext(state, chunks);
        // Results in one, uncollapsed group with all rows.
        assert.equal(result.groups.length, 1);
        assert.equal(result.groups[0].type, GrDiffGroupType.CONTEXT_CONTROL);

        // Skip and ab group are hidden in the same context control
        assert.equal(result.groups[0].contextGroups.length, 2);
        const [skippedGroup, abGroup] = result.groups[0].contextGroups;

        // Line numbers are set correctly.
        assert.deepEqual(
            skippedGroup.lineRange,
            {
              left: {
                start_line: lineNums.left + 1,
                end_line: lineNums.left + skip,
              },
              right: {
                start_line: lineNums.right + 1,
                end_line: lineNums.right + skip,
              },
            });

        assert.deepEqual(
            abGroup.lineRange,
            {
              left: {
                start_line: lineNums.left + skip + 1,
                end_line: lineNums.left + skip + rows.length,
              },
              right: {
                start_line: lineNums.right + skip + 1,
                end_line: lineNums.right + skip + rows.length,
              },
            });
      });

      test('with context', () => {
        element.context = 10;
        const state = {
          lineNums: {left: 10, right: 100},
          chunkIndex: 1,
        };
        const chunks = [
          {a: ['foo']},
          {ab: rows},
          {a: ['bar']},
        ];
        const result = element._processNext(state, chunks);
        const expectedCollapseSize = rows.length - 2 * element.context;

        assert.equal(result.groups.length, 3, 'Results in three groups');

        // The first and last are uncollapsed context, whereas the middle has
        // a single context-control line.
        assert.equal(result.groups[0].lines.length, element.context);
        assert.equal(result.groups[2].lines.length, element.context);

        // The collapsed group has the hidden lines as its context group.
        assert.equal(result.groups[1].contextGroups[0].lines.length,
            expectedCollapseSize);
      });

      test('first', () => {
        element.context = 10;
        const state = {
          lineNums: {left: 10, right: 100},
          chunkIndex: 0,
        };
        const chunks = [
          {ab: rows},
          {a: ['foo']},
          {a: ['bar']},
        ];
        const result = element._processNext(state, chunks);
        const expectedCollapseSize = rows.length - element.context;

        assert.equal(result.groups.length, 2, 'Results in two groups');

        // Only the first group is collapsed.
        assert.equal(result.groups[1].lines.length, element.context);

        // The collapsed group has the hidden lines as its context group.
        assert.equal(result.groups[0].contextGroups[0].lines.length,
            expectedCollapseSize);
      });

      test('few-rows', () => {
        // Only ten rows.
        rows = rows.slice(0, 10);
        element.context = 10;
        const state = {
          lineNums: {left: 10, right: 100},
          chunkIndex: 0,
        };
        const chunks = [
          {ab: rows},
          {a: ['foo']},
          {a: ['bar']},
        ];
        const result = element._processNext(state, chunks);

        // Results in one uncollapsed group with all rows.
        assert.equal(result.groups.length, 1, 'Results in one group');
        assert.equal(result.groups[0].lines.length, rows.length);
      });

      test('no single line collapse', () => {
        rows = rows.slice(0, 7);
        element.context = 3;
        const state = {
          lineNums: {left: 10, right: 100},
          chunkIndex: 1,
        };
        const chunks = [
          {a: ['foo']},
          {ab: rows},
          {a: ['bar']},
        ];
        const result = element._processNext(state, chunks);

        // Results in one uncollapsed group with all rows.
        assert.equal(result.groups.length, 1, 'Results in one group');
        assert.equal(result.groups[0].lines.length, rows.length);
      });

      suite('with key location', () => {
        let state;
        let chunks;

        setup(() => {
          state = {
            lineNums: {left: 10, right: 100},
          };
          element.context = 10;
          chunks = [
            {ab: rows},
            {ab: ['foo'], keyLocation: true},
            {ab: rows},
          ];
        });

        test('context before', () => {
          state.chunkIndex = 0;
          const result = element._processNext(state, chunks);

          // The first chunk is split into two groups:
          // 1) A context-control, hiding everything but the context before
          //    the key location.
          // 2) The context before the key location.
          // The key location is not processed in this call to _processNext
          assert.equal(result.groups.length, 2);
          // The collapsed group has the hidden lines as its context group.
          assert.equal(result.groups[0].contextGroups[0].lines.length,
              rows.length - element.context);
          assert.equal(result.groups[1].lines.length, element.context);
        });

        test('key location itself', () => {
          state.chunkIndex = 1;
          const result = element._processNext(state, chunks);

          // The second chunk results in a single group, that is just the
          // line with the key location
          assert.equal(result.groups.length, 1);
          assert.equal(result.groups[0].lines.length, 1);
          assert.equal(result.lineDelta.left, 1);
          assert.equal(result.lineDelta.right, 1);
        });

        test('context after', () => {
          state.chunkIndex = 2;
          const result = element._processNext(state, chunks);

          // The last chunk is split into two groups:
          // 1) The context after the key location.
          // 1) A context-control, hiding everything but the context after the
          //    key location.
          assert.equal(result.groups.length, 2);
          assert.equal(result.groups[0].lines.length, element.context);
          // The collapsed group has the hidden lines as its context group.
          assert.equal(result.groups[1].contextGroups[0].lines.length,
              rows.length - element.context);
        });
      });
    });

    suite('gr-diff-processor helpers', () => {
      let rows;

      setup(() => {
        rows = loremIpsum.split(' ');
      });

      test('_linesFromRows', () => {
        const startLineNum = 10;
        let result = element._linesFromRows(GrDiffLineType.ADD, rows,
            startLineNum + 1);

        assert.equal(result.length, rows.length);
        assert.equal(result[0].type, GrDiffLineType.ADD);
        assert.notOk(result[0].hasIntralineInfo);
        assert.equal(result[0].afterNumber, startLineNum + 1);
        assert.notOk(result[0].beforeNumber);
        assert.equal(result[result.length - 1].afterNumber,
            startLineNum + rows.length);
        assert.notOk(result[result.length - 1].beforeNumber);

        result = element._linesFromRows(GrDiffLineType.REMOVE, rows,
            startLineNum + 1);

        assert.equal(result.length, rows.length);
        assert.equal(result[0].type, GrDiffLineType.REMOVE);
        assert.notOk(result[0].hasIntralineInfo);
        assert.equal(result[0].beforeNumber, startLineNum + 1);
        assert.notOk(result[0].afterNumber);
        assert.equal(result[result.length - 1].beforeNumber,
            startLineNum + rows.length);
        assert.notOk(result[result.length - 1].afterNumber);
      });
    });

    suite('_breakdown*', () => {
      test('_breakdownChunk breaks down additions', () => {
        sinon.spy(element, '_breakdown');
        const chunk = {b: ['blah', 'blah', 'blah']};
        const result = element._breakdownChunk(chunk);
        assert.deepEqual(result, [chunk]);
        assert.isTrue(element._breakdown.called);
      });

      test('_breakdownChunk keeps due_to_rebase for broken down additions',
          () => {
            sinon.spy(element, '_breakdown');
            const chunk = {b: ['blah', 'blah', 'blah'], due_to_rebase: true};
            const result = element._breakdownChunk(chunk);
            for (const subResult of result) {
              assert.isTrue(subResult.due_to_rebase);
            }
          });

      test('_breakdown common case', () => {
        const array = 'Lorem ipsum dolor sit amet, suspendisse inceptos'
            .split(' ');
        const size = 3;

        const result = element._breakdown(array, size);

        for (const subResult of result) {
          assert.isAtMost(subResult.length, size);
        }
        const flattened = result
            .reduce((a, b) => a.concat(b), []);
        assert.deepEqual(flattened, array);
      });

      test('_breakdown smaller than size', () => {
        const array = 'Lorem ipsum dolor sit amet, suspendisse inceptos'
            .split(' ');
        const size = 10;
        const expected = [array];

        const result = element._breakdown(array, size);

        assert.deepEqual(result, expected);
      });

      test('_breakdown empty', () => {
        const array = [];
        const size = 10;
        const expected = [];

        const result = element._breakdown(array, size);

        assert.deepEqual(result, expected);
      });
    });
  });

  test('detaching cancels', () => {
    element = basicFixture.instantiate();
    sinon.stub(element, 'cancel');
    element.disconnectedCallback();
    assert(element.cancel.called);
  });
});

