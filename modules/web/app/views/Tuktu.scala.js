@(nextFlow: Option[String], jsCode: String, jsUrl: String)
var tuktuvars;
if (typeof tuktuvars === 'undefined') {
	tuktuvars = {};
}
var tuktu = function() {
	 var h = document.getElementsByTagName("head")[0];
     var s = document.createElement("script");
     s.setAttribute("type", "text/javascript");
     s.innerHTML = "@jsCode";
     h.appendChild(s);
     
     @nextFlow match {
    	 case Some(nf) => {
    		 S = document.createElement("script");
    	     s.setAttribute("type", "text/javascript");
    	     s.innerHTML = "var xhr = new XMLHttpRequest();" +
    	    		 "xhr.open('POST', '@jsUrl', true);" +
    	    		 "xhr.send(JSON.stringify({f: '@nextFlow', d: tuktuvars}));";
    	     h.appendChild(s);
    	 }
    	 case None => {}
     }
}
tuktu();