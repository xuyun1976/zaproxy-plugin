/**
 * Script to authenticate on a MediaWiki site in ZAP via the login form.
 *
 * MediaWiki protects against Login CSRF using a login token generated
 * on viewing the login page and storing it in the session and in a
 * hidden field in the login form. On submitting the login form, the
 * submitted token and the one in the session are compared to prevent
 * login CSRF. As a result, ZAP can't currently handle MediaWiki logins
 * with its form-based authentication. So, we need to first get the login
 * token, then use it to perform the login request.
 *
 * The required parameter 'Login URL' should be set to the path to
 * Special:UserLogin, i.e. http://127.0.0.1/wiki/Special:UserLogin
 *
 * The regex pattern to identify logged in responses could be set to:
 *     id="pt-logout"
 *
 * The regex pattern to identify logged out responses could be set to:
 *     id="pt-login"
 *
 * @author grunny
 */

function authenticate(helper, paramsValues, credentials) {
	println("Authenticating via JavaScript script...");
	importClass(org.parosproxy.paros.network.HttpRequestHeader);
	importClass(org.parosproxy.paros.network.HttpHeader);
	importClass(org.apache.commons.httpclient.URI);
	importClass(org.apache.commons.httpclient.Cookie);
	importClass(net.htmlparser.jericho.Source);
	importClass(java.util.HashMap);

	var authHelper = new ZAPAuthenticator(helper, paramsValues, credentials);
	var response = authHelper.doLogin();
	
	println("Fininshed Authenticat via JavaScript script");

	return response; 
}

function getRequiredParamsNames(){
	return ['LoginURLs', 'PostData'];
}

function getOptionalParamsNames(){
	return ['DynamicParameterNames', 'AdditionalCookies', 'AdditionalHeaders'];
}

function getCredentialsParamsNames(){
	return ['Username', 'Password'];
}

function ZAPAuthenticator(helper, paramsValues, credentials) {
	
	this.helper = helper;
	this.loginUrls = paramsValues.get('LoginURLs');
	this.postdata = paramsValues.get('PostData');
	this.dynamicParameterNames = paramsValues.get('DynamicParameterNames');

	this.additionalCookies = paramsValues.get('AdditionalCookies');
	this.additionalHeaders = paramsValues.get('AdditionalHeaders');

	this.userName = credentials.getParam('Username');
	this.password = credentials.getParam('Password');
	
	this.dynamicParameters = new HashMap();	
	
	return this;
}

ZAPAuthenticator.prototype = {
	doLogin: function () {
		this.initDynamicParameters();
		var splits = this.loginUrls.split(';');
		var response;
		
		for (var i = 0; i < splits.length; i++)
		{
			var request = splits[i].trim();
			if (request == '')
				continue;
			
			
			var index = request.indexOf(' ');
			var method = request.substring(0, index).toUpperCase();
			var url = request.substring(index + 1);
			var body = this.processPostData(method);
			
			var headerMap = this.processHeader();			
			response = this.doRequest(url, method, body, headerMap);
			
			this.processDynamicParameter(response);
			this.processCookies(response);
		}
		
		return response;
	},

	initDynamicParameters: function () {
		if (this.dynamicParameterNames == null || this.dynamicParameterNames == '')
			return;
		
		var splits = this.dynamicParameterNames.split(';');
		
		for (var i = 0; i < splits.length; i++)
		{
			var parameterName = splits[i].trim();
			if (parameterName == '')
				continue;
			
			this.dynamicParameters.put(parameterName, '');
		}
	},
	
	processPostData: function (method) {
		if (HttpRequestHeader.POST != method || this.postdata == null || this.postdata == '')
			return null;
		
		var requestBody = this.postdata;
		var parameterNames = this.dynamicParameters.keySet().toArray();
		
		for (var i = 0; i < parameterNames.length; i++)
		{
			var parameterName = parameterNames[i];
			var parameterValue = this.dynamicParameters.get(parameterName);

			if (parameterValue == '')
				continue;
			
			requestBody = requestBody.replace('{' + parameterName + '}', parameterValue);

			if (requestBody.indexOf(parameterValue) == -1)
				requestBody = requestBody + '&' + parameterName + '=' + encodeURIComponent(parameterValue);
		}

		return requestBody;
	},

	processHeader: function () {
		if (this.additionalHeaders == null || this.additionalHeaders == '')
			return null;
		
		var headers = this.additionalHeaders;
		var parameterNames = this.dynamicParameters.keySet().toArray();
		
		for (var i = 0; i < parameterNames.length; i++)
		{
			var parameterName = parameterNames[i];
			var parameterValue = this.dynamicParameters.get(parameterName);
			if (parameterValue == '')
				continue;
			
			headers = headers.replace('{' + parameterName + '}', parameterValue);
		}		
		
		var headerMap = new HashMap();
		var splits = headers.split(';');

		for (var i = 0; i < splits.length; i++)
		{
			var header = splits[i];
			var index = header.indexOf(":");
			var headerName = splits[i].substring(0, index).trim();
			var headerValue = splits[i].substring(index + 1).trim();
			
			headerMap.put(headerName, headerValue);
		}		

		return headerMap;
	},

	doRequest: function (url, requestMethod, requestBody, headerMap) {
		var msg;
		var requestInfo;
		var requestUri = new URI(url, false);

		var requestHeader = new HttpRequestHeader(requestMethod, requestUri, HttpHeader.HTTP10);

		if (headerMap)
		{
			var headerNames = headerMap.keySet().toArray();
		
			for (var i = 0; i < headerNames.length; i++)
			{
				var headerName = headerNames[i];
				var headerValue = headerMap.get(headerName);
				
				requestHeader.setHeader(headerName, headerValue);
			}	
		}
		
		requestInfo = 'Sending ' + requestMethod + ' request to ' + requestUri;
		
		msg = this.helper.prepareMessage();
		msg.setRequestHeader(requestHeader);

		if (requestBody) {
			requestInfo += ' with body: ' + requestBody;
			msg.setRequestBody(requestBody);
		}

		this.helper.sendAndReceive(msg);
		println("Received response status code for authentication request: " + msg.getResponseHeader().getStatusCode());

		return msg;
	},

	processCookies: function (response) {
		if (this.additionalCookies == null || this.additionalCookies == '')
			return;
		
		var cookies = this.additionalCookies;
		var parameterNames = this.dynamicParameters.keySet().toArray();
		
		for (var i = 0; i < parameterNames.length; i++)
		{
			var parameterName = parameterNames[i];
			var parameterValue = this.dynamicParameters.get(parameterName);

			if (parameterValue == '')
				continue;
			
			cookies = cookies.replace('{' + parameterName + '}', parameterValue);
		}

		println(cookies);

		var splits = cookies.split(';');
		for (var i = 0; i < splits.length; i++)
		{
			var index = splits[i].indexOf("=");
			var cookieName = splits[i].substring(0, index).trim();
			var cookieValue = splits[i].substring(index + 1).trim();
			
			//response.getResponseHeader().setHeader(HttpHeader.SET_COOKIE, cookieValue + "=" + cookieName);
			var state = this.helper.getCorrespondingHttpState();
			state.addCookie(new Cookie(null, cookieName, cookieValue, null, null, false));
		}
		
		return;
	},

	processDynamicParameter: function (response) {
		var parameterNames = this.dynamicParameters.keySet().toArray();
		
		for (var i = 0; i < parameterNames.length; i++)
		{
			var parameterName = parameterNames[i];
			var parameterValue = this.getDynamicValueFromResponse(response, parameterName);

			if (parameterValue)
			{
				println(parameterName + "=" + parameterValue);
				this.dynamicParameters.put(parameterName, parameterValue);
			}
		}
	},

	getDynamicValueFromResponse: function (request, parameterName) {
		var parameterValue = this.getDynamicValueFromResponseHeader(request, parameterName);
		
		if (parameterValue == null || parameterValue == '')
			parameterValue = this.getDynamicValueFromHTMLResponse(request, parameterName);
		if (parameterValue == null || parameterValue == '')
			parameterValue = this.getDynamicValueFromJsonResponse(request, parameterName);
		return parameterValue;
	},

	getDynamicValueFromResponseHeader: function (request, parameterName) {
		var parameterValue;
		
		var cookies = request.getResponseHeader().getCookieParams();
		//var state = helper.getCorrespondingHttpState();
		for(var iterator = cookies.iterator(); iterator.hasNext();){
			var cookie = iterator.next();
			if (cookie.getName() == parameterName)
			{
				parameterValue = cookie.getValue();
				break;
			}
			//println("Manually adding cookie: " + cookie.getName() + " = " + cookie.getValue());
			//state.addCookie(new Cookie(cookie.getName(), cookie.getValue(), path, 999999, false));
		}

		return parameterValue;
	},

	getDynamicValueFromHTMLResponse: function (request, parameterName) {
		var parameterValue;
		var src = new Source(request.getResponseBody().toString());
		var elements = src.getAllElements('input');

		for (var iterator = elements.iterator(); iterator.hasNext();) {
			element = iterator.next();
			if (element.getAttributeValue('name') == parameterName) {
				parameterValue = element.getAttributeValue('value');
				break;
			}
		}

		return parameterValue;
	},

	getDynamicValueFromJsonResponse: function (request, parameterName) {
		var parameterValue;
		
		try
		{
			println(request.getResponseBody().toString());
			var json = eval("(" + request.getResponseBody().toString() + ")");
			
			parameterValue = json.data.sessionID
		}
		catch(e)
		{
			
		}

		return parameterValue;
	}
};

