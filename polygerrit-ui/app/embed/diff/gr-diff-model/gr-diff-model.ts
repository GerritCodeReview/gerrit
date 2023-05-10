/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable, combineLatest, from} from 'rxjs';
import {debounceTime, filter, switchMap, withLatestFrom} from 'rxjs/operators';
import {
  CreateCommentEventDetail,
  DiffInfo,
  DiffLayer,
  DiffPreferencesInfo,
  DiffResponsiveMode,
  DiffViewMode,
  DisplayLine,
  LineNumber,
  LineSelectedEventDetail,
  RenderPreferences,
  Side,
} from '../../../api/diff';
import {define} from '../../../models/dependency';
import {Model} from '../../../models/model';
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
import {isImageDiff} from '../../../utils/diff-util';
import {ImageInfo} from '../../../types/common';
import {fire} from '../../../utils/event-util';

export interface DiffState {
  diff?: DiffInfo;
  baseImage?: ImageInfo;
  revisionImage?: ImageInfo;
  path?: string;
  renderPrefs: RenderPreferences;
  diffPrefs: DiffPreferencesInfo;
  lineOfInterest?: DisplayLine;
  comments: GrDiffCommentThread[];
  groups: GrDiffGroup[];
  /** how much context to show for large files */
  showFullContext: FullContext;
  isImageDiff: boolean;
  errorMessage?: string;
  layers: DiffLayer[];
}

export const diffModelToken = define<DiffModel>('diff-model');

export class DiffModel extends Model<DiffState> {
  readonly diff$: Observable<DiffInfo> = select(
    this.state$.pipe(filter(state => state.diff !== undefined)),
    diffState => diffState.diff!
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

  readonly renderPrefs$: Observable<RenderPreferences> = select(
    this.state$,
    diffState => diffState.renderPrefs
  );

  readonly viewMode$: Observable<DiffViewMode> = select(
    this.renderPrefs$,
    renderPrefs => renderPrefs.view_mode ?? DiffViewMode.SIDE_BY_SIDE
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

  readonly commentsPerLine = (
    side: Side,
    line: LineNumber
  ): Observable<GrDiffCommentThread[]> =>
    select(this.comments$, comments =>
      comments.filter(c => c.line === line && c.side === side)
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
      isImageDiff: false,
      layers: [],
    });
    this.subscriptions = [this.processDiff()];
  }

  processDiff() {
    return combineLatest([this.diff$, this.context$, this.renderPrefs$])
      .pipe(
        withLatestFrom(this.keyLocations$),
        debounceTime(1),
        switchMap(([[diff, context, renderPrefs], keyLocations]) => {
          const options: ProcessingOptions = {
            context,
            keyLocations,
            isBinary: !!(isImageDiff(diff) || diff.binary),
          };
          if (renderPrefs?.num_lines_rendered_at_once) {
            options.asyncThreshold = renderPrefs.num_lines_rendered_at_once;
          }
          const processor = new GrDiffProcessor(options);
          return from(processor.process(diff.content));
        })
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

  createComment(lineNum: LineNumber, side: Side) {
    const detail: CreateCommentEventDetail = {
      side,
      lineNum,
      range: undefined,
    };
    fire(this.eventTarget, 'create-comment', detail);
  }
}
