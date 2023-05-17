/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {
  createConfig,
  createEmptyDiff,
} from '../../../test/test-data-generators';
import './gr-diff-builder-element';
import {stubBaseUrl, waitUntil} from '../../../test/test-utils';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import {GrDiffLine} from '../gr-diff/gr-diff-line';
import {
  DiffContent,
  DiffLayer,
  DiffPreferencesInfo,
  DiffViewMode,
  GrDiffLineType,
  Side,
} from '../../../api/diff';
import {stubRestApi} from '../../../test/test-utils';
import {waitForEventOnce} from '../../../utils/event-util';
import {GrDiffBuilderElement} from './gr-diff-builder-element';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {KeyLocations} from '../gr-diff-processor/gr-diff-processor';
import {fixture, html, assert} from '@open-wc/testing';
import {GrDiffRow} from './gr-diff-row';
import {GrDiffBuilder} from './gr-diff-builder';
import {querySelectorAll} from '../../../utils/dom-util';

const DEFAULT_PREFS = createDefaultDiffPrefs();

suite('gr-diff-builder tests', () => {
  let element: GrDiffBuilderElement;
  let builder: GrDiffBuilder;
  let diffTable: HTMLTableElement;

  const setBuilderPrefs = (prefs: Partial<DiffPreferencesInfo>) => {
    builder = new GrDiffBuilder(
      createEmptyDiff(),
      {...createDefaultDiffPrefs(), ...prefs},
      diffTable
    );
  };

  const line = (text: string) => {
    const line = new GrDiffLine(GrDiffLineType.BOTH);
    line.text = text;
    return line;
  };

  setup(async () => {
    diffTable = await fixture(html`<table id="diffTable"></table>`);
    element = new GrDiffBuilderElement();
    element.diffElement = diffTable;
    stubRestApi('getLoggedIn').returns(Promise.resolve(false));
    stubRestApi('getProjectConfig').returns(Promise.resolve(createConfig()));
    stubBaseUrl('/r');
    setBuilderPrefs({});
  });

  [DiffViewMode.UNIFIED, DiffViewMode.SIDE_BY_SIDE].forEach(mode => {
    test(`line_length used for regular files under ${mode}`, () => {
      element.path = '/a.txt';
      element.viewMode = mode;
      element.diff = createEmptyDiff();
      element.prefs = {
        ...createDefaultDiffPrefs(),
        tab_size: 4,
        line_length: 50,
      };
      builder = element.getDiffBuilder();
      assert.equal(builder.prefs.line_length, 50);
    });

    test(`line_length ignored for commit msg under ${mode}`, () => {
      element.path = '/COMMIT_MSG';
      element.viewMode = mode;
      element.diff = createEmptyDiff();
      element.prefs = {
        ...createDefaultDiffPrefs(),
        tab_size: 4,
        line_length: 50,
      };
      builder = element.getDiffBuilder();
      assert.equal(builder.prefs.line_length, 72);
    });
  });

  test('_handlePreferenceError throws with invalid preference', () => {
    element.prefs = {...createDefaultDiffPrefs(), tab_size: 0};
    assert.throws(() => element.getDiffBuilder());
  });

  test('_handlePreferenceError triggers alert and javascript error', () => {
    const errorStub = sinon.stub();
    diffTable.addEventListener('show-alert', errorStub);
    assert.throws(() => element.handlePreferenceError('tab size'));
    assert.equal(
      errorStub.lastCall.args[0].detail.message,
      "The value of the 'tab size' user preference is invalid. " +
        'Fix in diff preferences'
    );
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
      annotateElementSpy = sinon.spy(GrAnnotation, 'annotateElement');
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
      const numHighlightedChars = GrAnnotation.getStringLength(str1);

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
      element.showTabs = true;
      layer = element.createTabIndicatorLayer();
    });

    test('does nothing with empty line', () => {
      const l = line('');
      const el = document.createElement('div');
      const annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isFalse(annotateElementStub.called);
    });

    test('does nothing with no tabs', () => {
      const str = 'lorem ipsum no tabs';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isFalse(annotateElementStub.called);
    });

    test('annotates tab at beginning', () => {
      const str = '\tlorem upsum';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.equal(annotateElementStub.callCount, 1);
      const args = annotateElementStub.getCalls()[0].args;
      assert.equal(args[0], el);
      assert.equal(args[1], 0, 'offset of tab indicator');
      assert.equal(args[2], 1, 'length of tab indicator');
      assert.include(args[3], 'tab-indicator');
    });

    test('does not annotate when disabled', () => {
      element.showTabs = false;

      const str = '\tlorem upsum';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isFalse(annotateElementStub.called);
    });

    test('annotates multiple in beginning', () => {
      const str = '\t\tlorem upsum';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');

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
      const annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.equal(annotateElementStub.callCount, 1);
      const args = annotateElementStub.getCalls()[0].args;
      assert.equal(args[0], el);
      assert.equal(args[1], 5, 'offset of tab indicator');
      assert.equal(args[2], 1, 'length of tab indicator');
      assert.include(args[3], 'tab-indicator');
    });
  });

  suite('layers', () => {
    let initialLayersCount = 0;
    let withLayerCount = 0;
    setup(() => {
      const layers: DiffLayer[] = [];
      element.layers = layers;
      element.showTrailingWhitespace = true;
      element.setupAnnotationLayers();
      initialLayersCount = element.layersInternal.length;
    });

    test('no layers', () => {
      element.setupAnnotationLayers();
      assert.equal(element.layersInternal.length, initialLayersCount);
    });

    suite('with layers', () => {
      const layers: DiffLayer[] = [{annotate: () => {}}, {annotate: () => {}}];
      setup(() => {
        element.layers = layers;
        element.showTrailingWhitespace = true;
        element.setupAnnotationLayers();
        withLayerCount = element.layersInternal.length;
      });
      test('with layers', () => {
        element.setupAnnotationLayers();
        assert.equal(element.layersInternal.length, withLayerCount);
        assert.equal(initialLayersCount + layers.length, withLayerCount);
      });
    });
  });

  suite('trailing whitespace', () => {
    let layer: DiffLayer;
    const lineNumberEl = document.createElement('td');

    setup(() => {
      element.showTrailingWhitespace = true;
      layer = element.createTrailingWhitespaceLayer();
    });

    test('does nothing with empty line', () => {
      const l = line('');
      const el = document.createElement('div');
      const annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isFalse(annotateElementStub.called);
    });

    test('does nothing with no trailing whitespace', () => {
      const str = 'lorem ipsum blah blah';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isFalse(annotateElementStub.called);
    });

    test('annotates trailing spaces', () => {
      const str = 'lorem ipsum   ';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');
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
      const annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');
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
      const annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');
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
      const annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isTrue(annotateElementStub.called);
      assert.equal(annotateElementStub.lastCall.args[1], 1);
      assert.equal(annotateElementStub.lastCall.args[2], 1);
    });

    test('does not annotate when disabled', () => {
      element.showTrailingWhitespace = false;
      const str = 'lorem upsum\t \t ';
      const l = line(str);
      const el = document.createElement('div');
      el.textContent = str;
      const annotateElementStub = sinon.stub(GrAnnotation, 'annotateElement');
      layer.annotate(el, lineNumberEl, l, Side.LEFT);
      assert.isFalse(annotateElementStub.called);
    });
  });

  suite('rendering text, images and binary files', () => {
    let keyLocations: KeyLocations;
    let content: DiffContent[] = [];

    setup(() => {
      element.viewMode = 'SIDE_BY_SIDE';
      keyLocations = {left: {}, right: {}};
      element.prefs = {
        ...DEFAULT_PREFS,
        context: -1,
        syntax_highlighting: true,
      };
      content = [
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
    });

    test('text', async () => {
      element.diff = {...createEmptyDiff(), content};
      element.render(keyLocations);
      await waitForEventOnce(diffTable, 'render-content');
      assert.equal(querySelectorAll(diffTable, 'tbody')?.length, 4);
    });

    test('image', async () => {
      element.diff = {...createEmptyDiff(), content, binary: true};
      element.isImageDiff = true;
      element.render(keyLocations);
      await waitForEventOnce(diffTable, 'render-content');
      assert.equal(querySelectorAll(diffTable, 'tbody')?.length, 4);
    });

    test('binary', async () => {
      element.diff = {...createEmptyDiff(), content, binary: true};
      element.render(keyLocations);
      await waitForEventOnce(diffTable, 'render-content');
      assert.equal(querySelectorAll(diffTable, 'tbody')?.length, 3);
    });
  });

  suite('context hiding and expanding', () => {
    let dispatchStub: sinon.SinonStub;

    setup(async () => {
      dispatchStub = sinon.stub(diffTable, 'dispatchEvent');
      element.diff = {
        ...createEmptyDiff(),
        content: [
          {ab: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(i => `unchanged ${i}`)},
          {a: ['before'], b: ['after']},
          {ab: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(i => `unchanged ${10 + i}`)},
        ],
      };
      element.viewMode = DiffViewMode.SIDE_BY_SIDE;

      const keyLocations: KeyLocations = {left: {}, right: {}};
      element.prefs = {
        ...DEFAULT_PREFS,
        context: 1,
      };
      element.render(keyLocations);
      // Make sure all listeners are installed.
      await element.untilGroupsRendered();
    });

    test('hides lines behind two context controls', () => {
      const contextControls = diffTable.querySelectorAll('gr-context-controls');
      assert.equal(contextControls.length, 2);

      const diffRows = diffTable.querySelectorAll('.diff-row');
      // The first two are LOST and FILE line
      assert.equal(diffRows.length, 2 + 1 + 1 + 1);
      assert.include(diffRows[2].textContent, 'unchanged 10');
      assert.include(diffRows[3].textContent, 'before');
      assert.include(diffRows[3].textContent, 'after');
      assert.include(diffRows[4].textContent, 'unchanged 11');
    });

    test('clicking +x common lines expands those lines', async () => {
      const contextControls = diffTable.querySelectorAll('gr-context-controls');
      const topExpandCommonButton =
        contextControls[0].shadowRoot?.querySelectorAll<HTMLElement>(
          '.showContext'
        )[0];
      assert.isOk(topExpandCommonButton);
      assert.include(topExpandCommonButton!.textContent, '+9 common lines');
      let diffRows = diffTable.querySelectorAll('.diff-row');
      // 5 lines:
      // FILE, LOST, the changed line plus one line of context in each direction
      assert.equal(diffRows.length, 5);

      topExpandCommonButton!.click();

      await waitUntil(() => {
        diffRows = diffTable.querySelectorAll<GrDiffRow>('.diff-row');
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
      dispatchStub.reset();
      element.unhideLine(4, Side.LEFT);

      await waitUntil(() => {
        const rows = diffTable.querySelectorAll<GrDiffRow>('.diff-row');
        return rows.length === 2 + 5 + 1 + 1 + 1;
      });

      const diffRows = diffTable.querySelectorAll('.diff-row');
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

      await element.untilGroupsRendered();
      const firedEventTypes = dispatchStub.getCalls().map(c => c.args[0].type);
      assert.include(firedEventTypes, 'render-content');
    });
  });
});
