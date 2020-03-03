<dom-module id="lgtm-plugin">
</dom-module>

<script>
  Gerrit.install(plugin => {
    const replyApi = plugin.changeReply();
    replyApi.addReplyTextChangedCallback(text => {
      const label = 'Code-Review';
      const labelValue = replyApi.getLabelValue(label);
      if (labelValue &&
          labelValue === ' 0' &&
          text.indexOf('LGTM') === 0) {
        replyApi.setLabelValue(label, '+1');
      }
    });
  });
</script>

