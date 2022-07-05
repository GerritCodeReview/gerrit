import {define} from '../../models/dependency';
import {Model} from '../../models/model';

export interface AriaGridCurrentFocus {
  row: number;
  column: number;
}
export interface AriaGridState {
  currentFocus?: AriaGridCurrentFocus;
  totalRows: number;
  totalColumns: number;
}

export const ariaGridModelToken = define<AriaGridModel>('AriaGridModel');
export class AriaGridModel extends Model<AriaGridState> {
  constructor() {
    super({totalRows: 0, totalColumns: 0});
  }

  setGridSize(totalRows: number, totalColumns: number) {
    this.update(state => ({...state, totalRows, totalColumns}));
  }

  setFocused(row: number, column: number) {
    this.update(state => ({...state, currentFocus: {row, column}}));
  }

  moveLeft() {
    this.update(state => ({
      ...state,
      currentFocus: state.currentFocus
        ? {
            row: state.currentFocus.row,
            column: Math.max(state.currentFocus.column - 1, 0),
          }
        : {row: 0, column: 0},
    }));
  }

  moveRight() {
    this.update(state => ({
      ...state,
      currentFocus: state.currentFocus
        ? {
            row: state.currentFocus.row,
            column: Math.min(
              state.currentFocus.column + 1,
              state.totalColumns - 1
            ),
          }
        : {row: 0, column: 0},
    }));
  }

  moveUp() {
    this.update(state => ({
      ...state,
      currentFocus: state.currentFocus
        ? {
            row: Math.max(state.currentFocus.row - 1, 0),
            column: state.currentFocus.column,
          }
        : {row: 0, column: 0},
    }));
  }

  moveDown() {
    this.update(state => ({
      ...state,
      currentFocus: state.currentFocus
        ? {
            column: state.currentFocus.column,
            row: Math.min(state.currentFocus.row + 1, state.totalRows - 1),
          }
        : {row: 0, column: 0},
    }));
  }

  private update(foo: (state: AriaGridState) => AriaGridState) {
    this.subject$.next(foo(this.subject$.getValue()));
  }
}
