const fs = require('fs');

fs.readdir('./polygerrit-ui/temp/behaviors/', (err, data) => {
  const behaviors = data;
  const externs = [];

  for (const behavior of behaviors) {
    externs.push({
      path: `./polygerrit-ui/temp/behaviors/${behavior}`,
      src: fs.readFileSync(
          `./polygerrit-ui/temp/behaviors/${behavior}`, 'utf-8'),
    });
  }

  const mappings = JSON.parse(fs.readFileSync(
      `./polygerrit-ui/temp/map.json`, 'utf-8'));

  externs.push({
    path: 'custom-externs.js',
    src: `/** @externs */ var page; var Gerrit; var GrRangeNormalizer;
    var GrAnnotation; var GrEtagDecorator; var GrGapiAuth; var GrGerritAuth;
    var GrReviewerUpdatesParser; var GrDiffBuilderUnified; var GrDiffBuilder;
    var GrDiffBuilderSideBySide; var GrDiffBuilderImage; var GrDiffLine;
    var GrThemeApi; var GrDomHooks; var GrChangeActionsInterface;
    var GrDiffGroup;
    var GrChangeReplyInterface; var GrPluginEndpoints; var GrLinkTextParser;
    var GrReporting; var GrAttributeHelper; var util; var moment;`,
  });

  for (key of Object.keys(mappings)) {
    if (mappings[key].html && mappings[key].js) {
      require('fried-twinkie').checkTemplate(
          mappings[key].html,
          mappings[key].js,
          'polygerrit.' + mappings[key].package,
          externs
      );
    }
  }
});
