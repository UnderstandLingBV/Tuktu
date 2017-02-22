@(nextFlow: Option[String], jsCode: String, jsUrl: String, includes: List[String])
var tuktuvars;
if (typeof tuktuvars === 'undefined') {
	tuktuvars = { "url": document.URL };
	tuktuvars = { "referrer_url": document.referrer };
}
function appendScript(link) {
	var h = document.getElementsByTagName("head")[0];
	var s = document.createElement("script");
	s.setAttribute("type", "text/javascript");
	s.setAttribute("async", "false");
	s.src=link;
	h.appendChild(s);
}
if (typeof jQuery === 'undefined') {appendScript("//code.jquery.com/jquery-2.2.0.min.js");}
function tuktuFlow(nf) {
	var h = document.getElementsByTagName("head")[0];
	var s = document.createElement("script");
	s.setAttribute("type", "text/javascript");
	s.innerHTML = "var xhr = new XMLHttpRequest();" +
		"xhr.open('POST', '@jsUrl', true);" +
		"xhr.setRequestHeader('Content-Type', 'application/json;charset=UTF-8');" +
		"xhr.send(JSON.stringify({f: '" + nf + "', d: tuktuvars}));" +
		"xhr.onreadystatechange = function() {" +
			"if (xhr.readyState == 4) {" +
				"var se = document.createElement('script');" +
				"se.setAttribute('type', 'text/javascript');" +
				"se.innerHTML = xhr.responseText;" +
				"document.getElementsByTagName('head')[0].appendChild(se);" +
			"}" +
		"}";
	h.appendChild(s);
}
var tuktu = function() {
	tuktuvars.referrer = document.referrer;
	var h = document.getElementsByTagName("head")[0];
	var s;

	s = document.createElement("script");
	s.setAttribute("type", "text/javascript");
	s.innerHTML = "@Html(jsCode)";
	h.appendChild(s);

	@nextFlow match {
		case Some(nf) => {tuktuFlow('@nf');}
		case None => {}
	}
}
@for(incl <- includes) {appendScript("@incl");}
tuktu();