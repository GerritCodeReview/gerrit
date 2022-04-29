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
import '../gr-diff-processor/gr-diff-processor';
import '../../../elements/shared/gr-hovercard/gr-hovercard';
import './gr-diff-builder-side-by-side';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-diff-builder-element_html';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import {DiffBuilder, DiffContextExpandedEventDetail} from './gr-diff-builder';
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
  GroupConsumer,
  KeyLocations,
} from '../gr-diff-processor/gr-diff-processor';
import {
  CommentRangeLayer,
  GrRangedCommentLayer,
} from '../gr-ranged-comment-layer/gr-ranged-comment-layer';
import {GrCoverageLayer} from '../gr-coverage-layer/gr-coverage-layer';
import {
  DiffViewMode,
  RenderPreferences,
  RenderProgressEventDetail,
} from '../../../api/diff';
import {createDefaultDiffPrefs, Side} from '../../../constants/constants';
import {GrDiffLine, LineNumber} from '../gr-diff/gr-diff-line';
import {
  GrDiffGroup,
  GrDiffGroupType,
  hideInContextControl,
} from '../gr-diff/gr-diff-group';
import {getLineNumber, getSideByLineEl} from '../gr-diff/gr-diff-utils';
import {fireAlert, fireEvent, fire} from '../../../utils/event-util';
import {afterNextRender} from '@polymer/polymer/lib/utils/render-status';

const TRAILING_WHITESPACE_PATTERN = /\s+$/;

// https://gerrit.googlesource.com/gerrit/+/234616a8627334686769f1de989d286039f4d6a5/polygerrit-ui/app/elements/diff/gr-diff/gr-diff.js#740
const COMMIT_MSG_PATH = '/COMMIT_MSG';
const COMMIT_MSG_LINE_LENGTH = 72;

declare global {
  interface HTMLElementEventMap {
    'render-progress': CustomEvent<RenderProgressEventDetail>;
  }
}

export function getLineNumberCellWidth(prefs: DiffPreferencesInfo) {
  return prefs.font_size * 4;
}

function annotateSymbols(
  contentEl: HTMLElement,
  line: GrDiffLine,
  separator: string | RegExp,
  className: string
) {
  const split = line.text.split(separator);
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
      `style-scope gr-diff ${className}`
    );

    pos++;
  }
}

@customElement('gr-diff-builder')
export class GrDiffBuilderElement
  extends PolymerElement
  implements GroupConsumer
{
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the diff begins rendering - both for full renders and for
   * partial rerenders.
   *
   * @event render-start
   */

  /**
   * Fired whenever a new chunk of lines has been rendered synchronously - this
   * only happens for full renders.
   *
   * @event render-progress
   */

  /**
   * Fired when the diff finishes rendering text content - both for full
   * renders and for partial rerenders.
   *
   * @event render-content
   */

  @property({type: Object})
  diff?: DiffInfo;

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
  prefs: DiffPreferencesInfo = createDefaultDiffPrefs();

  @property({type: Object})
  renderPrefs?: RenderPreferences;

  @property({type: Object})
  _builder?: DiffBuilder;

  /**
   * The gr-diff-processor adds (and only adds!) to this array. It does so by
   * using `this.push()` and Polymer's two-way data binding.
   * Below (@observe('_groups.splices')) we are observing the groups that the
   * processor adds, and pass them on to the builder for rendering. Henceforth
   * the builder groups are the source of truth, because when
   * expanding/collapsing groups only the builder is updated. This field and the
   * corresponsing one in the processor are not updated.
   */
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

  @property({type: Array, observer: 'coverageObserver'})
  coverageRanges: CoverageRange[] = [];

  @property({type: Boolean})
  useNewImageDiffUi = false;

  /**
   * The promise last returned from `render()` while the asynchronous
   * rendering is running - `null` otherwise. Provides a `cancel()`
   * method that rejects it with `{isCancelled: true}`.
   */
  @property({type: Object})
  _cancelableRenderPromise: CancelablePromise<unknown> | null = null;

  private coverageLayerLeft = new GrCoverageLayer(Side.LEFT);

  private coverageLayerRight = new GrCoverageLayer(Side.RIGHT);

  private rangeLayer = new GrRangedCommentLayer();

  private processor = new GrDiffProcessor();

  constructor() {
    super();
    afterNextRender(this, () => {
      this.addEventListener(
        'diff-context-expanded',
        (e: CustomEvent<DiffContextExpandedEventDetail>) => {
          // Don't stop propagation. The host may listen for reporting or
          // resizing.
          this.replaceGroup(e.detail.contextGroup, e.detail.groups);
        }
      );
    });
    this.processor.consumer = this;
  }

  override disconnectedCallback() {
    this.processor.cancel();
    if (this._builder) {
      this._builder.clear();
    }
    super.disconnectedCallback();
  }

  get diffElement(): HTMLTableElement {
    // Not searching in shadowRoot, because the diff table is slotted!
    return this.querySelector('#diffTable') as HTMLTableElement;
  }

  @observe('commentRanges.*')
  rangeObserver() {
    this.rangeLayer.updateRanges(this.commentRanges);
  }

  coverageObserver(coverageRanges: CoverageRange[]) {
    const leftRanges = coverageRanges.filter(
      range => range && range.side === Side.LEFT
    );
    this.coverageLayerLeft.setRanges(leftRanges);

    const rightRanges = coverageRanges.filter(
      range => range && range.side === Side.RIGHT
    );
    this.coverageLayerRight.setRanges(rightRanges);
  }

  render(keyLocations: KeyLocations): void {
    // Setting up annotation layers must happen after plugins are
    // installed, and |render| satisfies the requirement, however,
    // |attached| doesn't because in the diff view page, the element is
    // attached before plugins are installed.
    this._setupAnnotationLayers();

    this._showTabs = this.prefs.show_tabs;
    this._showTrailingWhitespace = this.prefs.show_whitespace_errors;

    // Stop the processor if it's running.
    this.cancel();

    if (this._builder) {
      this._builder.clear();
    }
    if (!this.diff) {
      throw Error('Cannot render a diff without DiffInfo.');
    }
    this._builder = this._getDiffBuilder();

    this.processor.context = this.prefs.context;
    this.processor.keyLocations = keyLocations;

    this._clearDiffContent();
    this._builder.addColumns(
      this.diffElement,
      getLineNumberCellWidth(this.prefs)
    );

    const isBinary = !!(this.isImageDiff || this.diff.binary);

    fireEvent(this, 'render-start');
    this._cancelableRenderPromise = util.makeCancelable(
      this.processor
        .process(this.diff.content, isBinary)
        .then(() => {
          if (this.isImageDiff) {
            (this._builder as GrDiffBuilderImage).renderDiff();
          }
          afterNextRender(this, () => fireEvent(this, 'render-content'));
        })
        // Mocha testing does not like uncaught rejections, so we catch
        // the cancels which are expected and should not throw errors in
        // tests.
        .catch(e => {
          if (!e.isCanceled) return Promise.reject(e);
          return;
        })
        .finally(() => {
          this._cancelableRenderPromise = null;
        })
    );
  }

  _setupAnnotationLayers() {
    const layers: DiffLayer[] = [
      this._createTrailingWhitespaceLayer(),
      this._createIntralineLayer(),
      this._createTabIndicatorLayer(),
      this._createSpecialCharacterIndicatorLayer(),
      this.rangeLayer,
      this.coverageLayerLeft,
      this.coverageLayerRight,
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
    if (!this._builder) return null;
    return this._builder.getLineElByNumber(lineNumber, side);
  }

  getLineNumberRows() {
    if (!this._builder) return [];
    return this._builder.getLineNumberRows();
  }

  getLineNumEls(side: Side) {
    if (!this._builder) return [];
    return this._builder.getLineNumEls(side);
  }

  /**
   * When the line is hidden behind a context expander, expand it.
   *
   * @param lineNum A line number to expand. Using number here because other
   *   special case line numbers are never hidden, so it does not make sense
   *   to expand them.
   * @param side The side the line number refer to.
   */
  unhideLine(lineNum: number, side: Side) {
    if (!this._builder) return;
    const group = this._builder.findGroup(side, lineNum);
    // Cannot unhide a line that is not part of the diff.
    if (!group) return;
    // If it's already visible, great!
    if (group.type !== GrDiffGroupType.CONTEXT_CONTROL) return;
    const lineRange = group.lineRange[side];
    const lineOffset = lineNum - lineRange.start_line;
    const newGroups = [];
    const groups = hideInContextControl(
      group.contextGroups,
      0,
      lineOffset - 1 - this.prefs.context
    );
    // If there is a context group, it will be the first group because we
    // start hiding from 0 offset
    if (groups[0].type === GrDiffGroupType.CONTEXT_CONTROL) {
      newGroups.push(groups.shift()!);
    }
    newGroups.push(
      ...hideInContextControl(
        groups,
        lineOffset + 1 + this.prefs.context,
        // Both ends inclusive, so difference is the offset of the last line.
        // But we need to pass the first line not to hide, which is the element
        // after.
        lineRange.end_line - lineRange.start_line + 1
      )
    );
    this.replaceGroup(group, newGroups);
  }

  /**
   * Replace the group of a context control section by rendering the provided
   * groups instead. This happens in response to expanding a context control
   * group.
   *
   * @param contextGroup The context control group to replace
   * @param newGroups The groups that are replacing the context control group
   */
  private replaceGroup(
    contextGroup: GrDiffGroup,
    newGroups: readonly GrDiffGroup[]
  ) {
    if (!this._builder) return;
    fireEvent(this, 'render-start');
    const linesRendered = newGroups.reduce(
      (sum, group) => sum + group.lines.length,
      0
    );
    this._builder.replaceGroup(contextGroup, newGroups);
    afterNextRender(this, () => {
      fire(this, 'render-progress', {linesRendered});
      fireEvent(this, 'render-content');
    });
  }

  cancel() {
    this.processor.cancel();
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

  _getDiffBuilder(): DiffBuilder {
    if (!this.diff) {
      throw Error('Cannot render a diff without DiffInfo.');
    }
    if (isNaN(this.prefs.tab_size) || this.prefs.tab_size <= 0) {
      this._handlePreferenceError('tab size');
    }

    if (isNaN(this.prefs.line_length) || this.prefs.line_length <= 0) {
      this._handlePreferenceError('diff width');
    }

    const localPrefs = {...this.prefs};
    if (this.path === COMMIT_MSG_PATH) {
      // override line_length for commit msg the same way as
      // in gr-diff
      localPrefs.line_length = COMMIT_MSG_LINE_LENGTH;
    }

    let builder = null;
    if (this.isImageDiff) {
      builder = new GrDiffBuilderImage(
        this.diff,
        localPrefs,
        this.diffElement,
        this.baseImage,
        this.revisionImage,
        this.renderPrefs,
        this.useNewImageDiffUi
      );
    } else if (this.diff.binary) {
      // If the diff is binary, but not an image.
      return new GrDiffBuilderBinary(this.diff, localPrefs, this.diffElement);
    } else if (this.viewMode === DiffViewMode.SIDE_BY_SIDE) {
      builder = new GrDiffBuilderSideBySide(
        this.diff,
        localPrefs,
        this.diffElement,
        this._layers,
        this.renderPrefs
      );
    } else if (this.viewMode === DiffViewMode.UNIFIED) {
      builder = new GrDiffBuilderUnified(
        this.diff,
        localPrefs,
        this.diffElement,
        this._layers,
        this.renderPrefs
      );
    }
    if (!builder) {
      throw Error(`Unsupported diff view mode: ${this.viewMode}`);
    }
    return builder;
  }

  _clearDiffContent() {
    this.diffElement.innerHTML = '';
  }

  /**
   * Called when the processor starts converting the diff information from the
   * server into chunks.
   */
  clearGroups() {
    if (!this._builder) return;
    this._builder.clearGroups();
  }

  /**
   * Called when the processor is done converting a chunk of the diff.
   */
  addGroup(group: GrDiffGroup) {
    if (!this._builder) return;
    this._builder.addGroups([group]);
    afterNextRender(this, () =>
      fire(this, 'render-progress', {linesRendered: group.lines.length})
    );
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
        annotateSymbols(contentEl, line, '\t', 'tab-indicator');
      },
    };
  }

  _createSpecialCharacterIndicatorLayer(): DiffLayer {
    return {
      annotate(contentEl: HTMLElement, _: HTMLElement, line: GrDiffLine) {
        // Find and annotate the locations of soft hyphen (\u00AD)
        annotateSymbols(contentEl, line, '\u00AD', 'special-char-indicator');
        // Find and annotate Stateful Unicode directional controls
        annotateSymbols(
          contentEl,
          line,
          /[\u202A-\u202E\u2066-\u2069]/,
          'special-char-warning'
        );
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
    this._builder.setBlame(blame ?? []);
  }

  updateRenderPrefs(renderPrefs: RenderPreferences) {
    this._builder?.updateRenderPrefs(renderPrefs);
    this.processor.updateRenderPrefs(renderPrefs);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-builder': GrDiffBuilderElement;
  }
}
