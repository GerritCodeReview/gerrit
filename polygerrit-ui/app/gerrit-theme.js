(() => {
  'use strict';

  Gerrit.install(plugin => {
    const css = `
      html {
        --header-background-color: #34adbe;
        --header-text-color: #fff;
        --header-title-content: "Gerrit";
        --header-icon: url("/static/diffy.png");
        --header-icon-size: 1em;
      }
      html.lightTheme {
        --header-background-color: #54cdde;
        --header-title-content: "Lerrit";
        --header-text-color: green;
      }
      html.darkTheme {
        --header-background-color: #148d9e;
        --header-title-content: "Derrit";
        --header-text-color: red;
      }
    `;
    plugin.styleApi().addStyle(css);
  });
})();
