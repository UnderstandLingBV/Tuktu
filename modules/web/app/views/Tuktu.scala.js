@(api_endpoint: String, capture: List[(String, Boolean, String, String)])

(function() {
	var el, i, len;
	@for((identifier, xpath, selector, event) <- capture) {
		el = document.@if(xpath){evaluate('@selector', document, null, XPathResult.UNORDERED_NODE_ITERATOR_TYPE, null)}else{querySelectorAll('@selector')};
		for (i = 0, len = el.length; i < len; i++) {
			var e = el[i];
			e.addEventListener('@event', function() {
				var xhr = new XMLHttpRequest();
				xhr.open('POST', '@api_endpoint', true);
				xhr.send(JSON.stringify({event: '@event', id: '@identifier'}));
			}, true);
		}
	}
})();
