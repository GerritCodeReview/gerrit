import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrEditControls} from '../../../../elements/edit/gr-edit-controls/gr-edit-controls';

export interface PolymerDomRepeatEventModel<T> {
  /**
   * The item corresponding to the element in the dom-repeat.
   */
  item: T;

  /**
   * The index of the element in the dom-repeat.
   */
  index: number;
  get: (name: string) => T;
  set: (name: string, val: T) => void;
}

declare function wrapInPolymerDomRepeatEvent<T, U>(event: T, item: U): T & {model: PolymerDomRepeatEventModel<U>};
declare function setTextContent(content: unknown): void;
declare function useVars(...args: unknown[]): void;

type UnionToIntersection<T> = (
  T extends any ? (v: T) => void : never
  ) extends (v: infer K) => void
  ? K
  : never;

type AddNonDefinedProperties<T, P> = {
  [K in keyof P]: K extends keyof T ? T[K] : undefined;
};

type FlatUnion<T, TIntersect> = T extends any
  ? AddNonDefinedProperties<T, TIntersect>
  : never;

type AllUndefined<T> = {
  [P in keyof T]: undefined;
}

type UnionToAllUndefined<T> = T extends any ? AllUndefined<T> : any

type Flat<T> = FlatUnion<T, UnionToIntersection<UnionToAllUndefined<T>>>;

declare function __f<T>(obj: T): Flat<NonNullable<T>>;

declare function pc<T>(obj: T): PolymerDeepPropertyChange<T, T>;

declare function convert<T, U extends T>(obj: T): U;

export class GrEditControlsCheck extends GrEditControls
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const action of this._actions!)
      {
        {
          const el: HTMLElementTagNameMap['gr-button'] = null!;
          useVars(el);
          el.setAttribute('id', `${__f(action)!.id}`);
          el.setAttribute('class', `${this._computeIsInvisible(__f(action)!.id, this.hiddenActions)}`);
          el.link = true;
          el.addEventListener('click', e => this._handleTap.bind(this, wrapInPolymerDomRepeatEvent(e, action))());
        }
        setTextContent(`${__f(action)!.label}`);

      }
    }
    {
      const el: HTMLElementTagNameMap['gr-overlay'] = null!;
      useVars(el);
      el.setAttribute('id', `overlay`);
    }
    {
      const el: HTMLElementTagNameMap['gr-dialog'] = null!;
      useVars(el);
      el.setAttribute('id', `openDialog`);
      el.setAttribute('class', `invisible dialog`);
      el.setAttribute('disabled', `${!this._isValidPath(this._path)}`);
      el.confirmLabel = `Confirm`;
      el.confirmOnEnter = true;
      el.addEventListener('confirm', this._handleOpenConfirm.bind(this));
      el.addEventListener('cancel', this._handleDialogCancel.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `header`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `main`);
    }
    {
      const el: HTMLElementTagNameMap['gr-autocomplete'] = null!;
      useVars(el);
      el.placeholder = `Enter an existing or new full file path.`;
      el.query = this._query;
      el.text = this._path;
      this._path = el.text;
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('id', `dragDropArea`);
      el.addEventListener('drop', this._handleDragAndDropUpload.bind(this));
      el.addEventListener('keypress', this._handleKeyPress.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['p'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['p'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['p'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `fileUploadInput`);
      el.addEventListener('change', this._handleFileUploadChanged.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['label'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `fileUploadBrowse`);
    }
    {
      const el: HTMLElementTagNameMap['gr-dialog'] = null!;
      useVars(el);
      el.setAttribute('id', `deleteDialog`);
      el.setAttribute('class', `invisible dialog`);
      el.setAttribute('disabled', `${!this._isValidPath(this._path)}`);
      el.confirmLabel = `Delete`;
      el.confirmOnEnter = true;
      el.addEventListener('confirm', this._handleDeleteConfirm.bind(this));
      el.addEventListener('cancel', this._handleDialogCancel.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `header`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `main`);
    }
    {
      const el: HTMLElementTagNameMap['gr-autocomplete'] = null!;
      useVars(el);
      el.placeholder = `Enter an existing full file path.`;
      el.query = this._query;
      el.text = this._path;
      this._path = el.text;
    }
    {
      const el: HTMLElementTagNameMap['gr-dialog'] = null!;
      useVars(el);
      el.setAttribute('id', `renameDialog`);
      el.setAttribute('class', `invisible dialog`);
      el.setAttribute('disabled', `${!this._computeRenameDisabled(this._path, this._newPath)}`);
      el.confirmLabel = `Rename`;
      el.confirmOnEnter = true;
      el.addEventListener('confirm', this._handleRenameConfirm.bind(this));
      el.addEventListener('cancel', this._handleDialogCancel.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `header`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `main`);
    }
    {
      const el: HTMLElementTagNameMap['gr-autocomplete'] = null!;
      useVars(el);
      el.placeholder = `Enter an existing full file path.`;
      el.query = this._query;
      el.text = this._path;
      this._path = el.text;
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.setAttribute('id', `newPathIronInput`);
      el.bindValue = this._newPath;
      this._newPath = convert(el.bindValue);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
      el.setAttribute('id', `newPathInput`);
    }
    {
      const el: HTMLElementTagNameMap['gr-dialog'] = null!;
      useVars(el);
      el.setAttribute('id', `restoreDialog`);
      el.setAttribute('class', `invisible dialog`);
      el.confirmLabel = `Restore`;
      el.confirmOnEnter = true;
      el.addEventListener('confirm', this._handleRestoreConfirm.bind(this));
      el.addEventListener('cancel', this._handleDialogCancel.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `header`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `main`);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.bindValue = this._path;
      this._path = convert(el.bindValue);
    }
    {
      const el: HTMLElementTagNameMap['input'] = null!;
      useVars(el);
    }
  }
}

