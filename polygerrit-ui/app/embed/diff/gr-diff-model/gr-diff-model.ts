/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable, combineLatest} from 'rxjs';
import {debounceTime, filter, map, withLatestFrom} from 'rxjs/operators';
import {
  CreateCommentEventDetail,
  DiffInfo,
  DiffLayer,
  DiffPreferencesInfo,
  DiffRangesToFocus,
  DiffResponsiveMode,
  DiffViewMode,
  DisplayLine,
  LineNumber,
  LineSelectedEventDetail,
  RangeSelectedEventDetail,
  RenderPreferences,
  Side,
  SyntaxBlock,
} from '../../../api/diff';
import {define} from '../../../models/dependency';
import {Model} from '../../../models/base/model';
import {select} from '../../../utils/observable-util';
import {
  FullContext,
  GrDiffCommentThread,
  KeyLocations,
  computeContext,
  computeKeyLocations,
  computeLineLength,
  getResponsiveMode,
} from '../gr-diff/gr-diff-utils';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {
  GrDiffProcessor,
  ProcessingOptions,
} from '../gr-diff-processor/gr-diff-processor';
import {GrDiffGroup, GrDiffGroupType} from '../gr-diff/gr-diff-group';
import {assert} from '../../../utils/common-util';
import {countLines, isImageDiff} from '../../../utils/diff-util';
import {BlameInfo, ImageInfo} from '../../../types/common';
import {fire} from '../../../utils/event-util';
import {CommentRange} from '../../../api/rest-api';

export interface DiffState {
  diff?: DiffInfo;
  baseImage?: ImageInfo;
  revisionImage?: ImageInfo;
  path?: string;
  renderPrefs: RenderPreferences;
  diffPrefs: DiffPreferencesInfo;
  lineOfInterest?: DisplayLine;
  diffRangesToFocus?: DiffRangesToFocus;
  comments: GrDiffCommentThread[];
  groups: GrDiffGroup[];
  /** how much context to show for large files */
  showFullContext: FullContext;
  errorMessage?: string;
  layers: DiffLayer[];
  blameInfo: BlameInfo[];
  actionHoverCardText?: string;
}

export interface ColumnsToShow {
  blame: boolean;
  leftNumber: boolean;
  leftSign: boolean;
  leftContent: boolean;
  rightNumber: boolean;
  rightSign: boolean;
  rightContent: boolean;
}

export const NO_COLUMNS: ColumnsToShow = {
  blame: false,
  leftNumber: false,
  leftSign: false,
  leftContent: false,
  rightNumber: false,
  rightSign: false,
  rightContent: false,
};

export const diffModelToken = define<DiffModel>('diff-model');

export class DiffModel extends Model<DiffState> {
  readonly diff$: Observable<DiffInfo> = select(
    this.state$.pipe(filter(state => state.diff !== undefined)),
    diffState => diffState.diff!
  );

  readonly syntaxTreeRight$: Observable<SyntaxBlock[] | undefined> = select(
    this.diff$,
    diff => diff.meta_b?.syntax_tree
  );

  readonly lineCountLeft$: Observable<number> = select(this.diff$, diff =>
    countLines(diff, Side.LEFT)
  );

  readonly baseImage$: Observable<ImageInfo | undefined> = select(
    this.state$,
    diffState => diffState.baseImage
  );

  readonly revisionImage$: Observable<ImageInfo | undefined> = select(
    this.state$,
    diffState => diffState.revisionImage
  );

  readonly path$: Observable<string | undefined> = select(
    this.state$,
    diffState => diffState.path
  );

  readonly blameInfo$: Observable<BlameInfo[]> = select(
    this.state$,
    diffState => diffState.blameInfo
  );

  readonly renderPrefs$: Observable<RenderPreferences> = select(
    this.state$,
    diffState => diffState.renderPrefs
  );

  readonly viewMode$: Observable<DiffViewMode> = select(
    this.renderPrefs$,
    renderPrefs => renderPrefs.view_mode ?? DiffViewMode.SIDE_BY_SIDE
  );

  readonly columnsToShow$: Observable<ColumnsToShow> = select(
    combineLatest([this.blameInfo$, this.renderPrefs$]),
    ([blameInfo, renderPrefs]) => {
      const hideLeft = !!renderPrefs.hide_left_side;
      const showSign = !!renderPrefs.show_sign_col;
      const unified = renderPrefs.view_mode === DiffViewMode.UNIFIED;

      return {
        blame: blameInfo.length > 0,
        // Hiding the left side in unified diff mode does not make a lot of sense and is not supported.
        leftNumber: !hideLeft || unified,
        leftSign: !hideLeft && showSign && !unified,
        leftContent: !hideLeft && !unified,
        rightNumber: true,
        rightSign: showSign && !unified,
        rightContent: true,
      };
    }
  );

  readonly columnCount$: Observable<number> = select(
    this.columnsToShow$,
    columnsToShow => Object.values(columnsToShow).filter(s => s).length
  );

  readonly diffPrefs$: Observable<DiffPreferencesInfo> = select(
    this.state$,
    diffState => diffState.diffPrefs
  );

  readonly layers$: Observable<DiffLayer[]> = select(
    this.state$,
    diffState => diffState.layers
  );

  readonly showFullContext$: Observable<FullContext> = select(
    this.state$,
    diffState => diffState.showFullContext
  );

  readonly actionHoverCardText$: Observable<string | undefined> = select(
    this.state$,
    diffState => diffState.actionHoverCardText
  );

  readonly context$: Observable<number> = select(this.state$, state =>
    computeContext(
      state.diffPrefs.context,
      state.showFullContext,
      createDefaultDiffPrefs().context
    )
  );

  readonly responsiveMode$: Observable<DiffResponsiveMode> = select(
    this.state$,
    diffState => getResponsiveMode(diffState.diffPrefs, diffState.renderPrefs)
  );

  readonly errorMessage$: Observable<string | undefined> = select(
    this.state$,
    diffState => diffState.errorMessage
  );

  readonly comments$: Observable<GrDiffCommentThread[]> = select(
    this.state$,
    diffState => diffState.comments ?? []
  );

  readonly groups$: Observable<GrDiffGroup[]> = select(
    this.state$,
    diffState => diffState.groups ?? []
  );

  readonly loading$: Observable<boolean> = select(
    this.state$,
    diffState =>
      (diffState.groups ?? []).length === 0 || diffState.diff === undefined
  );

  readonly lineLength$: Observable<number> = select(this.state$, state =>
    computeLineLength(state.diffPrefs, state.path)
  );

  readonly keyLocations$: Observable<KeyLocations> = select(
    this.state$,
    diffState =>
      computeKeyLocations(diffState.lineOfInterest, diffState.comments ?? [])
  );

  readonly diffRangesToFocus$: Observable<DiffRangesToFocus | undefined> =
    select(this.state$, diffState => diffState.diffRangesToFocus);

  constructor(
    /**
     * Normally a reference to the <gr-diff> component. Used for firing events
     * that are meant for <gr-diff> or the host of <gr-diff>. For tests this
     * can also be just `document`.
     */
    private readonly eventTarget: EventTarget
  ) {
    super({
      diffPrefs: createDefaultDiffPrefs(),
      renderPrefs: {},
      comments: [],
      groups: [],
      showFullContext: FullContext.UNDECIDED,
      layers: [],
      blameInfo: [],
    });
    this.subscriptions = [this.processDiff()];
  }

  processDiff() {
    return combineLatest([
      this.diff$,
      this.context$,
      this.renderPrefs$,
      this.diffRangesToFocus$,
    ])
      .pipe(
        withLatestFrom(this.keyLocations$),
        debounceTime(1),
        map(
          ([[diff, context, renderPrefs, diffRangesToFocus], keyLocations]) => {
            const options: ProcessingOptions = {
              context,
              keyLocations,
              isBinary: !!(isImageDiff(diff) || diff.binary),
              diffRangesToFocus,
            };
            if (renderPrefs?.num_lines_rendered_at_once) {
              options.asyncThreshold = renderPrefs.num_lines_rendered_at_once;
            }

            const processor = new GrDiffProcessor(options);
            return processor.process(diff.content);
          }
        )
      )
      .subscribe(groups => {
        this.updateState({groups});
      });
  }

  /**
   * Replace a context control group with some expanded groups. Happens when the
   * user clicks "+10" or something similar.
   */
  replaceGroup(group: GrDiffGroup, newGroups: readonly GrDiffGroup[]) {
    assert(
      group.type === GrDiffGroupType.CONTEXT_CONTROL,
      'gr-diff can only replace context control groups'
    );
    const groups = [...this.getState().groups];
    const i = groups.indexOf(group);
    if (i === -1) throw new Error('cannot find context control group');
    groups.splice(i, 1, ...newGroups);
    this.updateState({groups});
  }

  selectLine(number: LineNumber, side: Side) {
    const path = this.getState().path;
    if (!path) return;

    const detail: LineSelectedEventDetail = {number, side, path};
    fire(this.eventTarget, 'line-selected', detail);
  }

  createCommentOnLine(lineNum: LineNumber, side: Side) {
    const detail: CreateCommentEventDetail = {
      side,
      lineNum,
      range: undefined,
    };
    fire(this.eventTarget, 'create-comment', detail);
  }

  createCommentOnRange(range: CommentRange, side: Side) {
    const detail: CreateCommentEventDetail = {
      side,
      lineNum: range.end_line,
      range,
    };
    fire(this.eventTarget, 'create-comment', detail);
  }

  fireRangeSelectedEvent(side: Side, range: CommentRange, isMouseUp: boolean) {
    const detail: RangeSelectedEventDetail = {
      side,
      lineNum: range.end_line,
      range,
      isMouseUp,
    };
    fire(this.eventTarget, 'range-selected', detail);
  }
}
