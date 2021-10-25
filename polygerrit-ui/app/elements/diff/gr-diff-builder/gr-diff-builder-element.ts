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
import '../gr-coverage-layer/gr-coverage-layer';
import '../gr-diff-processor/gr-diff-processor';
import '../../shared/gr-hovercard/gr-hovercard';
import '../gr-ranged-comment-layer/gr-ranged-comment-layer';
import './gr-diff-builder-side-by-side';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-diff-builder-element_html';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import {GrDiffBuilder} from './gr-diff-builder';
import {GrDiffBuilderSideBySide} from './gr-diff-builder-side-by-side';
import {GrDiffBuilderImage} from './gr-diff-builder-image';
import {GrDiffBuilderUnified} from './gr-diff-builder-unified';
import {GrDiffBuilderBinary} from './gr-diff-builder-binary';
import {CancelablePromise, util} from '../../../scripts/util';
import {customElement, property, observe} from '@polymer/decorators';
import {BlameInfo, ImageInfo} from '../../../types/common';
import {DiffInfo, DiffPreferencesInfo} from '../../../types/diff';
import {CoverageRange, DiffLayer} from '../../../types/types';
import {
  GrDiffProcessor,
  KeyLocations,
} from '../gr-diff-processor/gr-diff-processor';
import {
  CommentRangeLayer,
  GrRangedCommentLayer,
} from '../gr-ranged-comment-layer/gr-ranged-comment-layer';
import {GrCoverageLayer} from '../gr-coverage-layer/gr-coverage-layer';
import {DiffViewMode, RenderPreferences} from '../../../api/diff';
import {Side} from '../../../constants/constants';
import {GrDiffLine, LineNumber} from '../gr-diff/gr-diff-line';
import {GrDiffGroup} from '../gr-diff/gr-diff-group';
import {PolymerSpliceChange} from '@polymer/polymer/interfaces';
import {getLineNumber, getSideByLineEl} from '../gr-diff/gr-diff-utils';
import {fireAlert, fireEvent} from '../../../utils/event-util';

const TRAILING_WHITESPACE_PATTERN = /\s+$/;

// https://gerrit.googlesource.com/gerrit/+/234616a8627334686769f1de989d286039f4d6a5/polygerrit-ui/app/elements/diff/gr-diff/gr-diff.js#740
const COMMIT_MSG_PATH = '/COMMIT_MSG';
const COMMIT_MSG_LINE_LENGTH = 72;

export interface GrDiffBuilderElement {
  $: {
    processor: GrDiffProcessor;
    rangeLayer: GrRangedCommentLayer;
    coverageLayerLeft: GrCoverageLayer;
    coverageLayerRight: GrCoverageLayer;
  };
}

export function getLineNumberCellWidth(prefs: DiffPreferencesInfo) {
  return prefs.font_size * 4;
}

@customElement('gr-diff-builder')
export class GrDiffBuilderElement extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

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

  @property({type: Object})
  diff?: DiffInfo;

  @property({type: String})
  changeNum?: string;

  @property({type: String})
  patchNum?: string;

  @property({type: String})
  viewMode?: string;

  @property({type: Boolean})
  isImageDiff?: boolean;

  @property({type: Object})
  baseImage: ImageInfo | null = null;

  @property({type: Object})
  revisionImage: ImageInfo | null = null;

  @property({type: Number})
  parentIndex?: number;

  @property({type: String})
  path?: string;

  @property({type: Object})
  _builder?: GrDiffBuilder;

  @property({type: Array})
  _groups: GrDiffGroup[] = [];

  /**
   * Layers passed in from the outside.
   */
  @property({type: Array})
  layers: DiffLayer[] = [];

  /**
   * All layers, both from the outside and the default ones.
   */
  @property({type: Array})
  _layers: DiffLayer[] = [];

  @property({type: Boolean})
  _showTabs?: boolean;

  @property({type: Boolean})
  _showTrailingWhitespace?: boolean;

  @property({type: Array})
  commentRanges: CommentRangeLayer[] = [];

  @property({type: Array})
  coverageRanges: CoverageRange[] = [];

  @property({type: Boolean})
  useNewImageDiffUi = false;

  @property({
    type: Array,
    computed: '_computeLeftCoverageRanges(coverageRanges)',
  })
  _leftCoverageRanges?: CoverageRange[];

  @property({
    type: Array,
    computed: '_computeRightCoverageRanges(coverageRanges)',
  })
  _rightCoverageRanges?: CoverageRange[];

  /**
   * The promise last returned from `render()` while the asynchronous
   * rendering is running - `null` otherwise. Provides a `cancel()`
   * method that rejects it with `{isCancelled: true}`.
   */
  @property({type: Object})
  _cancelableRenderPromise: CancelablePromise<unknown> | null = null;

  override disconnectedCallback() {
    if (this._builder) {
      this._builder.clear();
    }
    super.disconnectedCallback();
  }

  get diffElement(): HTMLTableElement {
    // Not searching in shadowRoot, because the diff table is slotted!
    return this.querySelector('#diffTable') as HTMLTableElement;
  }

  _computeLeftCoverageRanges(coverageRanges: CoverageRange[]) {
    return coverageRanges.filter(range => range && range.side === 'left');
  }

  _computeRightCoverageRanges(coverageRanges: CoverageRange[]) {
    return coverageRanges.filter(range => range && range.side === 'right');
  }

  render(
    keyLocations: KeyLocations,
    prefs: DiffPreferencesInfo,
    renderPrefs?: RenderPreferences
  ) {
    console.log('render()');
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
    if (!this.diff) {
      throw Error('Cannot render a diff without DiffInfo.');
    }
    this._builder = this._getDiffBuilder(this.diff, prefs, renderPrefs);

    this.$.processor.context = prefs.context;
    this.$.processor.keyLocations = keyLocations;

    this._clearDiffContent();
    this._builder.addColumns(this.diffElement, getLineNumberCellWidth(prefs));

    const isBinary = !!(this.isImageDiff || this.diff.binary);

    fireEvent(this, 'render-start');
    this._cancelableRenderPromise = util.makeCancelable(
      this.$.processor.process(this.diff.content, isBinary).then(() => {
        if (this.isImageDiff) {
          (this._builder as GrDiffBuilderImage).renderDiff();
        }
        fireEvent(this, 'render-content');
      })
    );
    return (
      this._cancelableRenderPromise
        .finally(() => {
          this._cancelableRenderPromise = null;
        })
        // Mocca testing does not like uncaught rejections, so we catch
        // the cancels which are expected and should not throw errors in
        // tests.
        .catch(e => {
          if (!e.isCanceled) return Promise.reject(e);
          return;
        })
    );
  }

  _setupAnnotationLayers() {
    const layers: DiffLayer[] = [
      this._createTrailingWhitespaceLayer(),
      this._createIntralineLayer(),
      this._createTabIndicatorLayer(),
      this._createSpecialCharacterIndicatorLayer(),
      this.$.rangeLayer,
      this.$.coverageLayerLeft,
      this.$.coverageLayerRight,
    ];

    if (this.layers) {
      layers.push(...this.layers);
    }
    this._layers = layers;
  }

  getContentTdByLine(lineNumber: LineNumber, side?: Side, root?: Element) {
    if (!this._builder) return null;
    return this._builder.getContentTdByLine(lineNumber, side, root);
  }

  _getDiffRowByChild(child: Element) {
    while (!child.classList.contains('diff-row') && child.parentElement) {
      child = child.parentElement;
    }
    return child;
  }

  getContentTdByLineEl(lineEl?: Element): Element | null {
    if (!lineEl) return null;
    const line = getLineNumber(lineEl);
    if (!line) return null;
    const side = getSideByLineEl(lineEl);
    // Performance optimization because we already have an element in the
    // correct row
    const row = this._getDiffRowByChild(lineEl);
    return this.getContentTdByLine(line, side, row);
  }

  getLineElByNumber(lineNumber: LineNumber, side?: Side) {
    const sideSelector = side ? '.' + side : '';
    return this.diffElement.querySelector(
      `.lineNum[data-value="${lineNumber}"]${sideSelector}`
    );
  }

  emitGroup(group: GrDiffGroup, sectionEl: HTMLElement) {
    if (!this._builder) return;
    this._builder.emitGroup(group, sectionEl);
  }

  showContext(newGroups: GrDiffGroup[], sectionEl: HTMLElement) {
    if (!this._builder) return;
    const groups = this._builder.groups;

    const contextIndex = groups.findIndex(group => group.element === sectionEl);
    groups.splice(contextIndex, 1, ...newGroups);

    for (const newGroup of newGroups) {
      this._builder.emitGroup(newGroup, sectionEl);
    }
    if (sectionEl.parentNode) {
      sectionEl.parentNode.removeChild(sectionEl);
    }

    setTimeout(() => fireEvent(this, 'render-content'), 1);
  }

  cancel() {
    this.$.processor.cancel();
    if (this._cancelableRenderPromise) {
      this._cancelableRenderPromise.cancel();
      this._cancelableRenderPromise = null;
    }
  }

  _handlePreferenceError(pref: string): never {
    const message =
      `The value of the '${pref}' user preference is ` +
      'invalid. Fix in diff preferences';
    fireAlert(this, message);
    throw Error(`Invalid preference value: ${pref}`);
  }

  _getDiffBuilder(
    diff: DiffInfo,
    prefs: DiffPreferencesInfo,
    renderPrefs?: RenderPreferences
  ): GrDiffBuilder {
    if (isNaN(prefs.tab_size) || prefs.tab_size <= 0) {
      this._handlePreferenceError('tab size');
    }

    if (isNaN(prefs.line_length) || prefs.line_length <= 0) {
      this._handlePreferenceError('diff width');
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
        this.revisionImage,
        renderPrefs,
        this.useNewImageDiffUi
      );
    } else if (diff.binary) {
      // If the diff is binary, but not an image.
      return new GrDiffBuilderBinary(diff, localPrefs, this.diffElement);
    } else if (this.viewMode === DiffViewMode.SIDE_BY_SIDE) {
      builder = new GrDiffBuilderSideBySide(
        diff,
        localPrefs,
        this.diffElement,
        this._layers,
        renderPrefs
      );
    } else if (this.viewMode === DiffViewMode.UNIFIED) {
      builder = new GrDiffBuilderUnified(
        diff,
        localPrefs,
        this.diffElement,
        this._layers,
        renderPrefs
      );
    }
    if (!builder) {
      throw Error(`Unsupported diff view mode: ${this.viewMode}`);
    }
    return builder;
  }

  _clearDiffContent() {
    console.log('_clearDiffContent');
    this.diffElement.innerHTML = '';
  }

  @observe('_groups.splices')
  _groupsChanged(changeRecord: PolymerSpliceChange<GrDiffGroup[]>) {
    if (!changeRecord || !this._builder) {
      return;
    }
    for (const splice of changeRecord.indexSplices) {
      let group;
      for (let i = 0; i < splice.addedCount; i++) {
        group = splice.object[splice.index + i];
        this._builder.groups.push(group);
        this._builder.emitGroup(group, null);
      }
    }
  }

  _createIntralineLayer(): DiffLayer {
    return {
      // Take a DIV.contentText element and a line object with intraline
      // differences to highlight and apply them to the element as
      // annotations.
      annotate(contentEl: HTMLElement, _: HTMLElement, line: GrDiffLine) {
        const HL_CLASS = 'style-scope gr-diff intraline';
        for (const highlight of line.highlights) {
          // The start and end indices could be the same if a highlight is
          // meant to start at the end of a line and continue onto the
          // next one. Ignore it.
          if (highlight.startIndex === highlight.endIndex) {
            continue;
          }

          // If endIndex isn't present, continue to the end of the line.
          const endIndex =
            highlight.endIndex === undefined
              ? line.text.length
              : highlight.endIndex;

          GrAnnotation.annotateElement(
            contentEl,
            highlight.startIndex,
            endIndex - highlight.startIndex,
            HL_CLASS
          );
        }
      },
    };
  }

  _createTabIndicatorLayer(): DiffLayer {
    const show = () => this._showTabs;
    return {
      annotate(contentEl: HTMLElement, _: HTMLElement, line: GrDiffLine) {
        // If visible tabs are disabled, do nothing.
        if (!show()) {
          return;
        }

        // Find and annotate the locations of tabs.
        const split = line.text.split('\t');
        if (!split) {
          return;
        }
        for (let i = 0, pos = 0; i < split.length - 1; i++) {
          // Skip forward by the length of the content
          pos += split[i].length;

          GrAnnotation.annotateElement(
            contentEl,
            pos,
            1,
            'style-scope gr-diff tab-indicator'
          );

          // Skip forward by one tab character.
          pos++;
        }
      },
    };
  }

  _createSpecialCharacterIndicatorLayer(): DiffLayer {
    return {
      annotate(contentEl: HTMLElement, _: HTMLElement, line: GrDiffLine) {
        // Find and annotate the locations of soft hyphen.
        const split = line.text.split('\u00AD'); // \u00AD soft hyphen
        if (!split || split.length < 2) {
          return;
        }
        for (let i = 0, pos = 0; i < split.length - 1; i++) {
          // Skip forward by the length of the content
          pos += split[i].length;

          GrAnnotation.annotateElement(
            contentEl,
            pos,
            1,
            'style-scope gr-diff special-char-indicator'
          );

          pos++;
        }
      },
    };
  }

  _createTrailingWhitespaceLayer(): DiffLayer {
    const show = () => this._showTrailingWhitespace;

    return {
      annotate(contentEl: HTMLElement, _: HTMLElement, line: GrDiffLine) {
        if (!show()) {
          return;
        }

        const match = line.text.match(TRAILING_WHITESPACE_PATTERN);
        if (match) {
          // Normalize string positions in case there is unicode before or
          // within the match.
          const index = GrAnnotation.getStringLength(
            line.text.substr(0, match.index)
          );
          const length = GrAnnotation.getStringLength(match[0]);
          GrAnnotation.annotateElement(
            contentEl,
            index,
            length,
            'style-scope gr-diff trailing-whitespace'
          );
        }
      },
    };
  }

  setBlame(blame: BlameInfo[] | null) {
    if (!this._builder) return;
    this._builder.setBlame(blame);
  }

  updateRenderPrefs(renderPrefs: RenderPreferences) {
    this._builder?.updateRenderPrefs(renderPrefs);
    this.$.processor.updateRenderPrefs(renderPrefs);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-builder': GrDiffBuilderElement;
  }
}
