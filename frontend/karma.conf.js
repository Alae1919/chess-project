// Angular's default Karma setup, with longer timeouts for slow machines.
//
// The scene specs build real WebGL scenes: on a CI runner without a GPU (software rendering, one or two cores) a single
// scene takes seconds of unbroken work on the page's thread. Karma talks to the browser over a socket that the page
// answers from that same thread, and its default of 5 s of silence is shorter than that: the browser is declared
// disconnected in the middle of the run ("Disconnected, reconnect failed before timeout of 2000ms (ping timeout)").
module.exports = function (config) {
  config.set({
    basePath: '',
    frameworks: ['jasmine', '@angular-devkit/build-angular'],
    plugins: [
      require('karma-jasmine'),
      require('karma-chrome-launcher'),
      require('karma-jasmine-html-reporter'),
      require('karma-coverage'),
      require('@angular-devkit/build-angular/plugins/karma'),
    ],
    client: {
      jasmine: {},
      clearContext: false, // leave the Jasmine Spec Runner output visible in the browser
    },
    jasmineHtmlReporter: {
      suppressAll: true, // removes the duplicated traces
    },
    coverageReporter: {
      dir: require('path').join(__dirname, './coverage/frontend'),
      subdir: '.',
      reporters: [{ type: 'html' }, { type: 'text-summary' }],
    },
    reporters: ['progress', 'kjhtml'],
    browsers: ['Chrome'],
    restartOnFileChange: true,
    pingTimeout: 60000, // how long the page may stay silent (default 5000)
    browserDisconnectTimeout: 30000, // how long a dropped browser has to come back (default 2000)
    browserNoActivityTimeout: 120000, // how long it may go without reporting a result (default 30000)
  });
};
