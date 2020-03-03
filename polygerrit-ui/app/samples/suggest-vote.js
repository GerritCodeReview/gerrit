<dom-module id="suggested-vote">
</dom-module>

<script>
  Gerrit.install(plugin => {
    const replyApi = plugin.changeReply();
    let wasSuggested = false;
    plugin.on('showchange', () => {
      wasSuggested = false;
    });
    const CODE_REVIEW = 'Code-Review';
    replyApi.addLabelValuesChangedCallback(({name, value}) => {
      if (wasSuggested && name === CODE_REVIEW) {
        replyApi.showMessage('');
        wasSuggested = false;
      } else if (replyApi.getLabelValue(CODE_REVIEW) === '+1' &&
          !wasSuggested) {
        replyApi.setLabelValue(CODE_REVIEW, '+2');
        replyApi.showMessage(`Suggested ${CODE_REVIEW} upgrade: +2`);
        wasSuggested = true;
      }
    });
  });
</script>
