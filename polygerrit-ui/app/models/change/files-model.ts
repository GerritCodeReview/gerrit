/**
 * @license
 * Copyright 2022 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {FileInfo, FileNameToFileInfoMap} from '../../types/common';
import {combineLatest, Subscription, of, from} from 'rxjs';
import {switchMap, map} from 'rxjs/operators';
import {RestApiService} from '../../services/gr-rest-api/gr-rest-api';
import {Finalizable} from '../../services/registry';
import {select} from '../../utils/observable-util';
import {specialFilePathCompare} from '../../utils/path-list-util';
import {Model} from '../model';
import {define} from '../dependency';
import {ChangeModel} from './change-model';

export interface NormalizedFileInfo extends FileInfo {
  __path: string;
}

function mapToList(map?: FileNameToFileInfoMap): NormalizedFileInfo[] {
  const list: NormalizedFileInfo[] = [];
  for (const [key, value] of Object.entries(map ?? {})) {
    list.push({
      ...value,
      __path: key,
      lines_inserted: value.lines_inserted ?? 0,
      lines_deleted: value.lines_deleted ?? 0,
      size_delta: value.size_delta ?? 0,
    });
  }
  list.sort((f1, f2) => specialFilePathCompare(f1.__path, f2.__path));
  return list;
}

export interface FilesState {
  // TODO: Maybe a loading state??

  // TODO: Maybe move reviewed files from change model into here?

  files: NormalizedFileInfo[];
}

const initialState: FilesState = {
  files: [],
};

export const filesModelToken = define<FilesModel>('files-model');

export class FilesModel extends Model<FilesState> implements Finalizable {
  public readonly files$ = select(this.state$, state => state.files);

  private subscriptions: Subscription[] = [];

  constructor(
    readonly changeModel: ChangeModel,
    readonly restApiService: RestApiService
  ) {
    super(initialState);
    this.subscriptions = [
      combineLatest(
        this.changeModel.changeNum$,
        this.changeModel.basePatchNum$,
        this.changeModel.patchNum$
      )
        .pipe(
          switchMap(([changeNum, basePatchNum, patchNum]) => {
            if (!changeNum || !patchNum) return of({});
            return from(
              this.restApiService.getChangeOrEditFiles(changeNum, {
                basePatchNum,
                patchNum,
              })
            );
          }),
          map(mapToList)
        )
        .subscribe(files => {
          this.updateFiles(files);
        }),
    ];
  }

  finalize() {
    for (const s of this.subscriptions) {
      s.unsubscribe();
    }
    this.subscriptions = [];
  }

  // visible for testing
  updateFiles(files: NormalizedFileInfo[]) {
    const current = this.subject$.getValue();
    this.setState({
      ...current,
      files: [...files],
    });
  }

  // visible for testing
  setState(state: FilesState) {
    this.subject$.next(state);
  }
}
