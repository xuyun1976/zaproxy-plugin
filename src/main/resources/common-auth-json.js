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
	return ['AuthenticationSteps'];
}

function getOptionalParamsNames(){
	return [];
}

function getCredentialsParamsNames(){
	return ['Username', 'Password'];
}

function ZAPAuthenticator(helper, paramsValues, credentials) {
	this.helper = helper;
	this.authenticationSteps = paramsValues.get('AuthenticationSteps');
	
	this.dynamicParameters = new HashMap();	

	return this;
}

ZAPAuthenticator.prototype = {
	doLogin: function () {
	
		var jsonAuthenticationSteps = eval("(" + this.authenticationSteps + ")");
		var response;

		for (var i = 0; i < jsonAuthenticationSteps.length; i++)
		{
			var jsonAuthenticationStep = jsonAuthenticationSteps[i];

			response = this.doRequest(jsonAuthenticationStep);

			this.processParameters(jsonAuthenticationStep, response);
			this.processAdditionalCookies(jsonAuthenticationStep, response);
		}
		return response;
	},
	
	processPostData: function (jsonAuthenticationStep, msg) {
		if (HttpRequestHeader.POST != jsonAuthenticationStep.method || jsonAuthenticationStep.postData == null || jsonAuthenticationStep.postData == '')
			return;
		
		var requestBody = jsonAuthenticationStep.postData;
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

		msg.setRequestBody(requestBody);
	},

	processAdditionalHeaders: function (jsonAuthenticationStep, requestHeader) {
		if (jsonAuthenticationStep.additionalHeaders == null || jsonAuthenticationStep.additionalHeaders == '')
			return;

		var headers = jsonAuthenticationStep.additionalHeaders;
		var parameterNames = this.dynamicParameters.keySet().toArray();

		for (var i = 0; i < parameterNames.length; i++)
		{
			var parameterName = parameterNames[i];
			var parameterValue = this.dynamicParameters.get(parameterName);
			if (parameterValue == '')
				continue;
			
			headers = headers.replace('{' + parameterName + '}', parameterValue);
		}	

		var splits = headers.split(';');

		for (var i = 0; i < splits.length; i++)
		{
			var header = splits[i];
			var index = header.indexOf(":");
			var headerName = splits[i].substring(0, index).trim();
			var headerValue = splits[i].substring(index + 1).trim();
			
			requestHeader.setHeader(headerName, headerValue);
		}	

		return;
	},

	doRequest: function (jsonAuthenticationStep) {
		var msg;
		var requestUri = new URI(jsonAuthenticationStep.url, false);

		var requestHeader = new HttpRequestHeader(jsonAuthenticationStep.method, requestUri, HttpHeader.HTTP10);
		
		this.processAdditionalHeaders(jsonAuthenticationStep, requestHeader);

		msg = this.helper.prepareMessage();
		msg.setRequestHeader(requestHeader);

		this.processPostData(jsonAuthenticationStep, msg);
	

		this.helper.sendAndReceive(msg);
		println("Received response status code for authentication request: " + msg.getResponseHeader().getStatusCode());

		return msg;
	},

	processAdditionalCookies: function (jsonAuthenticationStep, response) {
		if (jsonAuthenticationStep.additionalCookies == null || jsonAuthenticationStep.additionalCookies == '')
			return;
		
		var cookies = jsonAuthenticationStep.additionalCookies;
		var parameterNames = this.dynamicParameters.keySet().toArray();
		
		for (var i = 0; i < parameterNames.length; i++)
		{
			var parameterName = parameterNames[i];
			var parameterValue = this.dynamicParameters.get(parameterName);

			if (parameterValue == '')
				continue;
			
			cookies = cookies.replace('{' + parameterName + '}', parameterValue);
		}

		var splits = cookies.split(';');
		for (var i = 0; i < splits.length; i++)
		{
			var index = splits[i].indexOf("=");
			var cookieName = splits[i].substring(0, index).trim();
			var cookieValue = splits[i].substring(index + 1).trim();
			
			//response.getResponseHeader().setHeader(HttpHeader.SET_COOKIE, "test-cookie=" + "2222");
			var state = this.helper.getCorrespondingHttpState();
			
			state.addCookie(new Cookie(".ebay.com", cookieName, cookieValue, "/", 99999999, false));
			//println("state" + state);
		}
		
		return;
	},

	processParameters: function (jsonAuthenticationStep, response) {
		if (!jsonAuthenticationStep.parameters)
			return;
		
		var splits = jsonAuthenticationStep.parameters.split(';');
		
		for (var i = 0; i < splits.length; i++)
		{
			var parameterName = splits[i].trim();
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
			var json = eval("(" + request.getResponseBody().toString() + ")");
			
			//println(json.hasOwnProperty(parameterName));	
			parameterValue = eval("json." + parameterName);
		}
		catch(e)
		{
		}

		return parameterValue;
	}
};

