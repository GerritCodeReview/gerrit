/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {Observable, combineLatest, from} from 'rxjs';
import {debounceTime, filter, switchMap, withLatestFrom} from 'rxjs/operators';
import {
  DiffInfo,
  DiffPreferencesInfo,
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
  computeContext,
  computeKeyLocations,
  computeLineLength,
} from '../gr-diff/gr-diff-utils';
import {
  GrDiffProcessor,
  KeyLocations,
  ProcessingOptions,
} from '../gr-diff-processor/gr-diff-processor';
import {GrDiffGroup} from '../gr-diff/gr-diff-group';
import {createDefaultDiffPrefs} from '../../../constants/constants';
import {fire} from '../../../utils/event-util';
import {CreateCommentEventDetail} from '../gr-diff/gr-diff';
import {CommentRange} from '../../../api/rest-api';
import {assertIsDefined} from '../../../utils/common-util';

export interface DiffState {
  diff?: DiffInfo;
  path?: string;
  renderPrefs: RenderPreferences;
  diffPrefs: DiffPreferencesInfo;
  lineOfInterest?: DisplayLine;
  comments: GrDiffCommentThread[];
  groups: GrDiffGroup[];
  /** how much context to show for large files */
  showFullContext: FullContext;
  isImageDiff: boolean;
}

export const diffModelToken = define<DiffModel>('diff-model');

export class DiffModel extends Model<DiffState> {
  readonly diff$: Observable<DiffInfo> = select(
    this.state$.pipe(filter(state => state.diff !== undefined)),
    diffState => diffState.diff!
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

  readonly context$: Observable<number> = select(this.state$, state =>
    computeContext(
      state.diffPrefs.context,
      state.showFullContext,
      createDefaultDiffPrefs().context
    )
  );

  readonly lineOfInterest$: Observable<DisplayLine | undefined> = select(
    this.state$,
    diffState => diffState.lineOfInterest
  );

  readonly isImageDiff$: Observable<boolean> = select(
    this.state$,
    diffState => diffState.isImageDiff
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

  readonly lineLength$: Observable<number> = select(this.state$, state =>
    computeLineLength(state.diffPrefs, state.path)
  );

  readonly keyLocations$: Observable<KeyLocations> = select(
    combineLatest([this.lineOfInterest$, this.comments$]),
    ([lineOfInterest, comments]) =>
      computeKeyLocations(lineOfInterest, comments)
  );

  constructor() {
    super({
      diffPrefs: createDefaultDiffPrefs(),
      renderPrefs: {},
      comments: [],
      groups: [],
      showFullContext: FullContext.UNDECIDED,
      isImageDiff: false,
    });
    this.subscriptions = [this.processDiff()];
  }

  processDiff() {
    return combineLatest([
      this.diff$,
      this.context$,
      this.renderPrefs$,
      this.isImageDiff$,
    ])
      .pipe(
        withLatestFrom(this.keyLocations$),
        debounceTime(1),
        switchMap(
          ([[diff, context, renderPrefs, isImageDiff], keyLocations]) => {
            const processor = new GrDiffProcessor();
            const options: ProcessingOptions = {
              context,
              keyLocations,
              isBinary: !!(isImageDiff || diff.binary),
            };
            if (renderPrefs?.num_lines_rendered_at_once) {
              options.asyncThreshold = renderPrefs.num_lines_rendered_at_once;
            }
            return from(processor.process(diff.content, undefined, options));
          }
        )
      )
      .subscribe(groups => {
        this.updateState({groups});
      });
  }

  replaceGroup(contextControl: GrDiffGroup, newGroups: readonly GrDiffGroup[]) {
    const groups = [...this.getState().groups];
    const i = groups.indexOf(contextControl);
    if (i === -1) throw new Error('cannot find context control group');
    groups.splice(i, 1, ...newGroups);
    this.updateState({groups});
  }

  selectLine(target: EventTarget, number: LineNumber, side: Side) {
    const path = this.getState().path;
    if (!path) return;

    const detail: LineSelectedEventDetail = {number, side, path};
    fire(target, 'line-selected', detail);
  }

  createComment(
    target: EventTarget,
    lineNum: LineNumber,
    side: Side,
    range?: CommentRange
  ) {
    const path = this.getState().path;
    assertIsDefined(path, 'path');
    const detail: CreateCommentEventDetail = {
      path,
      side,
      lineNum,
      range,
    };
    fire(target, 'create-comment', detail);
  }
}
