@(actor_path: String, api_endpoint: String, capture: List[(String, Boolean, String, String)])

(function() {
	var el, i, len, api = '@api_endpoint', path = '@actor_path';

	@**
	 *  The function which adds the event listener for event type ev to e, and posts it to api with an id
	 *@
	function aEL(e, ev, id) {
		e.addEventListener(ev, function() {
			var xhr = new XMLHttpRequest();
			xhr.open('POST', api, true);
			xhr.send(JSON.stringify({event: ev, id: id, path: path}));
		}, true);
	}

	@for((identifier, xpath, selector, event) <- capture) {
		@if(xpath) {
			el = document.evaluate('@selector', document, null, XPathResult.UNORDERED_NODE_ITERATOR_TYPE, null);
			@* Iterate over XPathResult Iterator *@
			try {
				var e = el.iterateNext();

				while (e) {
					aEL(e, '@event', '@identifier');
					e = el.iterateNext();
				}
			}
			catch (e) {
				dump('Error: Document tree modified during iteration ' + e);
			}
		} else {
			el = document.querySelectorAll('@selector');
			@* Iterate over NodeList *@
			for (i = 0, len = el.length; i < len; i++) {
				var e = el[i];
				aEL(e, '@event', '@identifier');
			}
		}
	}
})();
