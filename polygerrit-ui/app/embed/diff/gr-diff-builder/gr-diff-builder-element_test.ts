/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../../test/common-test-setup';
import {
  createConfig,
  createDiff,
  createEmptyDiff,
} from '../../../test/test-data-generators';
import './gr-diff-builder-element';
import {queryAndAssert, stubBaseUrl, waitUntil} from '../../../test/test-utils';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import {GrDiffLine, GrDiffLineType} from '../gr-diff/gr-diff-line';
import {GrDiffBuilderSideBySide} from './gr-diff-builder-side-by-side';
import {
  DiffContent,
  DiffInfo,
  DiffLayer,
  DiffPreferencesInfo,
  DiffViewMode,
  Side,
} from '../../../api/diff';
import {stubRestApi} from '../../../test/test-utils';
import {GrDiffBuilderLegacy} from './gr-diff-builder-legacy';
import {waitForEventOnce} from '../../../utils/event-util';
import {GrDiffBuilderElement} from './gr-diff-builder-element';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {KeyLocations} from '../gr-diff-processor/gr-diff-processor';
import {BlameInfo} from '../../../types/common';
import {fixture, html, assert} from '@open-wc/testing';
import {EventType} from '../../../types/events';

const DEFAULT_PREFS = createDefaultDiffPrefs();

suite('gr-diff-builder tests', () => {
  let element: GrDiffBuilderElement;
  let builder: GrDiffBuilderLegacy;
  let diffTable: HTMLTableElement;

  const LINE_BREAK_HTML = '<span class="gr-diff br"></span>';
  const WBR_HTML = '<wbr class="gr-diff">';

  const setBuilderPrefs = (prefs: Partial<DiffPreferencesInfo>) => {
    builder = new GrDiffBuilderSideBySide(
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

  test('line_length applied with <wbr> if line_wrapping is true', () => {
    setBuilderPrefs({line_wrapping: true, tab_size: 4, line_length: 50});
    const text = 'a'.repeat(51);
    const expected = 'a'.repeat(50) + WBR_HTML + 'a';
    const result = builder.createTextEl(null, line(text)).firstElementChild
      ?.firstElementChild!.innerHTML;
    assert.equal(result, expected);
  });

  test('line_length applied with line break if line_wrapping is false', () => {
    setBuilderPrefs({line_wrapping: false, tab_size: 4, line_length: 50});
    const text = 'a'.repeat(51);
    const expected = 'a'.repeat(50) + LINE_BREAK_HTML + 'a';
    const result = builder.createTextEl(null, line(text)).firstElementChild
      ?.firstElementChild!.innerHTML;
    assert.equal(result, expected);
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
      builder = element.getDiffBuilder() as GrDiffBuilderLegacy;
      assert.equal(builder._prefs.line_length, 50);
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
      builder = element.getDiffBuilder() as GrDiffBuilderLegacy;
      assert.equal(builder._prefs.line_length, 72);
    });
  });

  test('createTextEl linewrap with tabs', () => {
    setBuilderPrefs({tab_size: 4, line_length: 10});
    const text = '\t'.repeat(7) + '!';
    const el = builder.createTextEl(null, line(text));
    assert.equal(el.innerText, text);
    // With line length 10 and tab size 4, there should be a line break
    // after every two tabs.
    const newlineEl = el.querySelector('.contentText .br');
    assert.isOk(newlineEl);
    assert.equal(
      el.querySelector('.contentText .tab:nth-child(2)')?.nextSibling,
      newlineEl
    );
  });

  test('_handlePreferenceError throws with invalid preference', () => {
    element.prefs = {...createDefaultDiffPrefs(), tab_size: 0};
    assert.throws(() => element.getDiffBuilder());
  });

  test('_handlePreferenceError triggers alert and javascript error', () => {
    const errorStub = sinon.stub();
    diffTable.addEventListener(EventType.SHOW_ALERT, errorStub);
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

      layer.annotate(el, lineNumberEl, l, Side.LEFT);

      assert.isTrue(annotateElementSpy.called);
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
    let processStub: sinon.SinonStub;
    let keyLocations: KeyLocations;
    let content: DiffContent[] = [];

    setup(() => {
      element.viewMode = 'SIDE_BY_SIDE';
      processStub = sinon
        .stub(element.processor, 'process')
        .returns(Promise.resolve());
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
      assert.isTrue(processStub.calledOnce);
      assert.isFalse(processStub.lastCall.args[1]);
    });

    test('image', async () => {
      element.diff = {...createEmptyDiff(), content, binary: true};
      element.isImageDiff = true;
      element.render(keyLocations);
      await waitForEventOnce(diffTable, 'render-content');
      assert.isTrue(processStub.calledOnce);
      assert.isTrue(processStub.lastCall.args[1]);
    });

    test('binary', async () => {
      element.diff = {...createEmptyDiff(), content, binary: true};
      element.render(keyLocations);
      await waitForEventOnce(diffTable, 'render-content');
      assert.isTrue(processStub.calledOnce);
      assert.isTrue(processStub.lastCall.args[1]);
    });
  });

  suite('rendering', () => {
    let content: DiffContent[];
    let outputEl: HTMLTableElement;
    let keyLocations: KeyLocations;
    let addColumnsStub: sinon.SinonStub;
    let dispatchStub: sinon.SinonStub;
    let builder: GrDiffBuilderSideBySide;

    setup(() => {
      const prefs = {...DEFAULT_PREFS};
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
      dispatchStub = sinon.stub(diffTable, 'dispatchEvent');
      outputEl = element.diffElement!;
      keyLocations = {left: {}, right: {}};
      sinon.stub(element, 'getDiffBuilder').callsFake(() => {
        builder = new GrDiffBuilderSideBySide(
          {...createEmptyDiff(), content},
          prefs,
          outputEl
        );
        addColumnsStub = sinon.stub(builder, 'addColumns');
        builder.buildSectionElement = function (group) {
          const section = document.createElement('stub');
          section.style.display = 'block';
          section.textContent = group.lines.reduce(
            (acc, line) => acc + line.text,
            ''
          );
          return section;
        };
        return builder;
      });
      element.diff = {...createEmptyDiff(), content};
      element.prefs = prefs;
      element.render(keyLocations);
    });

    test('addColumns is called', () => {
      assert.isTrue(addColumnsStub.called);
    });

    test('getGroupsByLineRange one line', () => {
      const section = outputEl.querySelector<HTMLElement>(
        'stub:nth-of-type(3)'
      );
      const groups = builder.getGroupsByLineRange(1, 1, Side.LEFT);
      assert.equal(groups.length, 1);
      assert.strictEqual(groups[0].element, section);
    });

    test('getGroupsByLineRange over diff', () => {
      const section = [
        outputEl.querySelector<HTMLElement>('stub:nth-of-type(3)'),
        outputEl.querySelector<HTMLElement>('stub:nth-of-type(4)'),
      ];
      const groups = builder.getGroupsByLineRange(1, 2, Side.LEFT);
      assert.equal(groups.length, 2);
      assert.strictEqual(groups[0].element, section[0]);
      assert.strictEqual(groups[1].element, section[1]);
    });

    test('render-start and render-content are fired', async () => {
      await waitUntil(() => dispatchStub.callCount >= 1);
      let firedEventTypes = dispatchStub.getCalls().map(c => c.args[0].type);
      assert.include(firedEventTypes, 'render-start');

      await waitUntil(() => dispatchStub.callCount >= 2);
      firedEventTypes = dispatchStub.getCalls().map(c => c.args[0].type);
      assert.include(firedEventTypes, 'render-content');
    });

    test('cancel cancels the processor', () => {
      const processorCancelStub = sinon.stub(element.processor, 'cancel');
      element.cleanup();
      assert.isTrue(processorCancelStub.called);
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

    test('clicking +x common lines expands those lines', () => {
      const contextControls = diffTable.querySelectorAll('gr-context-controls');
      const topExpandCommonButton =
        contextControls[0].shadowRoot?.querySelectorAll<HTMLElement>(
          '.showContext'
        )[0];
      assert.isOk(topExpandCommonButton);
      assert.include(topExpandCommonButton!.textContent, '+9 common lines');
      topExpandCommonButton!.click();
      const diffRows = diffTable.querySelectorAll('.diff-row');
      // The first two are LOST and FILE line
      assert.equal(diffRows.length, 2 + 10 + 1 + 1);
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

  [DiffViewMode.UNIFIED, DiffViewMode.SIDE_BY_SIDE].forEach(mode => {
    suite(`mock-diff mode:${mode}`, () => {
      let builder: GrDiffBuilderSideBySide;
      let diff: DiffInfo;
      let keyLocations: KeyLocations;

      setup(() => {
        element.viewMode = mode;
        diff = createDiff();
        element.diff = diff;

        keyLocations = {left: {}, right: {}};

        element.prefs = {
          ...createDefaultDiffPrefs(),
          line_length: 80,
          show_tabs: true,
          tab_size: 4,
        };
        element.render(keyLocations);
        builder = element.builder as GrDiffBuilderSideBySide;
      });

      test('aria-labels on added line numbers', () => {
        const deltaLineNumberButton = diffTable.querySelectorAll(
          '.lineNumButton.right'
        )[5];

        assert.isOk(deltaLineNumberButton);
        assert.equal(
          deltaLineNumberButton.getAttribute('aria-label'),
          '5 added'
        );
      });

      test('aria-labels on removed line numbers', () => {
        const deltaLineNumberButton = diffTable.querySelectorAll(
          '.lineNumButton.left'
        )[10];

        assert.isOk(deltaLineNumberButton);
        assert.equal(
          deltaLineNumberButton.getAttribute('aria-label'),
          '10 removed'
        );
      });

      test('getContentByLine', () => {
        let actual: HTMLElement | null;

        actual = builder.getContentByLine(2, Side.LEFT);
        assert.equal(actual?.textContent, diff.content[0].ab?.[1]);

        actual = builder.getContentByLine(2, Side.RIGHT);
        assert.equal(actual?.textContent, diff.content[0].ab?.[1]);

        actual = builder.getContentByLine(5, Side.LEFT);
        assert.equal(actual?.textContent, diff.content[2].ab?.[0]);

        actual = builder.getContentByLine(5, Side.RIGHT);
        assert.equal(actual?.textContent, diff.content[1].b?.[0]);
      });

      test('getContentTdByLineEl works both with button and td', () => {
        const diffRow = diffTable.querySelectorAll('tr.diff-row')[2];

        const lineNumTdLeft = queryAndAssert(diffRow, 'td.lineNum.left');
        const lineNumButtonLeft = queryAndAssert(lineNumTdLeft, 'button');
        const contentTdLeft = diffRow.querySelectorAll('.content')[0];

        const lineNumTdRight = queryAndAssert(diffRow, 'td.lineNum.right');
        const lineNumButtonRight = queryAndAssert(lineNumTdRight, 'button');
        const contentTdRight =
          mode === DiffViewMode.SIDE_BY_SIDE
            ? diffRow.querySelectorAll('.content')[1]
            : contentTdLeft;

        assert.equal(
          element.getContentTdByLineEl(lineNumTdLeft),
          contentTdLeft
        );
        assert.equal(
          element.getContentTdByLineEl(lineNumButtonLeft),
          contentTdLeft
        );
        assert.equal(
          element.getContentTdByLineEl(lineNumTdRight),
          contentTdRight
        );
        assert.equal(
          element.getContentTdByLineEl(lineNumButtonRight),
          contentTdRight
        );
      });

      test('findLinesByRange LEFT', () => {
        const lines: GrDiffLine[] = [];
        const elems: HTMLElement[] = [];
        const start = 1;
        const end = 44;

        // lines 26-29 are collapsed, so minus 4
        let count = end - start + 1 - 4;
        // Lines 14+15 are part of a 'common' chunk. And we have a bug in
        // unified diff that results in not rendering these lines for the LEFT
        // side. TODO: Fix that bug!
        if (mode === DiffViewMode.UNIFIED) count -= 2;

        builder.findLinesByRange(start, end, Side.LEFT, lines, elems);

        assert.equal(lines.length, count);
        assert.equal(elems.length, count);

        for (let i = 0; i < count; i++) {
          assert.instanceOf(lines[i], GrDiffLine);
          assert.instanceOf(elems[i], HTMLElement);
          assert.equal(lines[i].text, elems[i].textContent);
        }
      });

      test('findLinesByRange RIGHT', () => {
        const lines: GrDiffLine[] = [];
        const elems: HTMLElement[] = [];
        const start = 1;
        const end = 48;

        // lines 26-29 are collapsed, so minus 4
        const count = end - start + 1 - 4;

        builder.findLinesByRange(start, end, Side.RIGHT, lines, elems);

        assert.equal(lines.length, count);
        assert.equal(elems.length, count);

        for (let i = 0; i < count; i++) {
          assert.instanceOf(lines[i], GrDiffLine);
          assert.instanceOf(elems[i], HTMLElement);
          assert.equal(lines[i].text, elems[i].textContent);
        }
      });

      test('renderContentByRange', () => {
        const spy = sinon.spy(builder, 'createTextEl');
        const start = 9;
        const end = 14;
        let count = end - start + 1;
        // Lines 14+15 are part of a 'common' chunk. And we have a bug in
        // unified diff that results in not rendering these lines for the LEFT
        // side. TODO: Fix that bug!
        if (mode === DiffViewMode.UNIFIED) count -= 1;

        builder.renderContentByRange(start, end, Side.LEFT);

        assert.equal(spy.callCount, count);
        spy.getCalls().forEach((call, i: number) => {
          assert.equal(call.args[1].beforeNumber, start + i);
        });
      });

      test('renderContentByRange non-existent elements', () => {
        const spy = sinon.spy(builder, 'createTextEl');

        sinon
          .stub(builder, 'getLineNumberEl')
          .returns(document.createElement('div'));
        sinon
          .stub(builder, 'findLinesByRange')
          .callsFake((_1, _2, _3, lines, elements) => {
            // Add a line and a corresponding element.
            lines?.push(new GrDiffLine(GrDiffLineType.BOTH));
            const tr = document.createElement('tr');
            const td = document.createElement('td');
            const el = document.createElement('div');
            tr.appendChild(td);
            td.appendChild(el);
            elements?.push(el);

            // Add 2 lines without corresponding elements.
            lines?.push(new GrDiffLine(GrDiffLineType.BOTH));
            lines?.push(new GrDiffLine(GrDiffLineType.BOTH));
          });

        builder.renderContentByRange(1, 10, Side.LEFT);
        // Should be called only once because only one line had a corresponding
        // element.
        assert.equal(spy.callCount, 1);
      });

      test('getLineNumberEl side-by-side left', () => {
        const contentEl = builder.getContentByLine(
          5,
          Side.LEFT,
          element.diffElement as HTMLTableElement
        );
        assert.isOk(contentEl);
        const lineNumberEl = builder.getLineNumberEl(contentEl!, Side.LEFT);
        assert.isOk(lineNumberEl);
        assert.isTrue(lineNumberEl!.classList.contains('lineNum'));
        assert.isTrue(lineNumberEl!.classList.contains(Side.LEFT));
      });

      test('getLineNumberEl side-by-side right', () => {
        const contentEl = builder.getContentByLine(
          5,
          Side.RIGHT,
          element.diffElement as HTMLTableElement
        );
        assert.isOk(contentEl);
        const lineNumberEl = builder.getLineNumberEl(contentEl!, Side.RIGHT);
        assert.isOk(lineNumberEl);
        assert.isTrue(lineNumberEl!.classList.contains('lineNum'));
        assert.isTrue(lineNumberEl!.classList.contains(Side.RIGHT));
      });

      test('getLineNumberEl unified left', async () => {
        // Re-render as unified:
        element.viewMode = 'UNIFIED_DIFF';
        element.render(keyLocations);
        builder = element.builder as GrDiffBuilderSideBySide;

        const contentEl = builder.getContentByLine(
          5,
          Side.LEFT,
          element.diffElement as HTMLTableElement
        );
        assert.isOk(contentEl);
        const lineNumberEl = builder.getLineNumberEl(contentEl!, Side.LEFT);
        assert.isOk(lineNumberEl);
        assert.isTrue(lineNumberEl!.classList.contains('lineNum'));
        assert.isTrue(lineNumberEl!.classList.contains(Side.LEFT));
      });

      test('getLineNumberEl unified right', async () => {
        // Re-render as unified:
        element.viewMode = 'UNIFIED_DIFF';
        element.render(keyLocations);
        builder = element.builder as GrDiffBuilderSideBySide;

        const contentEl = builder.getContentByLine(
          5,
          Side.RIGHT,
          element.diffElement as HTMLTableElement
        );
        assert.isOk(contentEl);
        const lineNumberEl = builder.getLineNumberEl(contentEl!, Side.RIGHT);
        assert.isOk(lineNumberEl);
        assert.isTrue(lineNumberEl!.classList.contains('lineNum'));
        assert.isTrue(lineNumberEl!.classList.contains(Side.RIGHT));
      });

      test('getNextContentOnSide side-by-side left', () => {
        const startElem = builder.getContentByLine(
          5,
          Side.LEFT,
          element.diffElement as HTMLTableElement
        );
        assert.isOk(startElem);
        const expectedStartString = diff.content[2].ab?.[0];
        const expectedNextString = diff.content[2].ab?.[1];
        assert.equal(startElem!.textContent, expectedStartString);

        const nextElem = builder.getNextContentOnSide(startElem!, Side.LEFT);
        assert.isOk(nextElem);
        assert.equal(nextElem!.textContent, expectedNextString);
      });

      test('getNextContentOnSide side-by-side right', () => {
        const startElem = builder.getContentByLine(
          5,
          Side.RIGHT,
          element.diffElement as HTMLTableElement
        );
        const expectedStartString = diff.content[1].b?.[0];
        const expectedNextString = diff.content[1].b?.[1];
        assert.isOk(startElem);
        assert.equal(startElem!.textContent, expectedStartString);

        const nextElem = builder.getNextContentOnSide(startElem!, Side.RIGHT);
        assert.isOk(nextElem);
        assert.equal(nextElem!.textContent, expectedNextString);
      });

      test('getNextContentOnSide unified left', async () => {
        // Re-render as unified:
        element.viewMode = 'UNIFIED_DIFF';
        element.render(keyLocations);
        builder = element.builder as GrDiffBuilderSideBySide;

        const startElem = builder.getContentByLine(
          5,
          Side.LEFT,
          element.diffElement as HTMLTableElement
        );
        const expectedStartString = diff.content[2].ab?.[0];
        const expectedNextString = diff.content[2].ab?.[1];
        assert.isOk(startElem);
        assert.equal(startElem!.textContent, expectedStartString);

        const nextElem = builder.getNextContentOnSide(startElem!, Side.LEFT);
        assert.isOk(nextElem);
        assert.equal(nextElem!.textContent, expectedNextString);
      });

      test('getNextContentOnSide unified right', async () => {
        // Re-render as unified:
        element.viewMode = 'UNIFIED_DIFF';
        element.render(keyLocations);
        builder = element.builder as GrDiffBuilderSideBySide;

        const startElem = builder.getContentByLine(
          5,
          Side.RIGHT,
          element.diffElement as HTMLTableElement
        );
        const expectedStartString = diff.content[1].b?.[0];
        const expectedNextString = diff.content[1].b?.[1];
        assert.isOk(startElem);
        assert.equal(startElem!.textContent, expectedStartString);

        const nextElem = builder.getNextContentOnSide(startElem!, Side.RIGHT);
        assert.isOk(nextElem);
        assert.equal(nextElem!.textContent, expectedNextString);
      });
    });
  });

  suite('blame', () => {
    let mockBlame: BlameInfo[];

    setup(() => {
      mockBlame = [
        {
          author: 'test-author',
          time: 314,
          commit_msg: 'test-commit-message',
          id: 'commit 1',
          ranges: [
            {start: 1, end: 2},
            {start: 10, end: 16},
          ],
        },
        {
          author: 'test-author',
          time: 314,
          commit_msg: 'test-commit-message',
          id: 'commit 2',
          ranges: [
            {start: 4, end: 10},
            {start: 17, end: 32},
          ],
        },
      ];
    });

    test('setBlame attempts to render each blamed line', () => {
      const getBlameStub = sinon
        .stub(builder, 'getBlameTdByLine')
        .returns(undefined);
      builder.setBlame(mockBlame);
      assert.equal(getBlameStub.callCount, 32);
    });

    test('getBlameCommitForBaseLine', () => {
      sinon.stub(builder, 'getBlameTdByLine').returns(undefined);
      builder.setBlame(mockBlame);
      assert.isOk(builder.getBlameCommitForBaseLine(1));
      assert.equal(builder.getBlameCommitForBaseLine(1)?.id, 'commit 1');

      assert.isOk(builder.getBlameCommitForBaseLine(11));
      assert.equal(builder.getBlameCommitForBaseLine(11)?.id, 'commit 1');

      assert.isOk(builder.getBlameCommitForBaseLine(32));
      assert.equal(builder.getBlameCommitForBaseLine(32)?.id, 'commit 2');

      assert.isUndefined(builder.getBlameCommitForBaseLine(33));
    });

    test('getBlameCommitForBaseLine w/o blame returns null', () => {
      assert.isUndefined(builder.getBlameCommitForBaseLine(1));
      assert.isUndefined(builder.getBlameCommitForBaseLine(11));
      assert.isUndefined(builder.getBlameCommitForBaseLine(31));
    });

    test('createBlameCell', () => {
      const mockBlameInfo = {
        time: 1576155200,
        id: '1234567890',
        author: 'Clark Kent',
        commit_msg: 'Testing Commit',
        ranges: [{start: 4, end: 10}],
      };
      const getBlameStub = sinon
        .stub(builder, 'getBlameCommitForBaseLine')
        .returns(mockBlameInfo);
      const line = new GrDiffLine(GrDiffLineType.BOTH);
      line.beforeNumber = 3;
      line.afterNumber = 5;

      const result = builder.createBlameCell(line.beforeNumber);

      assert.isTrue(getBlameStub.calledWithExactly(3));
      assert.equal(result.getAttribute('data-line-number'), '3');
      assert.dom.equal(
        result,
        /* HTML */ `
          <span class="gr-diff">
            <a class="blameDate gr-diff" href="/r/q/1234567890"> 12/12/2019 </a>
            <span class="blameAuthor gr-diff">Clark</span>
            <gr-hovercard class="gr-diff">
              <span class="blameHoverCard gr-diff">
                Commit 1234567890<br />
                Author: Clark Kent<br />
                Date: 12/12/2019<br />
                <br />
                Testing Commit
              </span>
            </gr-hovercard>
          </span>
        `
      );
    });
  });
});
