/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {
  createConfig,
  createDiff,
  createEmptyDiff,
} from '../../../test/test-data-generators';
import './gr-diff';
import {getComputedStyleValue} from '../../../utils/dom-util';
import '@polymer/paper-button/paper-button';
import {
  DiffContent,
  DiffLayer,
  DiffPreferencesInfo,
  DiffViewMode,
  GrDiffLineType,
  IgnoreWhitespaceType,
  Side,
} from '../../../api/diff';
import {
  mouseDown,
  queryAll,
  stubBaseUrl,
  stubRestApi,
  waitQueryAndAssert,
  waitUntil,
} from '../../../test/test-utils';
import {AbortStop} from '../../../api/core';
import {GrDiff} from './gr-diff';
import {GrRangedCommentHint} from '../gr-ranged-comment-hint/gr-ranged-comment-hint';
import {fixture, html, assert} from '@open-wc/testing';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {
  GrAnnotationImpl,
  getStringLength,
} from '../gr-diff-highlight/gr-annotation';
import {GrDiffLine} from './gr-diff-line';

const DEFAULT_PREFS = createDefaultDiffPrefs();

suite('gr-diff a11y test', () => {
  test('audit', async () => {
    assert.isAccessible(await fixture(html`<gr-diff></gr-diff>`));
  });
});

suite('gr-diff tests', () => {
  let element: GrDiff;

  const MINIMAL_PREFS: DiffPreferencesInfo = {
    tab_size: 2,
    line_length: 80,
    font_size: 12,
    context: 3,
    ignore_whitespace: 'IGNORE_NONE',
  };

  setup(async () => {
    element = await fixture<GrDiff>(html`<gr-diff></gr-diff>`);
  });

  suite('selectionchange event handling', () => {
    let handleSelectionChangeStub: sinon.SinonSpy;

    const emulateSelection = function () {
      document.dispatchEvent(new CustomEvent('selectionchange'));
    };

    setup(async () => {
      handleSelectionChangeStub = sinon.spy(
        element.highlights,
        'handleSelectionChange'
      );
    });

    test('enabled if logged in', async () => {
      element.loggedIn = true;
      await element.updateComplete;
      emulateSelection();
      assert.isTrue(handleSelectionChangeStub.called);
    });

    test('ignored if logged out', async () => {
      element.loggedIn = false;
      await element.updateComplete;
      emulateSelection();
      assert.isFalse(handleSelectionChangeStub.called);
    });
  });

  test('line limit with line_wrapping', async () => {
    element.prefs = {...MINIMAL_PREFS, line_wrapping: true};
    await element.updateComplete;
    assert.equal(getComputedStyleValue('--line-limit-marker', element), '80ch');
  });

  test('line limit without line_wrapping', async () => {
    element.prefs = {...MINIMAL_PREFS, line_wrapping: false};
    await element.updateComplete;
    assert.equal(getComputedStyleValue('--line-limit-marker', element), '-1px');
  });

  suite('FULL_RESPONSIVE mode', () => {
    setup(async () => {
      element.prefs = {...MINIMAL_PREFS};
      element.renderPrefs = {responsive_mode: 'FULL_RESPONSIVE'};
      await element.updateComplete;
    });

    test('line limit is based on line_length', async () => {
      element.prefs = {...element.prefs!, line_length: 100};
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--line-limit-marker', element),
        '100ch'
      );
    });

    test('content-width should not be defined', () => {
      assert.equal(getComputedStyleValue('--content-width', element), 'none');
    });
  });

  suite('SHRINK_ONLY mode', () => {
    setup(async () => {
      element.prefs = {...MINIMAL_PREFS};
      element.renderPrefs = {responsive_mode: 'SHRINK_ONLY'};
      await element.updateComplete;
    });

    test('content-width should not be defined', () => {
      assert.equal(getComputedStyleValue('--content-width', element), 'none');
    });

    test('max-width considers two content columns in side-by-side', async () => {
      element.viewMode = DiffViewMode.SIDE_BY_SIDE;
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(2 * 80ch + 2 * 48px + 0ch + 1px + 2px)'
      );
    });

    test('max-width considers one content column in unified', async () => {
      element.viewMode = DiffViewMode.UNIFIED;
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(1 * 80ch + 2 * 48px + 0ch + 1px + 2px)'
      );
    });

    test('max-width considers font-size', async () => {
      element.prefs = {...element.prefs!, font_size: 13};
      await element.updateComplete;
      // Each line number column: 4 * 13 = 52px
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(2 * 80ch + 2 * 52px + 0ch + 1px + 2px)'
      );
    });

    test('sign cols are considered if show_sign_col is true', async () => {
      element.renderPrefs = {...element.renderPrefs, show_sign_col: true};
      await element.updateComplete;
      assert.equal(
        getComputedStyleValue('--diff-max-width', element),
        'calc(2 * 80ch + 2 * 48px + 2ch + 1px + 2px)'
      );
    });
  });

  suite('not logged in', () => {
    setup(async () => {
      element.loggedIn = false;
      await element.updateComplete;
    });

    test('toggleLeftDiff', () => {
      element.toggleLeftDiff();
      assert.isTrue(element.classList.contains('no-left'));
      element.toggleLeftDiff();
      assert.isFalse(element.classList.contains('no-left'));
    });

    suite('getCursorStops', () => {
      async function setupDiff() {
        element.diff = createDiff();
        element.prefs = {
          context: 10,
          tab_size: 8,
          font_size: 12,
          line_length: 100,
          cursor_blink_rate: 0,
          line_wrapping: false,

          show_line_endings: true,
          show_tabs: true,
          show_whitespace_errors: true,
          syntax_highlighting: true,
          ignore_whitespace: 'IGNORE_NONE',
        };
        await element.updateComplete;
      }

      test('returns [] when hidden and noAutoRender', async () => {
        element.noAutoRender = true;
        await setupDiff();
        element.loading = false;
        await element.updateComplete;
        element.hidden = true;
        await element.updateComplete;
        assert.equal(element.getCursorStops().length, 0);
      });

      test('returns one stop per line and one for the file row', async () => {
        await setupDiff();
        element.loading = false;
        await waitUntil(() => element.groups.length > 2);
        await element.updateComplete;
        const ROWS = 48;
        const FILE_ROW = 1;
        const LOST_ROW = 1;
        assert.equal(
          element.getCursorStops().length,
          ROWS + FILE_ROW + LOST_ROW
        );
      });

      test('returns an additional AbortStop when still loading', async () => {
        await setupDiff();
        element.loading = true;
        await waitUntil(() => element.groups.length > 2);
        await element.updateComplete;
        const ROWS = 48;
        const FILE_ROW = 1;
        const LOST_ROW = 1;
        element.loading = true;
        const actual = element.getCursorStops();
        assert.equal(actual.length, ROWS + FILE_ROW + LOST_ROW + 1);
        assert.isTrue(actual[actual.length - 1] instanceof AbortStop);
      });
    });
  });

  suite('logged in', async () => {
    setup(async () => {
      element.loggedIn = true;
      await element.updateComplete;
    });

    test('adds long range comment hint', async () => {
      const range = {
        start_line: 1,
        end_line: 12,
        start_character: 0,
        end_character: 0,
      };
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', '1');
      threadEl.setAttribute('range', JSON.stringify(range));
      threadEl.setAttribute('slot', 'right-1');
      const content = [
        {
          a: ['asdf'],
        },
        {
          ab: Array(13).fill('text'),
        },
      ];
      await setupSampleDiff({content});

      element.appendChild(threadEl);

      const hint = await waitQueryAndAssert<GrRangedCommentHint>(
        element,
        'gr-ranged-comment-hint'
      );
      assert.deepEqual(hint.range, range);
    });

    test('no duplicate range hint for same thread', async () => {
      const range = {
        start_line: 1,
        end_line: 12,
        start_character: 0,
        end_character: 0,
      };
      const threadEl = document.createElement('div');
      threadEl.className = 'comment-thread';
      threadEl.setAttribute('diff-side', 'right');
      threadEl.setAttribute('line-num', '1');
      threadEl.setAttribute('range', JSON.stringify(range));
      threadEl.setAttribute('slot', 'right-1');
      const firstHint = document.createElement('gr-ranged-comment-hint');
      firstHint.range = range;
      firstHint.setAttribute('slot', 'right-1');
      const content = [
        {
          a: ['asdf'],
        },
        {
          ab: Array(13).fill('text'),
        },
      ];
      await setupSampleDiff({content});

      element.appendChild(firstHint);
      element.appendChild(threadEl);

      assert.equal(
        element.querySelectorAll('gr-ranged-comment-hint').length,
        1
      );
    });
  });

  suite('blame', () => {
    test('unsetting', async () => {
      element.blame = [];
      const setBlameSpy = sinon.spy(element, 'setBlame');
      element.classList.add('showBlame');
      element.blame = null;
      await element.updateComplete;
      assert.isTrue(setBlameSpy.calledWithExactly([]));
      assert.isFalse(element.classList.contains('showBlame'));
    });

    test('setting', async () => {
      element.blame = [
        {
          author: 'test-author',
          time: 12345,
          commit_msg: '',
          id: 'commit id',
          ranges: [{start: 1, end: 2}],
        },
      ];
      await element.updateComplete;
      assert.isTrue(element.classList.contains('showBlame'));
    });
  });

  const setupSampleDiff = async function (params: {
    content: DiffContent[];
    ignore_whitespace?: IgnoreWhitespaceType;
    binary?: boolean;
  }) {
    const {ignore_whitespace, content} = params;
    // binary can't be undefined, use false if not set
    const binary = params.binary || false;
    element.prefs = {
      ignore_whitespace: ignore_whitespace || 'IGNORE_ALL',
      context: 10,
      cursor_blink_rate: 0,
      font_size: 12,

      line_length: 100,
      line_wrapping: false,
      show_line_endings: true,
      show_tabs: true,
      show_whitespace_errors: true,
      syntax_highlighting: true,
      tab_size: 8,
    };
    element.diff = {
      intraline_status: 'OK',
      change_type: 'MODIFIED',
      diff_header: [
        'diff --git a/carrot.js b/carrot.js',
        'index 2adc47d..f9c2f2c 100644',
        '--- a/carrot.js',
        '+++ b/carrot.jjs',
        'file differ',
      ],
      content,
      binary,
    };
    await waitUntil(() => element.groups.length > 1);
    await element.updateComplete;
  };

  suite('selection test', () => {
    test('user-select set correctly on side-by-side view', async () => {
      const content = [
        {
          a: ['all work and no play make andybons a dull boy'],
          b: ['elgoog elgoog elgoog'],
        },
        {
          ab: [
            'Non eram nescius, Brute, cum, quae summis ingeniis ',
            'exquisitaque doctrina philosophi Graeco sermone tractavissent',
          ],
        },
      ];
      await setupSampleDiff({content});

      // We are selecting "Non eram nescius..." on the left side.
      // The default is `selected-right`, so we will have to click.
      const diffLine = queryAll<HTMLElement>(element, '.contentText')[2];
      assert.equal(getComputedStyle(diffLine).userSelect, 'none');
      mouseDown(diffLine);
      assert.equal(getComputedStyle(diffLine).userSelect, 'text');
    });

    test('user-select set correctly on unified view', async () => {
      const content = [
        {
          a: ['all work and no play make andybons a dull boy'],
          b: ['elgoog elgoog elgoog'],
        },
        {
          ab: [
            'Non eram nescius, Brute, cum, quae summis ingeniis ',
            'exquisitaque doctrina philosophi Graeco sermone tractavissent',
          ],
        },
      ];
      element.viewMode = DiffViewMode.UNIFIED;
      await setupSampleDiff({content});

      // We are selecting "all work and no play..." on the left side.
      // The default is `selected-right`, so we will have to click.
      const diffLine = queryAll<HTMLElement>(element, '.contentText')[0];
      assert.equal(getComputedStyle(diffLine).userSelect, 'none');
      mouseDown(diffLine);
      assert.equal(getComputedStyle(diffLine).userSelect, 'text');
    });
  });
});

suite('former gr-diff-builder tests', () => {
  let element: GrDiff;

  const line = (text: string) => {
    const line = new GrDiffLine(GrDiffLineType.BOTH);
    line.text = text;
    return line;
  };

  setup(async () => {
    element = await fixture<GrDiff>(html`<gr-diff></gr-diff>`);
    element.diff = createEmptyDiff();
    await element.updateComplete;
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    stubRestApi('getProjectConfig').returns(Promise.resolve(createConfig()));
    stubBaseUrl('/r');
  });

  suite('intraline differences', () => {
    let el: HTMLElement;
    let str: string;
    let annotateElementSpy: sinon.SinonSpy;
    let layer: DiffLayer;
    const lineNumberEl = document.createElement('td');

    function slice(str: string, start: number, end?: number) {
      return Array.from(str).slice(start, end).join('');
    }

    setup(async () => {
      el = await fixture(html`
        <div>Lorem ipsum dolor sit amet, suspendisse inceptos vehicula</div>
      `);
      str = el.textContent ?? '';
      annotateElementSpy = sinon.spy(GrAnnotationImpl, 'annotateElement');
      layer = element.createIntralineLayer();
    });

    test('annotate no highlights', () => {
      layer.annotate(el, lineNumberEl, line(str), Side.LEFT);

      // The content is unchanged.
      assert.isFalse(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 1);
      assert.instanceOf(el.childNodes[0], Text);
      assert.equal(str, el.childNodes[0].textContent);
    });

    test('annotate with highlights', () => {
      const l = line(str);
      l.highlights = [
        {contentIndex: 0, startIndex: 6, endIndex: 12},
        {contentIndex: 0, startIndex: 18, endIndex: 22},
      ];
      const str0 = slice(str, 0, 6);
      const str1 = slice(str, 6, 12);
      const str2 = slice(str, 12, 18);
      const str3 = slice(str, 18, 22);
      const str4 = slice(str, 22);

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isTrue(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 5);

      assert.instanceOf(el.childNodes[0], Text);
      assert.equal(el.childNodes[0].textContent, str0);

      assert.notInstanceOf(el.childNodes[1], Text);
      assert.equal(el.childNodes[1].textContent, str1);

      assert.instanceOf(el.childNodes[2], Text);
      assert.equal(el.childNodes[2].textContent, str2);

      assert.notInstanceOf(el.childNodes[3], Text);
      assert.equal(el.childNodes[3].textContent, str3);

      assert.instanceOf(el.childNodes[4], Text);
      assert.equal(el.childNodes[4].textContent, str4);
    });

    test('annotate without endIndex', () => {
      const l = line(str);
      l.highlights = [{contentIndex: 0, startIndex: 28}];

      const str0 = slice(str, 0, 28);
      const str1 = slice(str, 28);

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isTrue(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 2);

      assert.instanceOf(el.childNodes[0], Text);
      assert.equal(el.childNodes[0].textContent, str0);

      assert.notInstanceOf(el.childNodes[1], Text);
      assert.equal(el.childNodes[1].textContent, str1);
    });

    test('annotate ignores empty highlights', () => {
      const l = line(str);
      l.highlights = [{contentIndex: 0, startIndex: 28, endIndex: 28}];

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isFalse(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 1);
    });

    test('annotate handles unicode', () => {
      // Put some unicode into the string:
      str = str.replace(/\s/g, '💢');
      el.textContent = str;
      const l = line(str);
      l.highlights = [{contentIndex: 0, startIndex: 6, endIndex: 12}];

      const str0 = slice(str, 0, 6);
      const str1 = slice(str, 6, 12);
      const str2 = slice(str, 12);

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isTrue(annotateElementSpy.called);
      assert.equal(el.childNodes.length, 3);

      assert.instanceOf(el.childNodes[0], Text);
      assert.equal(el.childNodes[0].textContent, str0);

      assert.notInstanceOf(el.childNodes[1], Text);
      assert.equal(el.childNodes[1].textContent, str1);

      assert.instanceOf(el.childNodes[2], Text);
      assert.equal(el.childNodes[2].textContent, str2);
    });

    test('annotate handles unicode w/o endIndex', () => {
      // Put some unicode into the string:
      str = str.replace(/\s/g, '💢');
      el.textContent = str;

      const l = line(str);
      l.highlights = [{contentIndex: 0, startIndex: 6}];

      const str0 = slice(str, 0, 6);
      const str1 = slice(str, 6);
      const numHighlightedChars = getStringLength(str1);

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isTrue(annotateElementSpy.calledWith(el, 6, numHighlightedChars));
      assert.equal(el.childNodes.length, 2);

      assert.instanceOf(el.childNodes[0], Text);
      assert.equal(el.childNodes[0].textContent, str0);

      assert.notInstanceOf(el.childNodes[1], Text);
      assert.equal(el.childNodes[1].textContent, str1);
    });
  });

  suite('tab indicators', () => {
    let layer: DiffLayer;
    const lineNumberEl = document.createElement('td');

    setup(() => {
      element.prefs = {...DEFAULT_PREFS, show_tabs: true};
      layer = element.createTabIndicatorLayer();
    });

    test('does nothing with empty line', () => {
      const l = line('');
      const el = document.createElement('div');
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isFalse(annotateElementStub.called);
    });

    test('does nothing with no tabs', () => {
      const str = 'lorem ipsum no tabs';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isFalse(annotateElementStub.called);
    });

    test('annotates tab at beginning', () => {
      const str = '\tlorem upsum';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.equal(annotateElementStub.callCount, 1);
      const args = annotateElementStub.getCalls()[0].args;
      assert.equal(args[0], el);
      assert.equal(args[1], 0, 'offset of tab indicator');
      assert.equal(args[2], 1, 'length of tab indicator');
      assert.include(args[3], 'tab-indicator');
    });

    test('does not annotate when disabled', () => {
      element.prefs = {...DEFAULT_PREFS, show_tabs: false};

      const str = '\tlorem upsum';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isFalse(annotateElementStub.called);
    });

    test('annotates multiple in beginning', () => {
      const str = '\t\tlorem upsum';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.equal(annotateElementStub.callCount, 2);

      let args = annotateElementStub.getCalls()[0].args;
      assert.equal(args[0], el);
      assert.equal(args[1], 0, 'offset of tab indicator');
      assert.equal(args[2], 1, 'length of tab indicator');
      assert.include(args[3], 'tab-indicator');

      args = annotateElementStub.getCalls()[1].args;
      assert.equal(args[0], el);
      assert.equal(args[1], 1, 'offset of tab indicator');
      assert.equal(args[2], 1, 'length of tab indicator');
      assert.include(args[3], 'tab-indicator');
    });

    test('annotates intermediate tabs', () => {
      const str = 'lorem\tupsum';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.equal(annotateElementStub.callCount, 1);
      const args = annotateElementStub.getCalls()[0].args;
      assert.equal(args[0], el);
      assert.equal(args[1], 5, 'offset of tab indicator');
      assert.equal(args[2], 1, 'length of tab indicator');
      assert.include(args[3], 'tab-indicator');
    });
  });

  suite('trailing whitespace', () => {
    let layer: DiffLayer;
    const lineNumberEl = document.createElement('td');

    setup(() => {
      element.prefs = {
        ...createDefaultDiffPrefs(),
        show_whitespace_errors: true,
      };
      layer = element.createTrailingWhitespaceLayer();
    });

    test('does nothing with empty line', () => {
      const l = line('');
      const el = document.createElement('div');
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isFalse(annotateElementStub.called);
    });

    test('does nothing with no trailing whitespace', () => {
      const str = 'lorem ipsum blah blah';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isFalse(annotateElementStub.called);
    });

    test('annotates trailing spaces', () => {
      const str = 'lorem ipsum   ';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isTrue(annotateElementStub.called);
      assert.equal(annotateElementStub.lastCall.args[1], 11);
      assert.equal(annotateElementStub.lastCall.args[2], 3);
    });

    test('annotates trailing tabs', () => {
      const str = 'lorem ipsum\t\t\t';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isTrue(annotateElementStub.called);
      assert.equal(annotateElementStub.lastCall.args[1], 11);
      assert.equal(annotateElementStub.lastCall.args[2], 3);
    });

    test('annotates mixed trailing whitespace', () => {
      const str = 'lorem ipsum\t \t';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isTrue(annotateElementStub.called);
      assert.equal(annotateElementStub.lastCall.args[1], 11);
      assert.equal(annotateElementStub.lastCall.args[2], 3);
    });

    test('unicode preceding trailing whitespace', () => {
      const str = '💢\t';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isTrue(annotateElementStub.called);
      assert.equal(annotateElementStub.lastCall.args[1], 1);
      assert.equal(annotateElementStub.lastCall.args[2], 1);
    });

    test('does not annotate when disabled', () => {
      element.prefs = {
        ...createDefaultDiffPrefs(),
        show_whitespace_errors: false,
      };
      const str = 'lorem upsum\t \t ';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(
        GrAnnotationImpl,
        'annotateElement'
      );
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isFalse(annotateElementStub.called);
    });
  });

  suite('context hiding and expanding', () => {
    setup(async () => {
      element.diff = {
        ...createEmptyDiff(),
        content: [
          {ab: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(i => `unchanged ${i}`)},
          {a: ['before'], b: ['after']},
          {ab: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(i => `unchanged ${10 + i}`)},
        ],
      };
      element.viewMode = DiffViewMode.SIDE_BY_SIDE;
      element.prefs = {
        ...DEFAULT_PREFS,
        context: 1,
      };
      await waitUntil(() => element.groups.length > 2);
      await element.updateComplete;
    });

    test('hides lines behind two context controls', () => {
      const contextControls = queryAll(element, 'gr-context-controls');
      assert.equal(contextControls.length, 2);

      const diffRows = queryAll(element, '.diff-row');
      // The first two are LOST and FILE line
      assert.equal(diffRows.length, 2 + 1 + 1 + 1);
      assert.include(diffRows[2].textContent, 'unchanged 10');
      assert.include(diffRows[3].textContent, 'before');
      assert.include(diffRows[3].textContent, 'after');
      assert.include(diffRows[4].textContent, 'unchanged 11');
    });

    test('clicking +x common lines expands those lines', async () => {
      const contextControls = queryAll(element, 'gr-context-controls');
      const topExpandCommonButton =
        contextControls[0].shadowRoot?.querySelectorAll<HTMLElement>(
          '.showContext'
        )[0];
      assert.isOk(topExpandCommonButton);
      assert.include(topExpandCommonButton!.textContent, '+9 common lines');
      let diffRows = queryAll(element, '.diff-row');
      // 5 lines:
      // FILE, LOST, the changed line plus one line of context in each direction
      assert.equal(diffRows.length, 5);

      topExpandCommonButton!.click();

      await waitUntil(() => {
        diffRows = queryAll(element, '.diff-row');
        return diffRows.length === 14;
      });
      // 14 lines: The 5 above plus the 9 unchanged lines that were expanded
      assert.equal(diffRows.length, 14);
      assert.include(diffRows[2].textContent, 'unchanged 1');
      assert.include(diffRows[3].textContent, 'unchanged 2');
      assert.include(diffRows[4].textContent, 'unchanged 3');
      assert.include(diffRows[5].textContent, 'unchanged 4');
      assert.include(diffRows[6].textContent, 'unchanged 5');
      assert.include(diffRows[7].textContent, 'unchanged 6');
      assert.include(diffRows[8].textContent, 'unchanged 7');
      assert.include(diffRows[9].textContent, 'unchanged 8');
      assert.include(diffRows[10].textContent, 'unchanged 9');
      assert.include(diffRows[11].textContent, 'unchanged 10');
      assert.include(diffRows[12].textContent, 'before');
      assert.include(diffRows[12].textContent, 'after');
      assert.include(diffRows[13].textContent, 'unchanged 11');
    });

    test('unhideLine shows the line with context', async () => {
      element.unhideLine(4, Side.LEFT);

      await waitUntil(() => {
        const rows = queryAll(element, '.diff-row');
        return rows.length === 2 + 5 + 1 + 1 + 1;
      });

      const diffRows = queryAll(element, '.diff-row');
      // The first two are LOST and FILE line
      // Lines 3-5 (Line 4 plus 1 context in each direction) will be expanded
      // Because context expanders do not hide <3 lines, lines 1-2 will also
      // be shown.
      // Lines 6-9 continue to be hidden
      assert.equal(diffRows.length, 2 + 5 + 1 + 1 + 1);
      assert.include(diffRows[2].textContent, 'unchanged 1');
      assert.include(diffRows[3].textContent, 'unchanged 2');
      assert.include(diffRows[4].textContent, 'unchanged 3');
      assert.include(diffRows[5].textContent, 'unchanged 4');
      assert.include(diffRows[6].textContent, 'unchanged 5');
      assert.include(diffRows[7].textContent, 'unchanged 10');
      assert.include(diffRows[8].textContent, 'before');
      assert.include(diffRows[8].textContent, 'after');
      assert.include(diffRows[9].textContent, 'unchanged 11');
    });
  });
});
