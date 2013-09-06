Code to modern standards. Run everywhere.
=========

License: MIT

poly.js is the a collection of AMD modules that shim (aka "polyfill")
old browsers to support modern (aka "ES5-ish") javascript.

poly.js is unique amongst ES5-ish shims because it:

* is modular, not monolithic
* is tiny
* is configurable to suit your code
* can be minified using a has-aware optimizer

Note: poly/strict has been deprecated. See below.

Support
---

Issues: https://github.com/cujojs/poly/issues

Discussion: https://groups.google.com/d/forum/cujojs


What's new
---

* 0.5.2
	* Implement setImmediate/clearImmediate as a temporary, non-standard method
	  for performant task queueing.
	* New poly/es5 and poly/es5-strict modules. poly/strict is deprecated.
	* Use @kitcambridge's JSON3 instead of JSON2
	* Date shim now keeps properties on Date constructor and keeps the name
	  of the constructor "Date" (was previously "_Date")
	* Fix bugs in Object shims in IE 6-8.

Features
---

poly augments browsers with all of the following features:

poly/array:
---

* array.forEach
* array.map
* array.some
* array.every
* array.indexOf
* array.lastIndexOf
* array.reduce
* array.reduceRight
* Array.isArray

poly/function:
---

* func.bind

poly/json:
---

* (global) JSON

poly/setImmediate:
---

* (global) setImmediate
* (global) clearImmediate

Note: setImmediate is not expected to become standardized, but is included
here as an interim solution as a performant next-turn implementation.

poly/object:
---

* Object.create *
* Object.freeze *
* Object.isFrozen *
* Object.seal *
* Object.isSealed *
* Object.getPrototypeOf
* Object.keys
* Object.getOwnPropertyNames
* Object.defineProperty *
* Object.defineProperties *
* Object.isExtensible
* Object.preventExtensions *
* Object.getOwnPropertyDescriptor *

Methods marked with * cannot be shimmed completely. You can decide whether
these methods should fail silently or loudly.  The poly/object and poly/all
modules return a function, `failIfShimmed`, that takes a single parameter.

This parameter may be:

* a boolean (all Object.XXX functions should fail)
* a function that takes a method name as a parameter and return truthy/falsey

By default, poly/object will not throw any exceptions and allows non-functional
or incomplete shims to fail silently.  poly/all works the same way.  However,
poly/strict sets `failIfShimmed` so that poly/object will throw
exceptions for some functions.  (see below)

Object.getPrototypeOf works in all situations except when using raw
prototypal inheritance in IE6-8.  This is due to a well-known IE bug that
clobbers the constructor property on objects whose constructor has a prototype.

By "raw", we mean the following:

```js
function MyClass () {}
MyClass.prototype = { foo: 42 };
var obj = new MyClass();
console.log(obj.constructor == MyClass); // false in IE6-8
```

The workaround is to set the constructor explicitly:

```js
function MyClass () {}
MyClass.prototype = { foo: 42, constructor: MyClass };
var obj = new MyClass();
console.log(obj.constructor == MyClass); // true everywhere!!!!!
```

Most inheritance helper libs, including John Resig's Simple Inheritance, dojo,
and prototype.js already do this for you.

poly/string:
---

* string.trim
* string.trimLeft
* string.trimRight

poly/xhr:
---

* (global) XMLHttpRequest

poly/date:
---

* Date.parse now supports simplified ISO8601 date strings
* new Date() now supports simplified ISO8601 date strings
* date.toISOString() returns a simplified ISO8601 date string

poly/all (also just "poly"):
---

This is a *convenience module* to load and apply all shims.  Shims that have
varying levels of "strictness" are set to be loose.  Use poly/strict or
create your own version of poly/all to be stricter.

The "poly" main module will load poly/all.

poly/es5:
---

This *convenience module* loads and applies all es5 shims.  Shims, such as
poly/setImmediate are not included.

poly/strict:
---

This is module is deprecated.  Please use poly/es5-strict.

poly/es5-strict:
---

This *convenience module* loads and applies all es5 shims, but ensures that
whitespace characters comply with ES5 specs (many browsers don't do this)
and fails loudly for the following object shims that can't reasonably
be shimmed to comply with ES5:

* Object.defineProperty
* Object.defineProperties
* Object.preventExtensions
* Object.getOwnPropertyDescriptor
* Object.create (but only if supplying the second parameter)

If you would like your code to be even stricter, load poly/object or poly/string
separately and set the desired level of strictness.

Examples
==========

Sample AMD package declaration:

```js
var cfg = {
	packages: [
		{ name: 'poly', location: 'js/poly-0.5', main: 'poly' }
	]
};
```

Sample AMD package declaration (strict):

```js
var cfg = {
	packages: [
		{ name: 'poly', location: 'js/poly-0.5', main: 'strict' }
	]
};
```

Using poly's modules as shims / polyfills:

```js
	// somewhere in your app's initialization code, load the "poly/array"
	// and "poly/function" module
	// and it will shim the native Array prototype
	curl({ preloads: [ "poly/array" ] });

	// later, just use arrays as if the js engine supports javascript 1.7!
	define(/* my module */ function () {

		// Arrays are so hawt!

		return {

			myFunc: function (arr, process) {

				arr.forEach(function (item) {

					process(item);

				}

			}

		}

	});
```

```js
	// use all available shims
	curl({ preloads: [ "poly/all" ] });
```

```js
	// another way to use all available shims
	curl({ preloads: [ "poly" ] });
```

```js
	// use all shims in, but with stronger ES5 compliance
	curl({ preloads: [ "poly/strict" ] });
```

```js
	// use just the array and function shims
	curl({ preloads: [ "poly/array", "poly/function" ] });
```

How do I know which shims to use?
===

If you've written the code, you probably know what ES5-ish features you've used.
If you're using `func.bind()`, you should load the poly/function module.  If
you're using `str.trim()`, you will need the poly/string module.

If you're leveraging code that is meant to run in a CommonJS environment, you
probably need all of poly's shims except poly/xhr.  (Note:
[curl.js](https://github.com/cujojs/curl) can load CommonJS Modules/1.1 files
without pre-wrapping them in an AMD "transport" wrapper.  Check out the
[moduleLoader](https://github.com/cujojs/curl/wiki/Using-curl.js-with-CommonJS-Modules)
package config option.)

If you're using poly.js with wire.js, you will need the following shims
to use wire.js in all browsers: poly/array, poly/function, poly/object.

How can I limit the size of the shim modules?
---

poly.js supports has-aware AMD optimizers.  dojo's build tool and RequireJS's
r.js optimizer will automatically remove unneeded shims when provided a "has
profile".  Please refer to either of those optimization tools for more
information about using and creating a "has profile".

Can I use feature detection to only load the shims the current browser requires?
---

Unfortunately, browser's didn't adopt ES5 features over night. There are various
degrees of ES5-ishness in the wild.  Therefore, there is no _magic test_ that
you can use to determine whether to load an ES5-ish shim or not.

However, if you limit your supported browser list, you may be able to make
certain assumptions.  For instance, if you limit your supported browsers to IE6+
and the latest version for the remainder of the vendors, you could choose
a fairly broad test.  Something like the following is fairly safe:

```js
var preloads = [];
if (typeof Object.preventExtensions != 'function') {
	preloads.push('poly/all');
}
curl({ preloads: preloads });
```

If your list of supported browsers isn't so clean, try taking a survey of your
code so you can find a reasonable set of tests.  The following is a possible
set of tests for a project that uses object, function, and string shims:

```js
var preloads = [];
if (typeof Object.preventExtensions == 'function') {
	preloads.push('poly/object');
}
if (typeof Function.prototype.bind != 'function') {
	preloads.push('poly/function');
}
if (typeof "".trim != 'function') {
	preloads.push('poly/string');
}
curl({ preloads: preloads });
```

JSON3
===

JSON support via Kit Cambridge's JSON3 lib at:
https://github.com/bestiejs/json3.git
