/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import './gr-diff-processor';
import {GrDiffLine} from '../gr-diff/gr-diff-line';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {GrDiffProcessor, ProcessingOptions, State} from './gr-diff-processor';
import {DiffContent} from '../../../types/diff';
import {assert} from '@open-wc/testing';
import {FILE, GrDiffLineType} from '../../../api/diff';
import {FULL_CONTEXT} from '../gr-diff/gr-diff-utils';

suite('gr-diff-processor tests', () => {
  const loremIpsum =
    'Lorem ipsum dolor sit amet, ei nonumes vituperata ius. ' +
    'Duo  animal omnesque fabellas et. Id has phaedrum dignissim ' +
    'deterruisset, pro ei petentium comprehensam, ut vis solum dicta. ' +
    'Eos cu aliquam labores qualisque, usu postea inermis te, et solum ' +
    'fugit assum per.';

  let processor: GrDiffProcessor;
  let options: ProcessingOptions = {
    context: 4,
  };

  setup(() => {});

  suite('not logged in', () => {
    setup(() => {
      options = {context: 4};
      processor = new GrDiffProcessor(options);
    });

    test('process loaded content', async () => {
      const content: DiffContent[] = [
        {
          ab: ['<!DOCTYPE html>', '<meta charset="utf-8">'],
        },
        {
          a: ['  Welcome ', '  to the wooorld of tomorrow!'],
          b: ['  Hello, world!'],
        },
        {
          ab: [
            'Leela: This is the only place the ship can’t hear us, so ',
            'everyone pretend to shower.',
            'Fry: Same as every day. Got it.',
          ],
        },
      ];

      const groups = await processor.process(content);
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

      function beforeNumberFn(l: GrDiffLine) {
        return l.beforeNumber;
      }
      function afterNumberFn(l: GrDiffLine) {
        return l.afterNumber;
      }
      function textFn(l: GrDiffLine) {
        return l.text;
      }

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
      assert.deepEqual(group.adds.map(textFn), ['  Hello, world!']);

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

    test('first group is for file', async () => {
      const content = [{b: ['foo']}];

      const groups = await processor.process(content);
      groups.shift(); // remove portedThreadsWithoutRangeGroup

      assert.equal(groups[0].type, GrDiffGroupType.BOTH);
      assert.equal(groups[0].lines.length, 1);
      assert.equal(groups[0].lines[0].text, '');
      assert.equal(groups[0].lines[0].beforeNumber, FILE);
      assert.equal(groups[0].lines[0].afterNumber, FILE);
    });

    suite('context groups', async () => {
      test('at the beginning, larger than context', async () => {
        options.context = 10;
        processor = new GrDiffProcessor(options);
        const content = [
          {
            ab: Array.from<string>({length: 100}).fill(
              'all work and no play make jack a dull boy'
            ),
          },
          {a: ['all work and no play make andybons a dull boy']},
        ];

        const groups = await processor.process(content);
        // group[0] is the LOST group
        // group[1] is the FILE group

        assert.equal(groups[2].type, GrDiffGroupType.CONTEXT_CONTROL);
        assert.instanceOf(groups[2].contextGroups[0], GrDiffGroup);
        assert.equal(groups[2].contextGroups[0].lines.length, 90);
        for (const l of groups[2].contextGroups[0].lines) {
          assert.equal(l.text, 'all work and no play make jack a dull boy');
        }

        assert.equal(groups[3].type, GrDiffGroupType.BOTH);
        assert.equal(groups[3].lines.length, 10);
        for (const l of groups[3].lines) {
          assert.equal(l.text, 'all work and no play make jack a dull boy');
        }
      });

      test('at the beginning with skip chunks', async () => {
        options.context = 10;
        processor = new GrDiffProcessor(options);
        const content = [
          {
            ab: Array.from<string>({length: 20}).fill(
              'all work and no play make jack a dull boy'
            ),
          },
          {skip: 43900},
          {ab: Array.from<string>({length: 30}).fill('some other content')},
          {a: ['some other content']},
        ];

        const groups = await processor.process(content);
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

      test('at the beginning, smaller than context', async () => {
        options.context = 10;
        processor = new GrDiffProcessor(options);
        const content = [
          {
            ab: Array.from<string>({length: 5}).fill(
              'all work and no play make jack a dull boy'
            ),
          },
          {a: ['all work and no play make andybons a dull boy']},
        ];

        const groups = await processor.process(content);
        groups.shift(); // remove portedThreadsWithoutRangeGroup

        // group[0] is the file group

        assert.equal(groups[1].type, GrDiffGroupType.BOTH);
        assert.equal(groups[1].lines.length, 5);
        for (const l of groups[1].lines) {
          assert.equal(l.text, 'all work and no play make jack a dull boy');
        }
      });

      test('at the end, larger than context', async () => {
        options.context = 10;
        processor = new GrDiffProcessor(options);
        const content = [
          {a: ['all work and no play make andybons a dull boy']},
          {
            ab: Array.from<string>({length: 100}).fill(
              'all work and no play make jill a dull girl'
            ),
          },
        ];

        const groups = await processor.process(content);
        groups.shift(); // remove portedThreadsWithoutRangeGroup

        // group[0] is the file group
        // group[1] is the "a" group

        assert.equal(groups[2].type, GrDiffGroupType.BOTH);
        assert.equal(groups[2].lines.length, 10);
        for (const l of groups[2].lines) {
          assert.equal(l.text, 'all work and no play make jill a dull girl');
        }

        assert.equal(groups[3].type, GrDiffGroupType.CONTEXT_CONTROL);
        assert.instanceOf(groups[3].contextGroups[0], GrDiffGroup);
        assert.equal(groups[3].contextGroups[0].lines.length, 90);
        for (const l of groups[3].contextGroups[0].lines) {
          assert.equal(l.text, 'all work and no play make jill a dull girl');
        }
      });

      test('at the end, smaller than context', async () => {
        options.context = 10;
        const content = [
          {a: ['all work and no play make andybons a dull boy']},
          {
            ab: Array.from<string>({length: 5}).fill(
              'all work and no play make jill a dull girl'
            ),
          },
        ];

        const groups = await processor.process(content);
        groups.shift(); // remove portedThreadsWithoutRangeGroup

        // group[0] is the file group
        // group[1] is the "a" group

        assert.equal(groups[2].type, GrDiffGroupType.BOTH);
        assert.equal(groups[2].lines.length, 5);
        for (const l of groups[2].lines) {
          assert.equal(l.text, 'all work and no play make jill a dull girl');
        }
      });

      test('for interleaved ab and common: true chunks', async () => {
        options.context = 10;
        processor = new GrDiffProcessor(options);
        const content = [
          {a: ['all work and no play make andybons a dull boy']},
          {
            ab: Array.from<string>({length: 3}).fill(
              'all work and no play make jill a dull girl'
            ),
          },
          {
            a: Array.from<string>({length: 3}).fill(
              'all work and no play make jill a dull girl'
            ),
            b: Array.from<string>({length: 3}).fill(
              '  all work and no play make jill a dull girl'
            ),
            common: true,
          },
          {
            ab: Array.from<string>({length: 3}).fill(
              'all work and no play make jill a dull girl'
            ),
          },
          {
            a: Array.from<string>({length: 3}).fill(
              'all work and no play make jill a dull girl'
            ),
            b: Array.from<string>({length: 3}).fill(
              '  all work and no play make jill a dull girl'
            ),
            common: true,
          },
          {
            ab: Array.from<string>({length: 3}).fill(
              'all work and no play make jill a dull girl'
            ),
          },
        ];

        const groups = await processor.process(content);
        groups.shift(); // remove portedThreadsWithoutRangeGroup

        // group[0] is the file group
        // group[1] is the "a" group

        // The first three interleaved chunks are completely shown because
        // they are part of the context (3 * 3 <= 10)

        assert.equal(groups[2].type, GrDiffGroupType.BOTH);
        assert.equal(groups[2].lines.length, 3);
        for (const l of groups[2].lines) {
          assert.equal(l.text, 'all work and no play make jill a dull girl');
        }

        assert.equal(groups[3].type, GrDiffGroupType.DELTA);
        assert.equal(groups[3].lines.length, 6);
        assert.equal(groups[3].adds.length, 3);
        assert.equal(groups[3].removes.length, 3);
        for (const l of groups[3].removes) {
          assert.equal(l.text, 'all work and no play make jill a dull girl');
        }
        for (const l of groups[3].adds) {
          assert.equal(l.text, '  all work and no play make jill a dull girl');
        }

        assert.equal(groups[4].type, GrDiffGroupType.BOTH);
        assert.equal(groups[4].lines.length, 3);
        for (const l of groups[4].lines) {
          assert.equal(l.text, 'all work and no play make jill a dull girl');
        }

        // The next chunk is partially shown, so it results in two groups

        assert.equal(groups[5].type, GrDiffGroupType.DELTA);
        assert.equal(groups[5].lines.length, 2);
        assert.equal(groups[5].adds.length, 1);
        assert.equal(groups[5].removes.length, 1);
        for (const l of groups[5].removes) {
          assert.equal(l.text, 'all work and no play make jill a dull girl');
        }
        for (const l of groups[5].adds) {
          assert.equal(l.text, '  all work and no play make jill a dull girl');
        }

        assert.equal(groups[6].type, GrDiffGroupType.CONTEXT_CONTROL);
        assert.equal(groups[6].contextGroups.length, 2);

        assert.equal(groups[6].contextGroups[0].lines.length, 4);
        assert.equal(groups[6].contextGroups[0].removes.length, 2);
        assert.equal(groups[6].contextGroups[0].adds.length, 2);
        for (const l of groups[6].contextGroups[0].removes) {
          assert.equal(l.text, 'all work and no play make jill a dull girl');
        }
        for (const l of groups[6].contextGroups[0].adds) {
          assert.equal(l.text, '  all work and no play make jill a dull girl');
        }

        // The final chunk is completely hidden
        assert.equal(groups[6].contextGroups[1].type, GrDiffGroupType.BOTH);
        assert.equal(groups[6].contextGroups[1].lines.length, 3);
        for (const l of groups[6].contextGroups[1].lines) {
          assert.equal(l.text, 'all work and no play make jill a dull girl');
        }
      });

      test('in the middle, larger than context', async () => {
        options.context = 10;
        processor = new GrDiffProcessor(options);
        const content = [
          {a: ['all work and no play make andybons a dull boy']},
          {
            ab: Array.from<string>({length: 100}).fill(
              'all work and no play make jill a dull girl'
            ),
          },
          {a: ['all work and no play make andybons a dull boy']},
        ];

        const groups = await processor.process(content);
        groups.shift(); // remove portedThreadsWithoutRangeGroup

        // group[0] is the file group
        // group[1] is the "a" group

        assert.equal(groups[2].type, GrDiffGroupType.BOTH);
        assert.equal(groups[2].lines.length, 10);
        for (const l of groups[2].lines) {
          assert.equal(l.text, 'all work and no play make jill a dull girl');
        }

        assert.equal(groups[3].type, GrDiffGroupType.CONTEXT_CONTROL);
        assert.instanceOf(groups[3].contextGroups[0], GrDiffGroup);
        assert.equal(groups[3].contextGroups[0].lines.length, 80);
        for (const l of groups[3].contextGroups[0].lines) {
          assert.equal(l.text, 'all work and no play make jill a dull girl');
        }

        assert.equal(groups[4].type, GrDiffGroupType.BOTH);
        assert.equal(groups[4].lines.length, 10);
        for (const l of groups[4].lines) {
          assert.equal(l.text, 'all work and no play make jill a dull girl');
        }
      });

      test('in the middle, smaller than context', async () => {
        options.context = 10;
        processor = new GrDiffProcessor(options);
        const content = [
          {a: ['all work and no play make andybons a dull boy']},
          {
            ab: Array.from<string>({length: 5}).fill(
              'all work and no play make jill a dull girl'
            ),
          },
          {a: ['all work and no play make andybons a dull boy']},
        ];

        const groups = await processor.process(content);
        groups.shift(); // remove portedThreadsWithoutRangeGroup

        // group[0] is the file group
        // group[1] is the "a" group

        assert.equal(groups[2].type, GrDiffGroupType.BOTH);
        assert.equal(groups[2].lines.length, 5);
        for (const l of groups[2].lines) {
          assert.equal(l.text, 'all work and no play make jill a dull girl');
        }
      });
    });

    test('in the middle with skip chunks', async () => {
      options.context = 10;
      processor = new GrDiffProcessor(options);
      const content = [
        {a: ['all work and no play make andybons a dull boy']},
        {
          ab: Array.from<string>({length: 20}).fill(
            'all work and no play make jill a dull girl'
          ),
        },
        {skip: 60},
        {
          ab: Array.from<string>({length: 20}).fill(
            'all work and no play make jill a dull girl'
          ),
        },
        {a: ['all work and no play make andybons a dull boy']},
      ];

      const groups = await processor.process(content);
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
        assert.equal(l.text, 'all work and no play make jill a dull girl');
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
        assert.equal(l.text, 'all work and no play make jill a dull girl');
      }
      // group[4] is the displayed part of the second ab
    });

    test('works with skip === 0', async () => {
      options.context = 3;
      processor = new GrDiffProcessor(options);
      const content = [
        {
          skip: 0,
        },
        {
          b: [
            '/**',
            ' * @license',
            ' * Copyright 2015 Google LLC',
            ' * SPDX-License-Identifier: Apache-2.0',
            ' */',
            "import '../../../test/common-test-setup';",
          ],
        },
      ];
      await processor.process(content);
    });

    test('break up common diff chunks', () => {
      options.keyLocations = {
        left: {1: true},
        right: {10: true},
      };
      processor = new GrDiffProcessor(options);

      const content = [
        {
          ab: [
            'copy',
            '',
            'asdf',
            'qwer',
            'zxcv',
            '',
            'http',
            '',
            'vbnm',
            'dfgh',
            'yuio',
            'sdfg',
            '1234',
          ],
        },
      ];
      const result = processor.splitCommonChunksWithKeyLocations(content);
      assert.deepEqual(result, [
        {
          ab: ['copy'],
          keyLocation: true,
        },
        {
          ab: ['', 'asdf', 'qwer', 'zxcv', '', 'http', '', 'vbnm'],
          keyLocation: false,
        },
        {
          ab: ['dfgh'],
          keyLocation: true,
        },
        {
          ab: ['yuio', 'sdfg', '1234'],
          keyLocation: false,
        },
      ]);
    });

    test('does not break-down common chunks w/ context', () => {
      const ab = Array(75)
        .fill(0)
        .map(() => `${Math.random()}`);
      const content = [{ab}];
      processor.context = 4;
      const result = processor.splitCommonChunksWithKeyLocations(content);
      assert.equal(result.length, 1);
      assert.deepEqual(result[0].ab, content[0].ab);
      assert.isFalse(result[0].keyLocation);
    });

    test('intraline normalization', () => {
      // The content and highlights are in the format returned by the Gerrit
      // REST API.
      let content = [
        '      <section class="summary">',
        '        <gr-formatted-text content="' +
          '[[_computeCurrentRevisionMessage(change)]]"></gr-formatted-text>',
        '      </section>',
      ];
      let highlights = [
        [31, 34],
        [42, 26],
      ];

      let results = processor.convertIntralineInfos(content, highlights);
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
          endIndex: 101,
          startIndex: 75,
        },
      ]);
      const lines = processor.linesFromRows(
        GrDiffLineType.BOTH,
        content,
        0,
        highlights
      );
      assert.equal(lines.length, 3);
      assert.isTrue(lines[0].hasIntralineInfo);
      assert.equal(lines[0].highlights.length, 1);
      assert.isTrue(lines[1].hasIntralineInfo);
      assert.equal(lines[1].highlights.length, 2);
      assert.isTrue(lines[2].hasIntralineInfo);
      assert.equal(lines[2].highlights.length, 0);

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
      results = processor.convertIntralineInfos(content, highlights);
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

      content = ['🙈 a', '🙉 b', '🙊 c'];
      highlights = [[2, 7]];
      results = processor.convertIntralineInfos(content, highlights);
      assert.deepEqual(results, [
        {
          contentIndex: 0,
          startIndex: 2,
        },
        {
          contentIndex: 1,
          startIndex: 0,
        },
        {
          contentIndex: 2,
          startIndex: 0,
          endIndex: 1,
        },
      ]);
    });

    test('image diffs', async () => {
      const content = Array(200).fill({ab: ['', '']});
      options.isBinary = true;
      processor = new GrDiffProcessor(options);
      const groups = await processor.process(content);
      assert.equal(groups.length, 2);

      // Image diffs don't process content, just the 'FILE' line.
      assert.equal(groups[0].lines.length, 1);
    });

    suite('processNext', () => {
      let rows: string[];

      setup(() => {
        rows = loremIpsum.split(' ');
      });

      test('FULL_CONTEXT', () => {
        processor.context = FULL_CONTEXT;
        const state: State = {
          lineNums: {left: 10, right: 100},
          chunkIndex: 1,
        };
        const chunks = [{a: ['foo']}, {ab: rows}, {a: ['bar']}];
        const result = processor.processNext(state, chunks);

        // Results in one, uncollapsed group with all rows.
        assert.equal(result.groups.length, 1);
        assert.equal(result.groups[0].type, GrDiffGroupType.BOTH);
        assert.equal(result.groups[0].lines.length, rows.length);

        // Line numbers are set correctly.
        assert.equal(
          result.groups[0].lines[0].beforeNumber,
          state.lineNums.left + 1
        );
        assert.equal(
          result.groups[0].lines[0].afterNumber,
          state.lineNums.right + 1
        );

        assert.equal(
          result.groups[0].lines[rows.length - 1].beforeNumber,
          state.lineNums.left + rows.length
        );
        assert.equal(
          result.groups[0].lines[rows.length - 1].afterNumber,
          state.lineNums.right + rows.length
        );
      });

      test('FULL_CONTEXT with skip chunks still get collapsed', () => {
        processor.context = FULL_CONTEXT;
        const lineNums = {left: 10, right: 100};
        const state = {
          lineNums,
          chunkIndex: 1,
        };
        const skip = 10000;
        const chunks = [{a: ['foo']}, {skip}, {ab: rows}, {a: ['bar']}];
        const result = processor.processNext(state, chunks);
        // Results in one, uncollapsed group with all rows.
        assert.equal(result.groups.length, 1);
        assert.equal(result.groups[0].type, GrDiffGroupType.CONTEXT_CONTROL);

        // Skip and ab group are hidden in the same context control
        assert.equal(result.groups[0].contextGroups.length, 2);
        const [skippedGroup, abGroup] = result.groups[0].contextGroups;

        // Line numbers are set correctly.
        assert.deepEqual(skippedGroup.lineRange, {
          left: {
            start_line: lineNums.left + 1,
            end_line: lineNums.left + skip,
          },
          right: {
            start_line: lineNums.right + 1,
            end_line: lineNums.right + skip,
          },
        });

        assert.deepEqual(abGroup.lineRange, {
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
        processor.context = 10;
        const state = {
          lineNums: {left: 10, right: 100},
          chunkIndex: 1,
        };
        const chunks = [{a: ['foo']}, {ab: rows}, {a: ['bar']}];
        const result = processor.processNext(state, chunks);
        const expectedCollapseSize = rows.length - 2 * processor.context;

        assert.equal(result.groups.length, 3, 'Results in three groups');

        // The first and last are uncollapsed context, whereas the middle has
        // a single context-control line.
        assert.equal(result.groups[0].lines.length, processor.context);
        assert.equal(result.groups[2].lines.length, processor.context);

        // The collapsed group has the hidden lines as its context group.
        assert.equal(
          result.groups[1].contextGroups[0].lines.length,
          expectedCollapseSize
        );
      });

      test('first', () => {
        processor.context = 10;
        const state = {
          lineNums: {left: 10, right: 100},
          chunkIndex: 0,
        };
        const chunks = [{ab: rows}, {a: ['foo']}, {a: ['bar']}];
        const result = processor.processNext(state, chunks);
        const expectedCollapseSize = rows.length - processor.context;

        assert.equal(result.groups.length, 2, 'Results in two groups');

        // Only the first group is collapsed.
        assert.equal(result.groups[1].lines.length, processor.context);

        // The collapsed group has the hidden lines as its context group.
        assert.equal(
          result.groups[0].contextGroups[0].lines.length,
          expectedCollapseSize
        );
      });

      test('few-rows', () => {
        // Only ten rows.
        rows = rows.slice(0, 10);
        processor.context = 10;
        const state = {
          lineNums: {left: 10, right: 100},
          chunkIndex: 0,
        };
        const chunks = [{ab: rows}, {a: ['foo']}, {a: ['bar']}];
        const result = processor.processNext(state, chunks);

        // Results in one uncollapsed group with all rows.
        assert.equal(result.groups.length, 1, 'Results in one group');
        assert.equal(result.groups[0].lines.length, rows.length);
      });

      test('no single line collapse', () => {
        rows = rows.slice(0, 7);
        processor.context = 3;
        const state = {
          lineNums: {left: 10, right: 100},
          chunkIndex: 1,
        };
        const chunks = [{a: ['foo']}, {ab: rows}, {a: ['bar']}];
        const result = processor.processNext(state, chunks);

        // Results in one uncollapsed group with all rows.
        assert.equal(result.groups.length, 1, 'Results in one group');
        assert.equal(result.groups[0].lines.length, rows.length);
      });

      suite('with key location', () => {
        let state: State;
        let chunks: DiffContent[];

        setup(() => {
          state = {
            lineNums: {left: 10, right: 100},
            chunkIndex: 0,
          };
          processor.context = 10;
          chunks = [{ab: rows}, {ab: ['foo'], keyLocation: true}, {ab: rows}];
        });

        test('context before', () => {
          state.chunkIndex = 0;
          const result = processor.processNext(state, chunks);

          // The first chunk is split into two groups:
          // 1) A context-control, hiding everything but the context before
          //    the key location.
          // 2) The context before the key location.
          // The key location is not processed in this call to processNext
          assert.equal(result.groups.length, 2);
          // The collapsed group has the hidden lines as its context group.
          assert.equal(
            result.groups[0].contextGroups[0].lines.length,
            rows.length - processor.context
          );
          assert.equal(result.groups[1].lines.length, processor.context);
        });

        test('key location itself', () => {
          state.chunkIndex = 1;
          const result = processor.processNext(state, chunks);

          // The second chunk results in a single group, that is just the
          // line with the key location
          assert.equal(result.groups.length, 1);
          assert.equal(result.groups[0].lines.length, 1);
          assert.equal(result.lineDelta.left, 1);
          assert.equal(result.lineDelta.right, 1);
        });

        test('context after', () => {
          state.chunkIndex = 2;
          const result = processor.processNext(state, chunks);

          // The last chunk is split into two groups:
          // 1) The context after the key location.
          // 1) A context-control, hiding everything but the context after the
          //    key location.
          assert.equal(result.groups.length, 2);
          assert.equal(result.groups[0].lines.length, processor.context);
          // The collapsed group has the hidden lines as its context group.
          assert.equal(
            result.groups[1].contextGroups[0].lines.length,
            rows.length - processor.context
          );
        });
      });

      suite('with diffRangesToFocus', () => {
        let state: State;
        let chunks: DiffContent[];

        setup(() => {
          state = {
            lineNums: {left: 0, right: 0},
            chunkIndex: 0,
          };
          processor.context = 3;
          processor.diffRangesToFocus = {
            left: [{start: 6, end: 7}],
            right: [{start: 6, end: 6}],
          };
          chunks = [
            {
              ab: Array.from<string>({length: 5}).fill(
                'all work and no play make jill a dull boy'
              ),
            },
            {
              a: ['Old ', ' Change!'],
              b: ['New Change'],
            },
            {
              ab: Array.from<string>({length: 5}).fill(
                'all work and no play make jack a dull boy'
              ),
            },
            {
              a: ['Old ', ' Change!', '1'],
              b: ['New Change', '2'],
            },
          ];
        });

        test('focussed group is not collapsed in context control group', () => {
          const result = processor.processNext(state, chunks);

          // This should consider second delta group as focussed and not collapse it.
          // This result is first chunk itself.
          assert.equal(result.groups.length, 1);
          assert.equal(result.groups[0].type, GrDiffGroupType.BOTH);
          assert.equal(result.groups[0].lines.length, 5);
        });

        test('collapsing delta group at end in context control group', () => {
          state = {
            lineNums: {left: 7, right: 6},
            chunkIndex: 2,
          };
          const result = processor.processNext(state, [
            ...chunks,
            {
              ab: Array.from<string>({length: 5}).fill(
                'all work and no play make jack a dull boy'
              ),
            },
          ]);

          // The first chunk is split into two groups:
          // 1) A common group which is rendered before contextControl group
          // 2) Second group is a context control which contains split from 4th chunk
          // and the delta group and the last unchanged group.
          assert.equal(result.groups.length, 2);
          assert.equal(result.groups[0].type, GrDiffGroupType.BOTH);
          assert.equal(result.groups[0].lines.length, 3);
          assert.equal(result.groups[1].type, GrDiffGroupType.CONTEXT_CONTROL);
          assert.equal(result.groups[1].contextGroups.length, 3);
          assert.equal(result.groups[1].contextGroups[0].lines.length, 2);
          assert.equal(
            result.groups[1].contextGroups[1].type,
            GrDiffGroupType.DELTA
          );
          assert.equal(
            result.groups[1].contextGroups[2].type,
            GrDiffGroupType.BOTH
          );
        });

        test('collapsing delta group in middle in context control group', () => {
          state = {
            lineNums: {left: 7, right: 6},
            chunkIndex: 2,
          };
          const result = processor.processNext(state, chunks);

          // The first chunk is split into two groups:
          // 1) A common group which is rendered before contextControl group
          // 2) Second group is a context control which contains split from 4th chunk
          // and the delta group.
          assert.equal(result.groups.length, 2);
          assert.equal(result.groups[0].type, GrDiffGroupType.BOTH);
          assert.equal(result.groups[0].lines.length, 3);
          assert.equal(result.groups[1].type, GrDiffGroupType.CONTEXT_CONTROL);
          assert.equal(result.groups[1].contextGroups.length, 2);
          assert.equal(result.groups[1].contextGroups[0].lines.length, 2);
          assert.equal(
            result.groups[1].contextGroups[1].type,
            GrDiffGroupType.DELTA
          );
        });

        test('do not collapse if there are not enough context lines', () => {
          processor.context = 10;
          state = {
            lineNums: {left: 7, right: 6},
            chunkIndex: 2,
          };
          const result = processor.processNext(state, chunks);

          // The first chunk is split into two groups:
          // 1) A common group which is rendered before contextControl group
          // 2) Second group is a context control which contains split from 4th chunk
          // and the delta group.
          assert.equal(result.groups.length, 2);
          assert.equal(result.groups[0].type, GrDiffGroupType.BOTH);
          assert.equal(result.groups[0].lines.length, 5);
          assert.equal(result.groups[1].type, GrDiffGroupType.DELTA);
        });

        test('collapse chunks with key locations if out of focus range', () => {
          const keyLocationLineText = 'key location behind a context group';
          state = {
            lineNums: {left: 7, right: 6},
            chunkIndex: 4,
          };
          const result = processor.processNext(state, [
            ...chunks,
            {
              ab: Array.from<string>({length: 2}).fill(
                'all work and no play make jill a dull boy'
              ),
              keyLocation: false,
            },
            {
              ab: Array.from<string>({length: 5}).fill(keyLocationLineText),
              keyLocation: true,
            },
          ]);
          assert.equal(result.groups.length, 3);
          assert.equal(
            result.groups[2].contextGroups[0].lines[0].text,
            keyLocationLineText
          );
        });
      });
    });

    suite('gr-diff-processor helpers', () => {
      let rows: string[];

      setup(() => {
        rows = loremIpsum.split(' ');
      });

      test('linesFromRows', () => {
        const startLineNum = 10;
        let result = processor.linesFromRows(
          GrDiffLineType.ADD,
          rows,
          startLineNum + 1
        );

        assert.equal(result.length, rows.length);
        assert.equal(result[0].type, GrDiffLineType.ADD);
        assert.notOk(result[0].hasIntralineInfo);
        assert.equal(result[0].afterNumber, startLineNum + 1);
        assert.notOk(result[0].beforeNumber);
        assert.equal(
          result[result.length - 1].afterNumber,
          startLineNum + rows.length
        );
        assert.notOk(result[result.length - 1].beforeNumber);

        result = processor.linesFromRows(
          GrDiffLineType.REMOVE,
          rows,
          startLineNum + 1
        );

        assert.equal(result.length, rows.length);
        assert.equal(result[0].type, GrDiffLineType.REMOVE);
        assert.notOk(result[0].hasIntralineInfo);
        assert.equal(result[0].beforeNumber, startLineNum + 1);
        assert.notOk(result[0].afterNumber);
        assert.equal(
          result[result.length - 1].beforeNumber,
          startLineNum + rows.length
        );
        assert.notOk(result[result.length - 1].afterNumber);
      });
    });
  });
});
