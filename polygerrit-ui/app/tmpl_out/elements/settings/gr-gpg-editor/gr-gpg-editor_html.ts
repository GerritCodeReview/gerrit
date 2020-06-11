import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrGpgEditor} from '../../../../elements/settings/gr-gpg-editor/gr-gpg-editor';

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

export class GrGpgEditorCheck extends GrGpgEditor
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
      el.setAttribute('class', `idColumn`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `fingerPrintColumn`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `userIdHeader`);
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
          el.setAttribute('class', `idColumn`);
        }
        setTextContent(`${__f(key)!.id}`);

        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `fingerPrintColumn`);
        }
        setTextContent(`${__f(key)!.fingerprint}`);

        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `userIdHeader`);
        }
        {
          const el: HTMLElementTagNameMap['dom-repeat'] = null!;
          useVars(el);
        }
        {
          const index = 0;
          const itemsIndexAs = 0;
          useVars(index, itemsIndexAs);
          for(const item of __f(key)!.user_ids!)
          {
            setTextContent(`
                  ${item}
                `);

          }
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `keyHeader`);
        }
        {
          const el: HTMLElementTagNameMap['gr-button'] = null!;
          useVars(el);
          el.addEventListener('click', e => this._showKey.bind(this, wrapInPolymerDomRepeatEvent(e, key))());
          el.setAttribute('dataIndex', `${index}`);
          el.link = true;
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['gr-copy-clipboard'] = null!;
          useVars(el);
          el.text = __f(key)!.key;
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['gr-button'] = null!;
          useVars(el);
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
    setTextContent(`${__f(this._keyToView)!.status}`);

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
    setTextContent(`${__f(this._keyToView)!.key}`);

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
      el.setAttribute('disabled', `${this._computeAddButtonDisabled(this._newKey)}`);
      el.addEventListener('click', this._handleAddKey.bind(this));
    }
  }
}

