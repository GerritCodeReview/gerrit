/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-diff-processor/gr-diff-processor';
import '../../../elements/shared/gr-hovercard/gr-hovercard';
import './gr-diff-builder-side-by-side';
import {GrAnnotation} from '../gr-diff-highlight/gr-annotation';
import {
  DiffBuilder,
  DiffBuilderImage,
  DiffContextExpandedEventDetail,
} from './gr-diff-builder';
import {GrDiffBuilderSideBySide} from './gr-diff-builder-side-by-side';
import {GrDiffBuilderImage} from './gr-diff-builder-image';
import {GrDiffBuilderUnified} from './gr-diff-builder-unified';
import {GrDiffBuilderBinary} from './gr-diff-builder-binary';
import {GrDiffBuilderLit} from './gr-diff-builder-lit';
import {CancelablePromise, makeCancelable} from '../../../utils/async-util';
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
import {DiffViewMode, RenderPreferences} from '../../../api/diff';
import {createDefaultDiffPrefs, Side} from '../../../constants/constants';
import {GrDiffLine, LineNumber} from '../gr-diff/gr-diff-line';
import {
  GrDiffGroup,
  GrDiffGroupType,
  hideInContextControl,
} from '../gr-diff/gr-diff-group';
import {getLineNumber, getSideByLineEl} from '../gr-diff/gr-diff-utils';
import {fireAlert, fireEvent} from '../../../utils/event-util';
import {assertIsDefined} from '../../../utils/common-util';

const TRAILING_WHITESPACE_PATTERN = /\s+$/;
const COMMIT_MSG_PATH = '/COMMIT_MSG';
const COMMIT_MSG_LINE_LENGTH = 72;

declare global {
  interface HTMLElementEventMap {
    /**
     * Fired when the diff begins rendering - both for full renders and for
     * partial rerenders.
     */
    'render-start': CustomEvent<{}>;
    /**
     * Fired when the diff finishes rendering text content - both for full
     * renders and for partial rerenders.
     */
    'render-content': CustomEvent<{}>;
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

    GrAnnotation.annotateElement(contentEl, pos, 1, `gr-diff ${className}`);

    pos++;
  }
}

// TODO: Rename the class and the file and remove "element". This is not an
// element anymore.
export class GrDiffBuilderElement implements GroupConsumer {
  diff?: DiffInfo;

  diffElement?: HTMLTableElement;

  viewMode?: string;

  isImageDiff?: boolean;

  baseImage: ImageInfo | null = null;

  revisionImage: ImageInfo | null = null;

  path?: string;

  prefs: DiffPreferencesInfo = createDefaultDiffPrefs();

  renderPrefs?: RenderPreferences;

  useNewImageDiffUi = false;

  /**
   * Layers passed in from the outside.
   *
   * See `layersInternal` for where these layers will end up together with the
   * internal layers.
   */
  layers: DiffLayer[] = [];

  // visible for testing
  builder?: DiffBuilder;

  /**
   * All layers, both from the outside and the default ones. See `layers` for
   * the property that can be set from the outside.
   */
  // visible for testing
  layersInternal: DiffLayer[] = [];

  // visible for testing
  showTabs?: boolean;

  // visible for testing
  showTrailingWhitespace?: boolean;

  /**
   * The promise last returned from `render()` while the asynchronous
   * rendering is running - `null` otherwise. Provides a `cancel()`
   * method that rejects it with `{isCancelled: true}`.
   */
  private cancelableRenderPromise: CancelablePromise<unknown> | null = null;

  private coverageLayerLeft = new GrCoverageLayer(Side.LEFT);

  private coverageLayerRight = new GrCoverageLayer(Side.RIGHT);

  private rangeLayer?: GrRangedCommentLayer;

  // visible for testing
  processor = new GrDiffProcessor();

  /**
   * Groups are mostly just passed on to the diff builder (this.builder). But
   * we also keep track of them here for being able to fire a `render-content`
   * event when .element of each group has rendered.
   *
   * TODO: Refactor DiffBuilderElement and DiffBuilders with a cleaner
   * separation of responsibilities.
   */
  private groups: GrDiffGroup[] = [];

  constructor() {
    this.processor.consumer = this;
  }

  updateCommentRanges(ranges: CommentRangeLayer[]) {
    this.rangeLayer?.updateRanges(ranges);
  }

  updateCoverageRanges(rs: CoverageRange[]) {
    this.coverageLayerLeft.setRanges(rs.filter(r => r?.side === Side.LEFT));
    this.coverageLayerRight.setRanges(rs.filter(r => r?.side === Side.RIGHT));
  }

  render(keyLocations: KeyLocations): Promise<void> {
    assertIsDefined(this.diff, 'diff');
    assertIsDefined(this.diffElement, 'diff table');

    // Setting up annotation layers must happen after plugins are
    // installed, and |render| satisfies the requirement, however,
    // |attached| doesn't because in the diff view page, the element is
    // attached before plugins are installed.
    this.setupAnnotationLayers();

    this.showTabs = this.prefs.show_tabs;
    this.showTrailingWhitespace = this.prefs.show_whitespace_errors;

    this.cleanup();
    this.builder = this.getDiffBuilder();
    this.init();

    this.processor.context = this.prefs.context;
    this.processor.keyLocations = keyLocations;

    this.clearDiffContent();
    this.builder.addColumns(
      this.diffElement,
      getLineNumberCellWidth(this.prefs)
    );

    const isBinary = !!(this.isImageDiff || this.diff.binary);

    this.fireDiffEvent('render-start');
    // TODO: processor.process() returns a cancelable promise already.
    // Why wrap another one around it?
    this.cancelableRenderPromise = makeCancelable(
      this.processor.process(this.diff.content, isBinary)
    );
    // All then/catch/finally clauses must be outside of makeCancelable().
    return (
      this.cancelableRenderPromise
        .then(async () => {
          if (this.isImageDiff) {
            (this.builder as unknown as DiffBuilderImage).renderImageDiff();
          }
          await this.untilGroupsRendered();
          this.fireDiffEvent('render-content');
        })
        // Mocha testing does not like uncaught rejections, so we catch
        // the cancels which are expected and should not throw errors in
        // tests.
        .catch(e => {
          if (!e.isCanceled) return Promise.reject(e);
          return;
        })
        .finally(() => {
          this.cancelableRenderPromise = null;
        })
    );
  }

  // visible for testing
  async untilGroupsRendered(groups: readonly GrDiffGroup[] = this.groups) {
    return Promise.all(groups.map(g => g.waitUntilRendered()));
  }

  private onDiffContextExpanded = (
    e: CustomEvent<DiffContextExpandedEventDetail>
  ) => {
    // Don't stop propagation. The host may listen for reporting or
    // resizing.
    this.replaceGroup(e.detail.contextGroup, e.detail.groups);
  };

  private fireDiffEvent<K extends keyof HTMLElementEventMap>(type: K) {
    assertIsDefined(this.diffElement, 'diff table');
    fireEvent(this.diffElement, type);
  }

  // visible for testing
  setupAnnotationLayers() {
    this.rangeLayer = new GrRangedCommentLayer();

    const layers: DiffLayer[] = [
      this.createTrailingWhitespaceLayer(),
      this.createIntralineLayer(),
      this.createTabIndicatorLayer(),
      this.createSpecialCharacterIndicatorLayer(),
      this.rangeLayer,
      this.coverageLayerLeft,
      this.coverageLayerRight,
    ];

    if (this.layers) {
      layers.push(...this.layers);
    }
    this.layersInternal = layers;
  }

  getContentTdByLine(lineNumber: LineNumber, side?: Side, root?: Element) {
    if (!this.builder) return null;
    return this.builder.getContentTdByLine(lineNumber, side, root);
  }

  private getDiffRowByChild(child: Element) {
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
    const row = this.getDiffRowByChild(lineEl);
    return this.getContentTdByLine(line, side, row);
  }

  getLineElByNumber(lineNumber: LineNumber, side?: Side) {
    if (!this.builder) return null;
    return this.builder.getLineElByNumber(lineNumber, side);
  }

  getLineNumberRows() {
    if (!this.builder) return [];
    return this.builder.getLineNumberRows();
  }

  getLineNumEls(side: Side) {
    if (!this.builder) return [];
    return this.builder.getLineNumEls(side);
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
    if (!this.builder) return;
    const group = this.builder.findGroup(side, lineNum);
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
    if (!this.builder) return;
    this.fireDiffEvent('render-start');
    this.builder.replaceGroup(contextGroup, newGroups);
    this.groups = this.groups.filter(g => g !== contextGroup);
    this.groups.push(...newGroups);
    this.untilGroupsRendered(newGroups).then(() => {
      this.fireDiffEvent('render-content');
    });
  }

  /**
   * This is meant to be called when the gr-diff component re-connects, or when
   * the diff is (re-)rendered.
   *
   * Make sure that this method is symmetric with cleanup(), which is called
   * when gr-diff disconnects.
   */
  init() {
    this.cleanup();
    this.diffElement?.addEventListener(
      'diff-context-expanded',
      this.onDiffContextExpanded
    );
    this.builder?.init();
  }

  /**
   * This is meant to be called when the gr-diff component disconnects, or when
   * the diff is (re-)rendered.
   *
   * Make sure that this method is symmetric with init(), which is called when
   * gr-diff re-connects.
   */
  cleanup() {
    this.processor.cancel();
    this.builder?.cleanup();
    this.cancelableRenderPromise?.cancel();
    this.cancelableRenderPromise = null;
    this.diffElement?.removeEventListener(
      'diff-context-expanded',
      this.onDiffContextExpanded
    );
  }

  // visible for testing
  handlePreferenceError(pref: string): never {
    const message =
      `The value of the '${pref}' user preference is ` +
      'invalid. Fix in diff preferences';
    assertIsDefined(this.diffElement, 'diff table');
    fireAlert(this.diffElement, message);
    throw Error(`Invalid preference value: ${pref}`);
  }

  // visible for testing
  getDiffBuilder(): DiffBuilder {
    assertIsDefined(this.diff, 'diff');
    assertIsDefined(this.diffElement, 'diff table');
    if (isNaN(this.prefs.tab_size) || this.prefs.tab_size <= 0) {
      this.handlePreferenceError('tab size');
    }

    if (isNaN(this.prefs.line_length) || this.prefs.line_length <= 0) {
      this.handlePreferenceError('diff width');
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
      const useLit = this.renderPrefs?.use_lit_components;
      if (useLit) {
        builder = new GrDiffBuilderLit(
          this.diff,
          localPrefs,
          this.diffElement,
          this.layersInternal,
          this.renderPrefs
        );
      } else {
        builder = new GrDiffBuilderSideBySide(
          this.diff,
          localPrefs,
          this.diffElement,
          this.layersInternal,
          this.renderPrefs
        );
      }
    } else if (this.viewMode === DiffViewMode.UNIFIED) {
      builder = new GrDiffBuilderUnified(
        this.diff,
        localPrefs,
        this.diffElement,
        this.layersInternal,
        this.renderPrefs
      );
    }
    if (!builder) {
      throw Error(`Unsupported diff view mode: ${this.viewMode}`);
    }
    return builder;
  }

  private clearDiffContent() {
    assertIsDefined(this.diffElement, 'diff table');
    this.diffElement.innerHTML = '';
  }

  /**
   * Called when the processor starts converting the diff information from the
   * server into chunks.
   */
  clearGroups() {
    if (!this.builder) return;
    this.groups = [];
    this.builder.clearGroups();
  }

  /**
   * Called when the processor is done converting a chunk of the diff.
   */
  addGroup(group: GrDiffGroup) {
    if (!this.builder) return;
    this.builder.addGroups([group]);
    this.groups.push(group);
  }

  // visible for testing
  createIntralineLayer(): DiffLayer {
    return {
      // Take a DIV.contentText element and a line object with intraline
      // differences to highlight and apply them to the element as
      // annotations.
      annotate(contentEl: HTMLElement, _: HTMLElement, line: GrDiffLine) {
        const HL_CLASS = 'gr-diff intraline';
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

  // visible for testing
  createTabIndicatorLayer(): DiffLayer {
    const show = () => this.showTabs;
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

  private createSpecialCharacterIndicatorLayer(): DiffLayer {
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

  // visible for testing
  createTrailingWhitespaceLayer(): DiffLayer {
    const show = () => this.showTrailingWhitespace;

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
            'gr-diff trailing-whitespace'
          );
        }
      },
    };
  }

  setBlame(blame: BlameInfo[] | null) {
    if (!this.builder) return;
    this.builder.setBlame(blame ?? []);
  }

  updateRenderPrefs(renderPrefs: RenderPreferences) {
    this.builder?.updateRenderPrefs(renderPrefs);
    this.processor.updateRenderPrefs(renderPrefs);
  }
}
