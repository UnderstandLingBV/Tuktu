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
    		 s = document.createElement("script");
    	     s.setAttribute("type", "text/javascript");
    	     s.innerHTML = "var xhr = new XMLHttpRequest();" +
    	    		 "xhr.open('POST', '@jsUrl', true);" +
    	    		 "xhr.setRequestHeader('Content-Type', 'application/json;charset=UTF-8');" +
    	    		 "xhr.send(JSON.stringify({f: '@nextFlow', d: tuktuvars}));" +
    	    		 "xhr.onreadystatechange = function() {" +
    	    			    "if (xhr.readyState == 4) {" +
    	    			        "s = document.createElement('script');" +
    	    			        "s.setAttribute('type', 'text/javascript');" +
    	    			        "s.innerHTML = xhr.responseText;" +
    	    			        "document.getElementsByTagName('head')[0].appendChild(s);" +
    	    			    "}}";
    	     h.appendChild(s);
    	 }
    	 case None => {}
     }
}
tuktu();