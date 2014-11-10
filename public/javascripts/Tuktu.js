var tuktu = function() {
	return load: function(u, f) {
		 var h = document.getElementsByTagName("head")[0];
         var s = document.createElement("script");
         s.setAttribute("type", "text/javascript");
         s.setAttribute("src", u);
         h.appendChild(js);
         s.onreadystatechange = function() {
             if (("complete" == js.readyState || "loaded" == js.readyState)) {
            	 
             }
         };
         s.onload = function() {
         };
	}
}
console.log(tuktu);