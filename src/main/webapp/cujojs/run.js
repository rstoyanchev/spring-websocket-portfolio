(function(curl) {

	var config = {

		baseUrl: '../assets',

		packages: [
			{ name: 'portfolio', location: '../cujojs', main: 'main' },

			// Add third-party packages here
			{ name: 'curl', location: 'lib/curl/src/curl' },
			{ name: 'msgs', location: 'lib/msgs', main: 'msgs' },
			{ name: 'poly', location: 'lib/poly' },
			{ name: 'knockout', location: 'lib/knockout', main: 'knockout' }
		],

		paths: {
			'common': 'common',
			'jquery': 'lib/jquery/jquery',
			'sockjs': 'lib/sockjs/sockjs',
			'bootstrap': {
				location: 'lib/bootstrap/js/bootstrap.js',
				config: {
					loader: 'curl/loader/legacy',
					exports: 'jQuery',
					requires: ['jquery']
				}
			}
		},

		// Polyfill everything ES5-ish
		preloads: ['poly/all']

	};

	curl(config, ['portfolio']).then(success, fail);

	// Success! curl.js indicates that your app loaded successfully!
	function success () {
		console.log('Application loaded');
	}

	// Oops. curl.js indicates that your app failed to load correctly.
	function fail (ex) {
		console.log('an error happened during loading :\'(');
		console.log(ex.message);
		if (ex.stack) console.log(ex.stack);
	}

})(curl);
