/*
The MIT License (MIT)

Copyright (c) 2015, Hans-Georg Becker

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package de.tu_dortmund.ub.api.paia.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tu_dortmund.ub.api.paia.model.*;
import net.sf.saxon.s9api.*;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthAuthzResponse;
import org.apache.oltu.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.oltu.oauth2.common.OAuth;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.ResponseType;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.jetty.http.HttpHeader;
import org.jdom2.Document;
import org.jdom2.transform.JDOMSource;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

/**
 * This class represents the PAIA Auth component as an OAtuh 2.0 Resource Server.
 *
 * @author Hans-Georg Becker
 * @version 2015-01-16
 */
public class PaiaAuthEndpoint extends HttpServlet {

    // Configuration
    private String conffile  = "";
    private Properties config = new Properties();
    private Logger logger = Logger.getLogger(PaiaAuthEndpoint.class.getName());

    /**
     *
     * @throws java.io.IOException
     */
    public PaiaAuthEndpoint() throws IOException {

        this("conf/paia.auth.properties");
    }

    /**
     *
     * @param conffile
     * @throws java.io.IOException
     */
    public PaiaAuthEndpoint(String conffile) throws IOException {

        this.conffile = conffile;

        // Init properties
        try {
            InputStream inputStream = new FileInputStream(conffile);

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

                try {
                    config.load(reader);

                } finally {
                    reader.close();
                }
            }
            finally {
                inputStream.close();
            }
        }
        catch (IOException e) {
            System.out.println("FATAL ERROR: Die Datei '" + conffile + "' konnte nicht geöffnet werden!");
        }

        // init logger
        PropertyConfigurator.configure(config.getProperty("service.log4j-conf"));

        logger.info("[" + config.getProperty("service.name") + "] " + "Starting 'PAIAauth Auth Endpoint' ...");
        logger.info("[" + config.getProperty("service.name") + "] " + "conf-file = " + conffile);
        logger.info("[" + config.getProperty("service.name") + "] " + "log4j-conf-file = " + config.getProperty("service.log4j-conf"));
    }

    protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        ObjectMapper mapper = new ObjectMapper();

        this.logger.debug("[" + config.getProperty("service.name") + "] " + "PathInfo = " + httpServletRequest.getPathInfo());
        this.logger.debug("[" + config.getProperty("service.name") + "] " + "QueryString = " + httpServletRequest.getQueryString());

        String patronid = "";
        String service = "";
        String accept = "";
        String access_token = "";
        String authorization = "";

        String path = httpServletRequest.getPathInfo();
        String[] params = path.substring(1,path.length()).split("/");

        if (params.length == 1) {
            service = params[0];
        }

        // 1. Schritt: Hole 'Accept' und 'Authorization' aus dem Header;
        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        while ( headerNames.hasMoreElements() ) {

            String headerNameKey = (String) headerNames.nextElement();
            this.logger.debug("[" + config.getProperty("service.name") + "] " + "headerNameKey = " + headerNameKey + " / headerNameValue = " + httpServletRequest.getHeader(headerNameKey));

            if (headerNameKey.equals("Accept")) {
                accept = httpServletRequest.getHeader( headerNameKey );
            }
            if (headerNameKey.equals("Authorization")) {
                authorization = httpServletRequest.getHeader( headerNameKey );
                String[] tmp = authorization.split(" ");
                if (tmp.length > 1) {
                    access_token = tmp[1];
                }
                else {
                    access_token= tmp[0];
                }
            }
        }

        this.logger.debug("[" + config.getProperty("service.name") + "] " + "Service: " + service);
        this.logger.debug("[" + config.getProperty("service.name") + "] " + "Accept: " + accept);
        this.logger.debug("[" + config.getProperty("service.name") + "] " + "Authorization: " + authorization);

        StringBuffer jb = new StringBuffer();
        String line = null;
        try {
            BufferedReader reader = httpServletRequest.getReader();
            while ((line = reader.readLine()) != null)
                jb.append(line);
        } catch (Exception e) { /*report an error*/ }

        if (service.equals("logout")) {

            LogoutRequest logoutRequest = new LogoutRequest();
            logoutRequest = mapper.readValue(jb.toString(), LogoutRequest.class);
            patronid = logoutRequest.getPatron();
        }
        else {

        }
        this.logger.debug("[" + config.getProperty("service.name") + "] " + "Patron: " + patronid);

        String requestBody = "";
        if (jb != null) {

            requestBody = jb.toString();
        }

        // 2. Schritt: Service
        if (service.equals("login") || service.equals("logout")) {

            this.paiaAuth(httpServletRequest, httpServletResponse, service, access_token, requestBody);
        }
        else {

            this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_METHOD_NOT_ALLOWED + ": " + "POST for '" + service + "' not allowed!");

            httpServletResponse.setHeader("WWW-Authentificate", "Bearer");
            httpServletResponse.setHeader("WWW-Authentificate", "Bearer realm=\"PAIA Auth\"");
            httpServletResponse.setContentType("application/json");
            httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");

            // Error handling mit suppress_response_codes=true
            if (httpServletRequest.getParameter("suppress_response_codes") != null) {
                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
            }
            // Error handling mit suppress_response_codes=false (=default)
            else {
                httpServletResponse.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }

            // Json für Response body
            RequestError requestError = new RequestError();
            requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_METHOD_NOT_ALLOWED)));
            requestError.setCode(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_METHOD_NOT_ALLOWED) + ".description"));
            requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_METHOD_NOT_ALLOWED) + ".uri"));

            StringWriter json = new StringWriter();
            mapper.writeValue(json, requestError);
            this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

            // send response
            httpServletResponse.getWriter().println(json);
        }
    }

    protected void doOptions(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        httpServletResponse.setHeader("Access-Control-Allow-Methods", this.config.getProperty("Access-Control-Allow-Methods"));
        httpServletResponse.addHeader("Access-Control-Allow-Headers", this.config.getProperty("Access-Control-Allow-Headers"));
        httpServletResponse.setHeader("Accept", this.config.getProperty("Accept"));
        httpServletResponse.setHeader("Access-Control-Allow-Origin", this.config.getProperty("Access-Control-Allow-Origin"));

        httpServletResponse.getWriter().println();
    }

    /**
     * PAIAauth services: Prüfe jeweils die scopes und liefere die Daten
     */
    private void paiaAuth(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String service, String token, String requestBody) throws IOException {

        ObjectMapper mapper = new ObjectMapper();

        switch (service) {

            case "login": {

                LoginRequest loginRequest = mapper.readValue(requestBody, LoginRequest.class);

                // TODO if patronid != null && password != null: baue OAuth-Request wie im Formular Zeile 180ff
                if ((loginRequest.getUsername() != null && loginRequest.getPassword() != null) || (httpServletRequest.getParameter("code") != null && httpServletRequest.getParameter("state") != null)) {

                    LoginResponse loginResponse = this.authorize(httpServletRequest, httpServletResponse, requestBody);

                    httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                    // TODO response; cases html, json, xml
                    httpServletResponse.getWriter().println("Auth successful!");

                }
                // TODO else Baue HTML-Seite mit login-Formular mittels XSLT
                else if (httpServletRequest.getParameter("code") == null || httpServletRequest.getParameter("state") == null) {

                    String provider = this.config.getProperty("service.base_url") + this.config.getProperty("service.endpoint.auth.login.xslt");

                    Document doc = new Document();

                    HashMap<String, String> parameters = new HashMap<String, String>();
                    parameters.put("lang", "de"); // TODO lang via Header
                    parameters.put("redirect_uri_params", URLDecoder.decode(httpServletRequest.getQueryString(), "UTF-8"));
                    parameters.put("formURL", provider);

                    String html = htmlOutputter(doc, this.config.getProperty("login.xslt"), parameters);

                    httpServletResponse.setContentType("text/html;charset=UTF-8");
                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                    httpServletResponse.getWriter().println(html);
                }

            }
            case "logout": {

                LogoutRequest logoutRequest = mapper.readValue(requestBody, LogoutRequest.class);
                String patronid = logoutRequest.getPatron();

                this.logger.debug("[" + config.getProperty("service.name") + "] " + "Patron: " + patronid);

                // DELETE TokenEndpoint
                CloseableHttpClient httpclient = HttpClients.createDefault();

                String url = this.config.getProperty("oauth2.tokenendpoint") + "/" + token;
                this.logger.debug("[" + config.getProperty("service.name") + "] " + "TOKEN-ENDPOINT-URL: " + url);
                HttpDelete httpDelete = new HttpDelete(url);

                CloseableHttpResponse httpResponse = httpclient.execute(httpDelete);

                try {

                    int statusCode = httpResponse.getStatusLine().getStatusCode();
                    HttpEntity httpEntity = httpResponse.getEntity();

                    switch (statusCode) {

                        case 200: {

                            httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                            httpServletResponse.getWriter().println("");

                            break;
                        }
                        default: {

                            this.logger.error("[" + config.getProperty("service.name") + "] " + "ERROR in logout(): HTTP STATUS = " + statusCode);
                            httpServletResponse.sendError(statusCode);
                        }
                    }

                    EntityUtils.consume(httpEntity);
                } finally {
                    httpResponse.close();
                }

                break;
            }
            case "change": {
/* TODO
                if (Lookup.lookupAll(IntegratedLibrarySystem.class).size() > 0) {

                    try {
                        IntegratedLibrarySystem integratedLibrarySystem = Lookup.lookup(IntegratedLibrarySystem.class);
                        // init ILS
                        integratedLibrarySystem.init(this.apiProperties);

                        boolean changed = integratedLibrarySystem.change(patronid, "");
                    }
                    catch (ILSException e) {

                        this.logger.error(HttpServletResponse.SC_SERVICE_UNAVAILABLE + ": ILS! " + e.getMessage());

                        httpServletResponse.setHeader("WWW-Authentificate", "Bearer");
                        httpServletResponse.setHeader("WWW-Authentificate", "Bearer realm=\"PAIA Core\"");
                        httpServletResponse.setContentType("application/json");
                        httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");

                        // Error handling mit suppress_response_codes=true
                        if (httpServletRequest.getParameter("suppress_response_codes") != null) {
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                        }
                        // Error handling mit suppress_response_codes=false (=default)
                        else {
                            httpServletResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                        }

                        // Json für Response body
                        RequestError requestError = new RequestError();
                        requestError.setError(this.apiProperties.getProperty(Integer.toString(HttpServletResponse.SC_SERVICE_UNAVAILABLE) + ".error"));
                        requestError.setCode(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                        requestError.setDescription(this.apiProperties.getProperty(Integer.toString(HttpServletResponse.SC_SERVICE_UNAVAILABLE) + ".description"));
                        requestError.setErrorUri(this.apiProperties.getProperty(Integer.toString(HttpServletResponse.SC_SERVICE_UNAVAILABLE) + ".error.uri"));

                        StringWriter json = new StringWriter();
                        mapper.writeValue(json, requestError);
                        this.logger.debug(json);

                        // send response
                        httpServletResponse.getWriter().println(json);
                    }
                }
*/
                break;
            }
            default: {

                this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_BAD_REQUEST + "Unknown function! (" + service + ")");

                httpServletResponse.setHeader("WWW-Authentificate", "Bearer");
                httpServletResponse.setHeader("WWW-Authentificate", "Bearer realm=\"PAIA Core\"");
                httpServletResponse.setContentType("application/json");
                httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");

                // Error handling mit suppress_response_codes=true
                if (httpServletRequest.getParameter("suppress_response_codes") != null && !httpServletRequest.getParameter("suppress_response_codes").equals("")) {
                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                }
                // Error handling mit suppress_response_codes=false (=default)
                else {
                    httpServletResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }

                // Json für Response body
                RequestError requestError = new RequestError();
                requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_BAD_REQUEST)));
                requestError.setCode(HttpServletResponse.SC_BAD_REQUEST);
                requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_BAD_REQUEST) + ".description"));
                requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_BAD_REQUEST) + ".uri"));

                StringWriter json = new StringWriter();
                mapper.writeValue(json, requestError);
                this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                // send response
                httpServletResponse.getWriter().println(json);
            }
        }
    }

    /**
     *
     * @param httpServletRequest
     * @param httpServletResponse
     * @throws IOException
     */
    private LoginResponse authorize(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String requestBody) throws IOException {

        LoginResponse loginResponse = null;

        ObjectMapper mapper = new ObjectMapper();

        LoginRequest loginRequest = mapper.readValue(requestBody, LoginRequest.class);

        String oAuthzService = this.config.getProperty("oauth2.authzendpoint");
        String client_id = this.config.getProperty("service.client_id.auth");
        String client_secret = this.config.getProperty("service.client_secret.auth");
        String function = "/";

        this.logger.debug("[" + config.getProperty("service.name") + "] " + "HttpHeader.REFERER: " + httpServletRequest.getHeader(HttpHeader.REFERER.asString()));

        String redirect_url = "";

        if (httpServletRequest.getParameter("redirect_url") != null && !httpServletRequest.getParameter("redirect_url").equals("")) {

            redirect_url = URLEncoder.encode(httpServletRequest.getParameter("redirect_url"), "UTF-8");
        }
        else {
            redirect_url = URLEncoder.encode(httpServletRequest.getHeader(HttpHeader.REFERER.asString()).split("&patron=")[0], "UTF-8");
        }

        String redirect_uri = URLEncoder.encode(this.config.getProperty("service.base_url") + this.config.getProperty("service.endpoint.auth")
                + function + "?redirect_uri=" + redirect_url, "UTF-8");
        String scope = "read_patron read_items read_fees write_items"; // alle möglichen scopes in PAIA
        String state = Long.toString(System.currentTimeMillis());

        if (httpServletRequest.getParameter("code") == null || httpServletRequest.getParameter("state") == null) {

            try {

                OAuthClientRequest.AuthenticationRequestBuilder authenticationRequestBuilder = OAuthClientRequest
                        .authorizationLocation(oAuthzService)
                        .setResponseType(ResponseType.CODE.toString())
                        .setClientId(client_id)
                        .setParameter("client_secret", client_secret)
                        .setRedirectURI(redirect_uri)
                        .setScope(scope)
                        .setState(state)
                        .setParameter("patronid", loginRequest.getUsername())
                        .setParameter("password", loginRequest.getPassword());

                OAuthClientRequest request = authenticationRequestBuilder.buildQueryMessage();

                this.logger.debug("[" + config.getProperty("service.name") + "] " + "Code-URI: " + request.getLocationUri());

                httpServletResponse.sendRedirect(request.getLocationUri());

            } catch (OAuthSystemException e) {

                this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_FORBIDDEN+ ": " + e.getMessage() + "!");

                httpServletResponse.setHeader("WWW-Authentificate", "Bearer");
                httpServletResponse.setHeader("WWW-Authentificate", "Bearer realm=\"PAIA auth\"");
                httpServletResponse.setContentType("application/json");
                httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");

                // Error handling mit suppress_response_codes=true
                if (httpServletRequest.getParameter("suppress_response_codes") != null) {
                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                }
                // Error handling mit suppress_response_codes=false (=default)
                else {
                    httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                }

                // Json für Response body
                RequestError requestError = new RequestError();
                requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_FORBIDDEN) + ".2"));
                requestError.setCode(HttpServletResponse.SC_FORBIDDEN);
                requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_FORBIDDEN) + ".2.description"));
                requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_FORBIDDEN) + ".2.uri"));

                StringWriter json = new StringWriter();
                mapper.writeValue(json, requestError);
                this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                // send response
                httpServletResponse.getWriter().println(json);
            }
        }
        else {

            try {

                // TODO Teste 'state'. ABER wie?

                OAuthClientRequest.TokenRequestBuilder tokenRequestBuilder = OAuthClientRequest
                        .tokenLocation(oAuthzService).setParameter(OAuth.OAUTH_RESPONSE_TYPE, ResponseType.TOKEN.toString())
                        .setClientId(client_id)
                        .setClientSecret(client_secret)
                        .setRedirectURI(URLEncoder.encode(this.config.getProperty("service.baseurl") + ":" + this.config.getProperty("service.endpoint.core"), "UTF-8"))
                        .setScope(scope)
                        .setCode(httpServletRequest.getParameter("code"));

                OAuthClientRequest request = tokenRequestBuilder.buildQueryMessage();

                this.logger.debug("[" + config.getProperty("service.name") + "] " + "Token-URI: " + request.getLocationUri());

                OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());

                OAuthJSONAccessTokenResponse oAuthJSONAccessTokenResponse = oAuthClient.accessToken(request);

                this.logger.debug("[" + config.getProperty("service.name") + "] " + "TokenResponse: " + oAuthJSONAccessTokenResponse.getBody());

                loginResponse = new LoginResponse();
                loginResponse.setToken_type("Bearer");
                loginResponse.setAccess_token(oAuthJSONAccessTokenResponse.getAccessToken());
                loginResponse.setExpires_in(oAuthJSONAccessTokenResponse.getExpiresIn());
                loginResponse.setPatron(oAuthJSONAccessTokenResponse.getParam("patronid"));
                loginResponse.setScope(oAuthJSONAccessTokenResponse.getScope());

            }
            catch (OAuthSystemException e) {

                this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_FORBIDDEN+ ": " + e.getMessage() + "!");

                httpServletResponse.setHeader("WWW-Authentificate", "Bearer");
                httpServletResponse.setHeader("WWW-Authentificate", "Bearer realm=\"PAIA auth\"");
                httpServletResponse.setContentType("application/json");
                httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");

                // Error handling mit suppress_response_codes=true
                if (httpServletRequest.getParameter("suppress_response_codes") != null) {
                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                }
                // Error handling mit suppress_response_codes=false (=default)
                else {
                    httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                }

                // Json für Response body
                RequestError requestError = new RequestError();
                requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_FORBIDDEN) + ".2"));
                requestError.setCode(HttpServletResponse.SC_FORBIDDEN);
                requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_FORBIDDEN) + ".2.description"));
                requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_FORBIDDEN) + ".2.uri"));

                StringWriter json = new StringWriter();
                mapper.writeValue(json, requestError);
                this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                // send response
                httpServletResponse.getWriter().println(json);

            }
            catch (OAuthProblemException e) {

                this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_FORBIDDEN+ ": " + e.getMessage() + "!");

                httpServletResponse.setHeader("WWW-Authentificate", "Bearer");
                httpServletResponse.setHeader("WWW-Authentificate", "Bearer realm=\"PAIA auth\"");
                httpServletResponse.setContentType("application/json");
                httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");

                // Error handling mit suppress_response_codes=true
                if (httpServletRequest.getParameter("suppress_response_codes") != null) {
                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                }
                // Error handling mit suppress_response_codes=false (=default)
                else {
                    httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                }

                // Json für Response body
                RequestError requestError = new RequestError();
                requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_FORBIDDEN) + ".2"));
                requestError.setCode(HttpServletResponse.SC_FORBIDDEN);
                requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_FORBIDDEN) + ".2.description"));
                requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_FORBIDDEN) + ".2.uri"));

                StringWriter json = new StringWriter();
                mapper.writeValue(json, requestError);
                this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                // send response
                httpServletResponse.getWriter().println(json);
            }
        }

        return loginResponse;
    }


    /**
     * This method transforms a XML document to HTML via a given XSLT stylesheet. It respects a map of additional parameters.
     *
     * @param doc
     * @param xslt
     * @param params
     * @return
     * @throws IOException
     */
    private String htmlOutputter(Document doc, String xslt, HashMap<String,String> params) throws IOException {

        String result = null;

        try {

            // Init XSLT-Transformer
            Processor processor = new Processor(false);
            XsltCompiler xsltCompiler = processor.newXsltCompiler();
            XsltExecutable xsltExecutable = xsltCompiler.compile(new StreamSource(xslt));


            XdmNode source = processor.newDocumentBuilder().build(new JDOMSource( doc ));
            Serializer out = new Serializer();
            out.setOutputProperty(Serializer.Property.METHOD, "html");
            out.setOutputProperty(Serializer.Property.INDENT, "yes");

            StringWriter buffer = new StringWriter();
            out.setOutputWriter(new PrintWriter( buffer ));

            XsltTransformer trans = xsltExecutable.load();
            trans.setInitialContextNode(source);
            trans.setDestination(out);

            if (params != null) {
                for (String p : params.keySet()) {
                    trans.setParameter(new QName(p), new XdmAtomicValue(params.get(p).toString()));
                }
            }

            trans.transform();

            result = buffer.toString();

        } catch (SaxonApiException e) {

            this.logger.error("SaxonApiException: " + e.getMessage());
        }

        return result;
    }
}
