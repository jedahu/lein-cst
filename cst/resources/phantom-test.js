var system = require('system');
var fs = require('fs');

var url = system.args[1];

var page = require('webpage').create();

page.onConsoleMessage = function(msg) {
  console.log(msg);
};

page.onError = function(msg, trace) {
  console.log(msg);
  trace.forEach(function(item) {
    console.log('   ', item.file, ':', item.line);
  });
};

page.open(url, function(status) {
  if (status === 'fail') {
    console.log('Failed to load ' + url);
    phantom.exit(1);
  } else {
    setInterval(function() {
      var fail_count = page.evaluate(function() {
        return document.body.getAttribute('data-menodora-final-fail-count');
      });
      if (fail_count) {
        phantom.exit(parseInt(fail_count));
      }
    }, 1000);
  }
}); 
