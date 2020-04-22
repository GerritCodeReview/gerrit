This folder contains shared components that can be used independently from Gerrit.

### gr-diff

`gr-diff.js` is the `gr-diff` component used in gerrit to render diff. If you want to use it, feel free to import it and use it in your project as:

```
<gr-diff></gr-diff>
```

All supported attributes defined in `polygerrit-ui/app/elements/diff/gr-diff/gr-diff.js`, you can pass them by just assigning them to the `gr-app` element.

To customize the style of the diff, you can use `css variables`, all supported varibled defined in `polygerrit-ui/app/styles/themes/app-theme.html` and `polygerrit-ui/app/styles/themes/dark-theme.html`.
