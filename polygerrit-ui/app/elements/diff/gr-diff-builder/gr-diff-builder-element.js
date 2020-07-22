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
import '../gr-coverage-layer/gr-coverage-layer.js';
import '../gr-diff-processor/gr-diff-processor.js';
import '../../shared/gr-hovercard/gr-hovercard.js';
import '../gr-ranged-comment-layer/gr-ranged-comment-layer.js';
import './gr-diff-builder-side-by-side.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-diff-builder-element_html.js';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation.js';
import {GrDiffBuilder} from './gr-diff-builder.js';
import {GrDiffBuilderSideBySide} from './gr-diff-builder-side-by-side.js';
import {GrDiffBuilderImage} from './gr-diff-builder-image.js';
import {GrDiffBuilderUnified} from './gr-diff-builder-unified.js';
import {GrDiffBuilderBinary} from './gr-diff-builder-binary.js';
import {util} from '../../../scripts/util.js';

const DiffViewMode = {
  SIDE_BY_SIDE: 'SIDE_BY_SIDE',
  UNIFIED: 'UNIFIED_DIFF',
};

const TRAILING_WHITESPACE_PATTERN = /\s+$/;

// https://gerrit.googlesource.com/gerrit/+/234616a8627334686769f1de989d286039f4d6a5/polygerrit-ui/app/elements/diff/gr-diff/gr-diff.js#740
const COMMIT_MSG_PATH = '/COMMIT_MSG';
const COMMIT_MSG_LINE_LENGTH = 72;

/**
 * @extends PolymerElement
 */
class GrDiffBuilderElement extends GestureEventListeners(
    LegacyElementMixin(PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-diff-builder'; }
  /**
   * Fired when the diff begins rendering.
   *
   * @event render-start
   */

  /**
   * Fired when the diff finishes rendering text content.
   *
   * @event render-content
   */

  static get properties() {
    return {
      diff: Object,
      changeNum: String,
      patchNum: String,
      viewMode: String,
      isImageDiff: Boolean,
      baseImage: Object,
      revisionImage: Object,
      parentIndex: Number,
      path: String,
      projectName: String,

      _builder: Object,
      _groups: Array,
      _layers: Array,
      _showTabs: Boolean,
      /** @type {!Array<!Gerrit.HoveredRange>} */
      commentRanges: {
        type: Array,
        value: () => [],
      },
      /** @type {!Array<!Gerrit.CoverageRange>} */
      coverageRanges: {
        type: Array,
        value: () => [],
      },
      _leftCoverageRanges: {
        type: Array,
        computed: '_computeLeftCoverageRanges(coverageRanges)',
      },
      _rightCoverageRanges: {
        type: Array,
        computed: '_computeRightCoverageRanges(coverageRanges)',
      },
      /**
       * The promise last returned from `render()` while the asynchronous
       * rendering is running - `null` otherwise. Provides a `cancel()`
       * method that rejects it with `{isCancelled: true}`.
       *
       * @type {?Object}
       */
      _cancelableRenderPromise: Object,
      layers: {
        type: Array,
        value: [],
      },
    };
  }

  /** @override */
  detached() {
    super.detached();
    if (this._builder) {
      this._builder.clear();
    }
  }

  get diffElement() {
    return this.queryEffectiveChildren('#diffTable');
  }

  static get observers() {
    return [
      '_groupsChanged(_groups.splices)',
    ];
  }

  _computeLeftCoverageRanges(coverageRanges) {
    return coverageRanges.filter(range => range && range.side === 'left');
  }

  _computeRightCoverageRanges(coverageRanges) {
    return coverageRanges.filter(range => range && range.side === 'right');
  }

  render(keyLocations, prefs) {
    // Setting up annotation layers must happen after plugins are
    // installed, and |render| satisfies the requirement, however,
    // |attached| doesn't because in the diff view page, the element is
    // attached before plugins are installed.
    this._setupAnnotationLayers();

    this._showTabs = !!prefs.show_tabs;
    this._showTrailingWhitespace = !!prefs.show_whitespace_errors;

    // Stop the processor if it's running.
    this.cancel();

    if (this._builder) {
      this._builder.clear();
    }
    this._builder = this._getDiffBuilder(this.diff, prefs);

    this.$.processor.context = prefs.context;
    this.$.processor.keyLocations = keyLocations;

    this._clearDiffContent();
    this._builder.addColumns(this.diffElement, prefs.font_size);

    const isBinary = !!(this.isImageDiff || this.diff.binary);

    this.dispatchEvent(new CustomEvent(
        'render-start', {bubbles: true, composed: true}));
    this._cancelableRenderPromise = util.makeCancelable(
        this.$.processor.process(this.diff.content, isBinary)
            .then(() => {
              if (this.isImageDiff) {
                this._builder.renderDiff();
              }
              this.dispatchEvent(new CustomEvent('render-content',
                  {bubbles: true, composed: true}));
            }));
    return this._cancelableRenderPromise
        .finally(() => { this._cancelableRenderPromise = null; })
    // Mocca testing does not like uncaught rejections, so we catch
    // the cancels which are expected and should not throw errors in
    // tests.
        .catch(e => { if (!e.isCanceled) return Promise.reject(e); });
  }

  _setupAnnotationLayers() {
    const layers = [
      this._createTrailingWhitespaceLayer(),
      this._createIntralineLayer(),
      this._createTabIndicatorLayer(),
      this.$.rangeLayer,
      this.$.coverageLayerLeft,
      this.$.coverageLayerRight,
    ];

    if (this.layers) {
      layers.push(...this.layers);
    }
    this._layers = layers;
  }

  getLineElByChild(node) {
    while (node) {
      if (node instanceof Element) {
        if (node.classList.contains('lineNum')) {
          return node;
        }
        if (node.classList.contains('section')) {
          return null;
        }
      }
      node = node.previousSibling || node.parentElement;
    }
    return null;
  }

  getLineNumberByChild(node) {
    const lineEl = this.getLineElByChild(node);
    return lineEl ?
      parseInt(lineEl.getAttribute('data-value'), 10) :
      null;
  }

  getContentTdByLine(lineNumber, opt_side, opt_root) {
    return this._builder.getContentTdByLine(lineNumber, opt_side, opt_root);
  }

  _getDiffRowByChild(child) {
    while (!child.classList.contains('diff-row') && child.parentElement) {
      child = child.parentElement;
    }
    return child;
  }

  getContentTdByLineEl(lineEl) {
    if (!lineEl) return;
    const line = lineEl.getAttribute('data-value');
    const side = this.getSideByLineEl(lineEl);
    // Performance optimization because we already have an element in the
    // correct row
    const row = dom(this._getDiffRowByChild(lineEl));
    return this.getContentTdByLine(line, side, row);
  }

  getLineElByNumber(lineNumber, opt_side) {
    const sideSelector = opt_side ? ('.' + opt_side) : '';
    return this.diffElement.querySelector(
        '.lineNum[data-value="' + lineNumber + '"]' + sideSelector);
  }

  getContentsByLineRange(startLine, endLine, opt_side) {
    const result = [];
    this._builder.findLinesByRange(startLine, endLine, opt_side, null,
        result);
    return result;
  }

  getSideByLineEl(lineEl) {
    return lineEl.classList.contains(GrDiffBuilder.Side.RIGHT) ?
      GrDiffBuilder.Side.RIGHT : GrDiffBuilder.Side.LEFT;
  }

  emitGroup(group, sectionEl) {
    this._builder.emitGroup(group, sectionEl);
  }

  showContext(newGroups, sectionEl) {
    const groups = this._builder.groups;

    const contextIndex = groups.findIndex(group =>
      group.element === sectionEl
    );
    groups.splice(contextIndex, 1, ...newGroups);

    for (const newGroup of newGroups) {
      this._builder.emitGroup(newGroup, sectionEl);
    }
    sectionEl.parentNode.removeChild(sectionEl);

    this.async(() => this.dispatchEvent(new CustomEvent('render-content', {
      composed: true, bubbles: true,
    })), 1);
  }

  cancel() {
    this.$.processor.cancel();
    if (this._cancelableRenderPromise) {
      this._cancelableRenderPromise.cancel();
      this._cancelableRenderPromise = null;
    }
  }

  _handlePreferenceError(pref) {
    const message = `The value of the '${pref}' user preference is ` +
        `invalid. Fix in diff preferences`;
    this.dispatchEvent(new CustomEvent('show-alert', {
      detail: {
        message,
      }, bubbles: true, composed: true}));
    throw Error(`Invalid preference value: ${pref}`);
  }

  _getDiffBuilder(diff, prefs) {
    if (isNaN(prefs.tab_size) || prefs.tab_size <= 0) {
      this._handlePreferenceError('tab size');
      return;
    }

    if (isNaN(prefs.line_length) || prefs.line_length <= 0) {
      this._handlePreferenceError('diff width');
      return;
    }

    const localPrefs = {...prefs};
    if (this.path === COMMIT_MSG_PATH) {
      // override line_length for commit msg the same way as
      // in gr-diff
      localPrefs.line_length = COMMIT_MSG_LINE_LENGTH;
    }

    let builder = null;
    if (this.isImageDiff) {
      builder = new GrDiffBuilderImage(
          diff,
          localPrefs,
          this.diffElement,
          this.baseImage,
          this.revisionImage);
    } else if (diff.binary) {
      // If the diff is binary, but not an image.
      return new GrDiffBuilderBinary(
          diff,
          localPrefs,
          this.diffElement);
    } else if (this.viewMode === DiffViewMode.SIDE_BY_SIDE) {
      builder = new GrDiffBuilderSideBySide(
          diff,
          localPrefs,
          this.diffElement,
          this._layers
      );
    } else if (this.viewMode === DiffViewMode.UNIFIED) {
      builder = new GrDiffBuilderUnified(
          diff,
          localPrefs,
          this.diffElement,
          this._layers);
    }
    if (!builder) {
      throw Error('Unsupported diff view mode: ' + this.viewMode);
    }
    return builder;
  }

  _clearDiffContent() {
    this.diffElement.innerHTML = null;
  }

  _groupsChanged(changeRecord) {
    if (!changeRecord) { return; }
    for (const splice of changeRecord.indexSplices) {
      let group;
      for (let i = 0; i < splice.addedCount; i++) {
        group = splice.object[splice.index + i];
        this._builder.groups.push(group);
        this._builder.emitGroup(group);
      }
    }
  }

  _createIntralineLayer() {
    return {
      // Take a DIV.contentText element and a line object with intraline
      // differences to highlight and apply them to the element as
      // annotations.
      annotate(contentEl, lineNumberEl, line) {
        const HL_CLASS = 'style-scope gr-diff intraline';
        for (const highlight of line.highlights) {
          // The start and end indices could be the same if a highlight is
          // meant to start at the end of a line and continue onto the
          // next one. Ignore it.
          if (highlight.startIndex === highlight.endIndex) { continue; }

          // If endIndex isn't present, continue to the end of the line.
          const endIndex = highlight.endIndex === undefined ?
            line.text.length :
            highlight.endIndex;

          GrAnnotation.annotateElement(
              contentEl,
              highlight.startIndex,
              endIndex - highlight.startIndex,
              HL_CLASS);
        }
      },
    };
  }

  _createTabIndicatorLayer() {
    const show = () => this._showTabs;
    return {
      annotate(contentEl, lineNumberEl, line) {
        // If visible tabs are disabled, do nothing.
        if (!show()) { return; }

        // Find and annotate the locations of tabs.
        const split = line.text.split('\t');
        if (!split) { return; }
        for (let i = 0, pos = 0; i < split.length - 1; i++) {
          // Skip forward by the length of the content
          pos += split[i].length;

          GrAnnotation.annotateElement(contentEl, pos, 1,
              'style-scope gr-diff tab-indicator');

          // Skip forward by one tab character.
          pos++;
        }
      },
    };
  }

  _createTrailingWhitespaceLayer() {
    const show = function() {
      return this._showTrailingWhitespace;
    }.bind(this);

    return {
      annotate(contentEl, lineNumberEl, line) {
        if (!show()) { return; }

        const match = line.text.match(TRAILING_WHITESPACE_PATTERN);
        if (match) {
          // Normalize string positions in case there is unicode before or
          // within the match.
          const index = GrAnnotation.getStringLength(
              line.text.substr(0, match.index));
          const length = GrAnnotation.getStringLength(match[0]);
          GrAnnotation.annotateElement(contentEl, index, length,
              'style-scope gr-diff trailing-whitespace');
        }
      },
    };
  }

  setBlame(blame) {
    if (!this._builder || !blame) { return; }
    this._builder.setBlame(blame);
  }
}

customElements.define(GrDiffBuilderElement.is, GrDiffBuilderElement);
