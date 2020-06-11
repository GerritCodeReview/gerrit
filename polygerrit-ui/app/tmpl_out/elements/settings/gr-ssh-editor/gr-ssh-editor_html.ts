import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrSshEditor} from '../../../../elements/settings/gr-ssh-editor/gr-ssh-editor';

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

export class GrSshEditorCheck extends GrSshEditor
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `gr-form-styles`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
      el.setAttribute('id', `existing`);
    }
    {
      const el: HTMLElementTagNameMap['table'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['thead'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `commentColumn`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `statusHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `keyHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tbody'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const key of this._keys!)
      {
        {
          const el: HTMLElementTagNameMap['tr'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `commentColumn`);
        }
        setTextContent(`${__f(key)!.comment}`);

        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
        }
        setTextContent(`${this._getStatusLabel(__f(key)!.valid)}`);

        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['gr-button'] = null!;
          useVars(el);
          el.link = true;
          el.addEventListener('click', e => this._showKey.bind(this, wrapInPolymerDomRepeatEvent(e, key))());
          el.setAttribute('dataIndex', `${index}`);
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['gr-copy-clipboard'] = null!;
          useVars(el);
          el.text = __f(key)!.ssh_public_key;
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['gr-button'] = null!;
          useVars(el);
          el.link = true;
          el.setAttribute('dataIndex', `${index}`);
          el.addEventListener('click', e => this._handleDeleteKey.bind(this, wrapInPolymerDomRepeatEvent(e, key))());
        }
      }
    }
    {
      const el: HTMLElementTagNameMap['gr-overlay'] = null!;
      useVars(el);
      el.setAttribute('id', `viewKeyOverlay`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    setTextContent(`${__f(this._keyToView)!.algorithm}`);

    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value publicKey`);
    }
    setTextContent(`${__f(this._keyToView)!.encoded_key}`);

    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    setTextContent(`${__f(this._keyToView)!.comment}`);

    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('class', `closeButton`);
      el.addEventListener('click', this._closeOverlay.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.addEventListener('click', this.save.bind(this));
      el.setAttribute('disabled', `${!this.hasUnsavedChanges}`);
    }
    {
      const el: HTMLElementTagNameMap['fieldset'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['section'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `title`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `value`);
    }
    {
      const el: HTMLElementTagNameMap['iron-autogrow-textarea'] = null!;
      useVars(el);
      el.setAttribute('id', `newKey`);
      el.bindValue = this._newKey;
      this._newKey = el.bindValue;
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `addButton`);
      el.link = true;
      el.setAttribute('disabled', `${this._computeAddButtonDisabled(this._newKey)}`);
      el.addEventListener('click', this._handleAddKey.bind(this));
    }
  }
}

