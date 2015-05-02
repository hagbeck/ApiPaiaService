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

package de.tu_dortmund.ub.api.paia.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tu_dortmund.ub.api.paia.core.ils.ILSException;
import de.tu_dortmund.ub.api.paia.core.ils.IntegratedLibrarySystem;
import de.tu_dortmund.ub.api.paia.core.ils.model.Patron;
import de.tu_dortmund.ub.api.paia.core.model.Document;
import de.tu_dortmund.ub.api.paia.core.model.DocumentList;
import de.tu_dortmund.ub.api.paia.core.model.FeeList;
import de.tu_dortmund.ub.api.paia.model.RequestError;
import de.tu_dortmund.ub.util.impl.Lookup;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.OAuthJSONAccessTokenResponse;
import org.apache.oltu.oauth2.common.OAuth;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.ResponseType;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.jetty.http.HttpHeader;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;

/**
 * Created by cihabe on 05.02.14.
 */
public class PaiaCoreEndpoint extends HttpServlet {

    // Configuration
    private String conffile  = "";
    private Properties config = new Properties();
    private Logger logger = Logger.getLogger(PaiaCoreEndpoint.class.getName());
    private Properties apikeys;

    /**
     *
     * @throws IOException
     */
    public PaiaCoreEndpoint() throws IOException {

        this("conf/paia.properties", null);
    }

    /**
     *
     * @param conffile
     * @throws IOException
     */
    public PaiaCoreEndpoint(String conffile, Properties apikeys) throws IOException {

        this.conffile = conffile;

        // Init properties
        try {
            InputStream inputStream = new FileInputStream(conffile);

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

                try {
                    this.config.load(reader);

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
        PropertyConfigurator.configure(this.config.getProperty("service.log4j-conf"));

        this.logger.info("[" + this.config.getProperty("service.name") + "] " + "Starting 'PAIA Core Endpoint' ...");
        this.logger.info("[" + this.config.getProperty("service.name") + "] " + "conf-file = " + conffile);
        this.logger.info("[" + this.config.getProperty("service.name") + "] " + "log4j-conf-file = " + this.config.getProperty("service.log4j-conf"));

        this.apikeys = apikeys;
    }

    /**
     *
     * @param httpServletRequest
     * @param httpServletResponse
     * @throws ServletException
     * @throws IOException
     */
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        // GET kommt vor, wenn in einer Anwendung, z.B. in Katalog plus, ein Nutzer der z.B. vormerken möchte noch nicht
        // angemeldet ist. Hier ist es dann möglich, dass die Anwendung einen GET-Request als einfaches <a>-Tag sendet.

        ObjectMapper mapper = new ObjectMapper();

        this.logger.debug("[" + config.getProperty("service.name") + "] " + "PathInfo = " + httpServletRequest.getPathInfo());
        this.logger.debug("[" + config.getProperty("service.name") + "] " + "QueryString = " + httpServletRequest.getQueryString());

        String patronid = "";
        String service = "";
        String accept = "";
        String access_token = "";
        String authorization = "";

        String path = httpServletRequest.getPathInfo();
        if (path != null) {
            String[] params = path.substring(1,path.length()).split("/");

            if (params.length == 1) {
                patronid = params[0];
                service = "patron";
            }
            else if (params.length == 2) {
                patronid = params[0];
                service = params[1];
            }
            else if (params[1].equals("items") && params.length == 3) {
                patronid = params[0];
                service = params[1] + "/" + params[2];
            }
        }

        // TODO Alternative: OpenAM-Session-Cookie abfragen und im Falle der Existenz das UserID-Attribut abfragen und mit dem aus der URL vergleichen. Bei ok: Anlegen eines PAIA-Tokens
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
                access_token = authorization.split(" ")[1];
            }
        }

        if (authorization.equals("") && httpServletRequest.getParameter("access_token") != null && !httpServletRequest.getParameter("access_token").equals("")) {
            access_token = httpServletRequest.getParameter("access_token");
            authorization = "Bearer " + access_token;
        }

        this.logger.debug("[" + config.getProperty("service.name") + "] " + "Patron: " + patronid);
        // "patronid" als Platzhalter entfernen
        if (patronid.equals("patronid")) {
            patronid = "";
            this.logger.debug("Patron: " + patronid);
        }
        this.logger.debug("[" + config.getProperty("service.name") + "] " + "Service: " + service);
        this.logger.debug("[" + config.getProperty("service.name") + "] " + "Accept: " + accept);
        this.logger.debug("[" + config.getProperty("service.name") + "] " + "Authorization: " + authorization);

        // 2. Schritt: service supported by GET? -> NO: SC_METHOD_NOT_ALLOWED
        if (service.equals("patron") || service.equals("fullpatron") || service.equals("items") || service.startsWith("items/") || service.equals("fees") || service.equals("request")) {

            DocumentList documentList = null;

            if (service.equals("request") && httpServletRequest.getParameter("document_id") != null) {

                this.logger.debug("[" + config.getProperty("service.name") + "] " + "TEST request + document_id? " + service + ", " + httpServletRequest.getParameter("document_id"));

                documentList = new DocumentList();
                documentList.setDoc(new ArrayList<Document>());

                Document document = new Document();
                document.setEdition(httpServletRequest.getParameter("document_id"));

                documentList.getDoc().add(document);

                this.logger.debug("[" + config.getProperty("service.name") + "] " + "TEST documentList? " + documentList.getDoc().size());
            }

            // Authentifizierung und Autorisierung
            if (!authorization.equals("") && authorization.contains("Bearer")) {

                // Teste Token: HTTP GET {OAuthTokenEndpoint}/{authorization}?scope={SCOPE}
                this.checkToken(httpServletRequest, httpServletResponse, service, patronid, access_token, documentList);
            }
            else {

                // Authorization
                this.authorize(httpServletRequest, httpServletResponse, service, patronid, documentList);
            }
        }
        else {

            this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_METHOD_NOT_ALLOWED + ": " + "GET for '" + service + "' not allowed!");

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

    /**
     *
     * @param httpServletRequest
     * @param httpServletResponse
     * @throws ServletException
     * @throws IOException
     */
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
            patronid = params[0];
            service = "patron";
        }
        else if (params.length == 2) {
            patronid = params[0];
            service = params[1];
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

        this.logger.debug("[" + config.getProperty("service.name") + "] " + "Patron: " + patronid);
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

        // read DocumentList
        DocumentList documents = mapper.readValue(jb.toString(), DocumentList.class);

        // 2. Schritt: Authentifizierung und Autorisierung
        if (service.equals("request") || service.equals("renew") || service.equals("cancel")) {

            // Authentifizierung und Autorisierung
            if (!authorization.equals("") && authorization.contains("Bearer")) {

                // Teste Token: HTTP GET {OAuthTokenEndpoint}/{authorization}?scope={SCOPE}
                this.checkToken(httpServletRequest, httpServletResponse, service, patronid, access_token, documents);
            }
            else {

                // Authorization
                this.authorize(httpServletRequest, httpServletResponse, service, patronid, documents);
            }
        }
        else {

            this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_METHOD_NOT_ALLOWED + ": " + "POST for '" + service + "' not allowed!");

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
     *
     * @param httpServletRequest
     * @param httpServletResponse
     * @param service
     * @param patronid
     * @param access_token
     * @throws IOException
     */
    private void checkToken(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String service, String patronid, String access_token, DocumentList documents) throws IOException {

        ObjectMapper mapper = new ObjectMapper();

        String scope = "";
        switch (service) {

            case "patron" : {

                scope = "read_patron";
                break;
            }
            case "fullpatron" : {

                scope = "write_patron";
                break;
            }
            case "items" : {

                scope = "read_items";
                break;
            }
            case "items/ordered" : {

                scope = "read_items";
                break;
            }
            case "items/reserved" : {

                scope = "read_items";
                break;
            }
            case "items/borrowed" : {

                scope = "read_items";
                break;
            }
            case "request" : {

                scope = "write_items";
                break;
            }
            case "renew" : {

                scope = "write_items";
                break;
            }
            case "cancel" : {

                scope = "write_items";
                break;
            }
            case "fees" : {

                scope = "read_fees";
                break;
            }
        }

        CloseableHttpClient httpclient = HttpClients.createDefault();
        CloseableHttpResponse httpResponse = null;

        try {

            int statusCode = -1;

            if (this.config.getProperty("service.auth.ubdo.tokenendpoint") != null && !this.config.getProperty("service.auth.ubdo.tokenendpoint").equals("")) {

                String url = this.config.getProperty("service.auth.ubdo.tokenendpoint") + "/" + access_token + "?scope=" + scope;
                this.logger.debug("[" + config.getProperty("service.name") + "] " + "TOKEN-ENDPOINT-URL: " + url);
                HttpGet httpGet = new HttpGet(url);

                try {

                    httpResponse = httpclient.execute(httpGet);
                    statusCode = httpResponse.getStatusLine().getStatusCode();
                }
                catch (Exception e) {

                    statusCode = HttpServletResponse.SC_SERVICE_UNAVAILABLE;
                }
            }
            else {

                statusCode = HttpServletResponse.SC_FORBIDDEN;
            }

            switch (statusCode) {

                case HttpServletResponse.SC_OK: {

                    this.logger.debug("[" + config.getProperty("service.name") + "] " + "PaiaService." + service + " kann ausgeführt werden!");

                    this.paiaCore(httpServletRequest, httpServletResponse, patronid, access_token, scope, service, documents);

                    break;
                }
                case HttpServletResponse.SC_FORBIDDEN: {

                    if (apikeys != null && apikeys.containsKey(access_token) && apikeys.getProperty(access_token).contains(scope)) {

                        this.logger.debug("[" + config.getProperty("service.name") + "] " + "PaiaService." + service + " kann ausgeführt werden!");

                        this.paiaCore(httpServletRequest, httpServletResponse, patronid, access_token, scope, service, documents);
                    }
                    else {

                        this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_FORBIDDEN + "!");

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

                    break;
                }
                default: {

                    this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_SERVICE_UNAVAILABLE + "!");

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
                    requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_SERVICE_UNAVAILABLE)));
                    requestError.setCode(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_SERVICE_UNAVAILABLE) + ".description"));
                    requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_SERVICE_UNAVAILABLE) + ".uri"));

                    StringWriter json = new StringWriter();
                    mapper.writeValue(json, requestError);
                    this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                    // send response
                    httpServletResponse.getWriter().println(json);
                }
            }
        }
        finally {

            if (httpResponse !=null) {

                httpResponse.close();
            }
        }
    }

    /**
     *
     * @param httpServletRequest
     * @param httpServletResponse
     * @param service
     * @param patronid
     * @throws IOException
     */
    private void authorize(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String service, String patronid, DocumentList documents) throws IOException {

        ObjectMapper mapper = new ObjectMapper();

        String oAuthzService = this.config.getProperty("service.auth.ubdo.authzendpoint");
        String client_id = this.config.getProperty("service.auth.ubdo.client_id.core");
        String client_secret = this.config.getProperty("service.auth.ubdo.client_secret.core");
        String function = "/";
        if (patronid.equals("")) {
            function += service;
        }
        else {
            function += patronid + "/" + service;
        }

        this.logger.debug("[" + config.getProperty("service.name") + "] " + "HttpHeader.REFERER: " + httpServletRequest.getHeader(HttpHeader.REFERER.asString()));

        String redirect_url = "";

        if (httpServletRequest.getParameter("redirect_url") != null && !httpServletRequest.getParameter("redirect_url").equals("")) {

            redirect_url = URLEncoder.encode(httpServletRequest.getParameter("redirect_url"), "UTF-8");
        }
        else {
            redirect_url = URLEncoder.encode(httpServletRequest.getHeader(HttpHeader.REFERER.asString()).split("&patron=")[0], "UTF-8");
        }

        String redirect_uri = URLEncoder.encode(this.config.getProperty("service.base_url") + this.config.getProperty("service.appPath.core")
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
                        .setState(state);

                if (documents != null && documents.getDoc() != null) {
                        authenticationRequestBuilder.setParameter("document_id", documents.getDoc().get(0).getEdition());
                }

                OAuthClientRequest request = authenticationRequestBuilder.buildQueryMessage();

                this.logger.debug("[" + config.getProperty("service.name") + "] " + "Code-URI: " + request.getLocationUri());

                httpServletResponse.sendRedirect(request.getLocationUri());

            } catch (OAuthSystemException e) {

                this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_FORBIDDEN+ ": " + e.getMessage() + "!");

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

                if (documents != null && documents.getDoc() != null) {
                    tokenRequestBuilder.setParameter("document_id", documents.getDoc().get(0).getEdition());
                }

                OAuthClientRequest request = tokenRequestBuilder.buildQueryMessage();

                this.logger.debug("[" + config.getProperty("service.name") + "] " + "Token-URI: " + request.getLocationUri());

                OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());

                OAuthJSONAccessTokenResponse oAuthJSONAccessTokenResponse = oAuthClient.accessToken(request);

                this.logger.debug("[" + config.getProperty("service.name") + "] " + "TokenResponse: " + oAuthJSONAccessTokenResponse.getBody());

                String accessToken = oAuthJSONAccessTokenResponse.getAccessToken();
                String expiresIn = Long.toString(oAuthJSONAccessTokenResponse.getExpiresIn());
                patronid = oAuthJSONAccessTokenResponse.getParam("patronid");

                this.logger.debug("PAIAcore." + service + " kann ausgeführt werden!");

                this.paiaCore(httpServletRequest, httpServletResponse, patronid, accessToken, scope, service, documents);

            } catch (OAuthSystemException e) {

                this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_FORBIDDEN+ ": " + e.getMessage() + "!");

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
            } catch (OAuthProblemException e) {

                this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_FORBIDDEN+ ": " + e.getMessage() + "!");

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
                    httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                }

                // Json für Response body
                RequestError requestError = new RequestError();
                requestError.setError("error." + this.config.getProperty(Integer.toString(HttpServletResponse.SC_FORBIDDEN) + ".2"));
                requestError.setCode(HttpServletResponse.SC_FORBIDDEN);
                requestError.setDescription("error." + this.config.getProperty(Integer.toString(HttpServletResponse.SC_FORBIDDEN) + ".2.description"));
                requestError.setErrorUri("error." + this.config.getProperty(Integer.toString(HttpServletResponse.SC_FORBIDDEN) + ".2.uri"));

                StringWriter json = new StringWriter();
                mapper.writeValue(json, requestError);
                this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                // send response
                httpServletResponse.getWriter().println(json);
            }
        }
    }

    /**
     * PAIAcore services: Prüfe jeweils die scopes und liefere die Daten
     */
    private void paiaCore(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String patronid, String token, String scope, String service, DocumentList documents) throws IOException {

        ObjectMapper mapper = new ObjectMapper();

        if (Lookup.lookupAll(IntegratedLibrarySystem.class).size() > 0) {

            try {
                IntegratedLibrarySystem integratedLibrarySystem = Lookup.lookup(IntegratedLibrarySystem.class);
                // init ILS
                integratedLibrarySystem.init(this.config);

                switch (service) {

                    case "patron": {

                        Patron patron = null;

                        if (scope.equals("write_patron")) {
                            patron = integratedLibrarySystem.patron(patronid, true);
                        }
                        else {
                            patron = integratedLibrarySystem.patron(patronid, false);
                        }

                        if (patron != null) {

                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, patron);
                            this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                            // If request contains parameter 'redirect_uri', then redirect mit access_token and patronid
                            if (httpServletRequest.getParameter("redirect_uri") != null) {
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + "REDIRECT-URI ? " + httpServletRequest.getParameter("redirect_uri"));

                                // sende nicht patronid und token mit redirect_url, falls beide schon enthalten waren
                                // Dann ist die Information der beiden Parameter bereits ausgewertet und muss nicht mehr mitgesendet werden.
                                // TODO Das geht so nicht, da die Parameter damit alternieren: einzige Lösung: verwende die PAIA.login()-Methode
                                if (!httpServletRequest.getParameter("redirect_uri").contains("patron=")) {

                                    if (httpServletRequest.getParameter("redirect_uri").contains("?")) {

                                        httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "&patron=" + patronid + "&token=" + token);

                                    } else {

                                        httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "?patron=" + patronid + "&token=" + token);
                                    }
                                }
                                else {

                                    if (httpServletRequest.getParameter("redirect_uri").contains("?patron")) {

                                        httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri").split("\\?patron=")[0]);

                                    } else if (httpServletRequest.getParameter("redirect_uri").contains("&patron")){

                                        httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri").split("&patron=")[0]);

                                    }
                                    else {
                                        // Tue nix
                                    }
                                }
                            }
                            else {

                                httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_patron");
                                httpServletResponse.setHeader("X-OAuth-Scopes", scope);
                                httpServletResponse.setContentType("application/json");
                                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                httpServletResponse.setHeader("Access-Control-Allow-Origin","*");

                                httpServletResponse.getWriter().println(json);
                            }
                        }
                        else {

                            // TODO Was muss hier passieren?
                        }

                        break;
                    }
                    case "fullpatron": {

                        Patron patron = null;

                        if (scope.equals("write_patron")) {
                            patron = integratedLibrarySystem.patron(patronid, true);
                        }
                        else {
                            patron = integratedLibrarySystem.patron(patronid, false);
                        }

                        if (patron != null) {

                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, patron);
                            this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                            // If request contains parameter 'redirect_uri', then redirect mit access_token and patronid
                            if (httpServletRequest.getParameter("redirect_uri") != null) {
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + "REDIRECT-URI ? " + httpServletRequest.getParameter("redirect_uri"));

                                if (httpServletRequest.getParameter("redirect_uri").contains("?")) {
                                    httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "&patron=" + patronid + "&token=" + token);
                                }
                                else {
                                    httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "?patron=" + patronid + "&token=" + token);
                                }
                            }
                            else {

                                httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "write_patron");
                                httpServletResponse.setHeader("X-OAuth-Scopes", scope);
                                httpServletResponse.setContentType("application/json");
                                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                httpServletResponse.setHeader("Access-Control-Allow-Origin","*");

                                httpServletResponse.getWriter().println(json);
                            }
                        }
                        else {

                            // TODO Was muss hier passieren?
                        }

                        break;
                    }
                    case "items": {

                        DocumentList documentList = null;

                        documentList = integratedLibrarySystem.items(patronid, "all");

                        if (documentList != null) {
                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, documentList);
                            this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                            // If request contains parameter 'redirect_uri', then redirect mit access_token and patronid
                            if (httpServletRequest.getParameter("redirect_uri") != null) {
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + "REDIRECT-URI ? " + httpServletRequest.getParameter("redirect_uri"));

                                if (httpServletRequest.getParameter("redirect_uri").contains("?")) {
                                    httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "&patron=" + patronid + "&token=" + token);
                                }
                                else {
                                    httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "?patron=" + patronid + "&token=" + token);
                                }
                            }
                            else {

                                httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_items");
                                httpServletResponse.setHeader("X-OAuth-Scopes", scope);
                                httpServletResponse.setContentType("application/json");
                                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                httpServletResponse.setHeader("Access-Control-Allow-Origin","*");

                                httpServletResponse.getWriter().println(json);
                            }
                        }
                        else {

                            // TODO Was muss hier passieren?
                        }

                        break;
                    }
                    case "items/borrowed": {

                        DocumentList documentList = null;

                        documentList = integratedLibrarySystem.items(patronid, "borrowed");

                        if (documentList != null) {
                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, documentList);
                            this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                            // If request contains parameter 'redirect_uri', then redirect mit access_token and patronid
                            if (httpServletRequest.getParameter("redirect_uri") != null) {
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + "REDIRECT-URI ? " + httpServletRequest.getParameter("redirect_uri"));

                                if (httpServletRequest.getParameter("redirect_uri").contains("?")) {
                                    httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "&patron=" + patronid + "&token=" + token);
                                }
                                else {
                                    httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "?patron=" + patronid + "&token=" + token);
                                }
                            }
                            else {

                                httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_items");
                                httpServletResponse.setHeader("X-OAuth-Scopes", scope);
                                httpServletResponse.setContentType("application/json");
                                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                httpServletResponse.setHeader("Access-Control-Allow-Origin","*");

                                httpServletResponse.getWriter().println(json);
                            }
                        }
                        else {

                            // TODO Was muss hier passieren?
                        }

                        break;
                    }
                    case "items/ordered": {

                        DocumentList documentList = null;

                        documentList = integratedLibrarySystem.items(patronid, "ordered");

                        if (documentList != null) {
                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, documentList);
                            this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                            // If request contains parameter 'redirect_uri', then redirect mit access_token and patronid
                            if (httpServletRequest.getParameter("redirect_uri") != null) {
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + "REDIRECT-URI ? " + httpServletRequest.getParameter("redirect_uri"));

                                if (httpServletRequest.getParameter("redirect_uri").contains("?")) {
                                    httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "&patron=" + patronid + "&token=" + token);
                                }
                                else {
                                    httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "?patron=" + patronid + "&token=" + token);
                                }
                            }
                            else {

                                httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_items");
                                httpServletResponse.setHeader("X-OAuth-Scopes", scope);
                                httpServletResponse.setContentType("application/json");
                                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                httpServletResponse.setHeader("Access-Control-Allow-Origin","*");

                                httpServletResponse.getWriter().println(json);
                            }
                        }
                        else {

                            // TODO Was muss hier passieren?
                        }

                        break;
                    }
                    case "items/reserved": {

                        DocumentList documentList = null;

                        documentList = integratedLibrarySystem.items(patronid, "reserved");

                        if (documentList != null) {
                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, documentList);
                            this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                            // If request contains parameter 'redirect_uri', then redirect mit access_token and patronid
                            if (httpServletRequest.getParameter("redirect_uri") != null) {
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + "REDIRECT-URI ? " + httpServletRequest.getParameter("redirect_uri"));

                                if (httpServletRequest.getParameter("redirect_uri").contains("?")) {
                                    httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "&patron=" + patronid + "&token=" + token);
                                }
                                else {
                                    httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "?patron=" + patronid + "&token=" + token);
                                }
                            }
                            else {

                                httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_items");
                                httpServletResponse.setHeader("X-OAuth-Scopes", scope);
                                httpServletResponse.setContentType("application/json");
                                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                httpServletResponse.setHeader("Access-Control-Allow-Origin","*");

                                httpServletResponse.getWriter().println(json);
                            }
                        }
                        else {

                            // TODO Was muss hier passieren?
                        }

                        break;
                    }
                    case "request": {

                        DocumentList documentList = null;

                        documentList = integratedLibrarySystem.request(patronid, documents);

                        if (documentList != null) {

                            if (documentList.getDoc() != null && documentList.getDoc().size() > 0 && documentList.getDoc().get(0).getError() != null && !documentList.getDoc().get(0).getError().equals("")) {

                                StringWriter json = new StringWriter();
                                mapper.writeValue(json, documentList);
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                                this.logger.debug("[" + config.getProperty("service.name") + "] " + httpServletRequest.getParameter("redirect_uri"));
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + httpServletRequest.getParameter("document_id"));

                                if (httpServletRequest.getParameter("redirect_uri") != null && !httpServletRequest.getParameter("redirect_uri").equals("") && httpServletRequest.getParameter("document_id") != null && !httpServletRequest.getParameter("document_id").equals("")) {

                                    String url = URLDecoder.decode(httpServletRequest.getParameter("redirect_uri"), "UTF-8");
                                    url += "&patron=" + patronid + "&token=" + token;
                                    url += "&document_id=" + httpServletRequest.getParameter("document_id");
                                    url += "&error_request=" + documentList.getDoc().get(0).getError();

                                    this.logger.debug("[" + config.getProperty("service.name") + "] " + "REDIRECT-URL: " + url);

                                    httpServletResponse.sendRedirect(url);

                                } else {

                                    httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "write_items");
                                    httpServletResponse.setHeader("X-OAuth-Scopes", scope);
                                    httpServletResponse.setContentType("application/json");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                    httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");

                                    httpServletResponse.getWriter().println(json);
                                }

                            } else {

                                StringWriter json = new StringWriter();
                                mapper.writeValue(json, documentList);
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                                this.logger.debug("[" + config.getProperty("service.name") + "] " + httpServletRequest.getParameter("redirect_uri"));
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + httpServletRequest.getParameter("document_id"));

                                if (httpServletRequest.getParameter("redirect_uri") != null && !httpServletRequest.getParameter("redirect_uri").equals("") && httpServletRequest.getParameter("document_id") != null && !httpServletRequest.getParameter("document_id").equals("")) {

                                    String url = URLDecoder.decode(httpServletRequest.getParameter("redirect_uri"), "UTF-8");
                                    url += "&patron=" + patronid + "&token=" + token;
                                    url += "&document_id=" + httpServletRequest.getParameter("document_id");
                                    if (documentList.getDoc() != null && documentList.getDoc().size() > 0 && documentList.getDoc().get(0).getError() != null) {
                                        url += "&error_request=" + documentList.getDoc().get(0).getError();
                                    }

                                    this.logger.debug("[" + config.getProperty("service.name") + "] " + "REDIRECT-URL: " + url);

                                    httpServletResponse.sendRedirect(url);

                                } else {
                                    httpServletResponse.setContentType("application/json");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                    httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");

                                    httpServletResponse.getWriter().println(json);
                                }
                            }
                        }
                        else {

                            // TODO Was muss hier passieren?
                        }

                        break;
                    }
                    case "renew": {

                        DocumentList documentList = null;

                        documentList = integratedLibrarySystem.renew(patronid, documents);

                        if (documentList != null) {

                            if (documentList.getDoc() != null && documentList.getDoc().size() > 0 && documentList.getDoc().get(0).getError() != null && !documentList.getDoc().get(0).getError().equals("")) {

                                StringWriter json = new StringWriter();
                                mapper.writeValue(json, documentList);
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                                this.logger.debug("[" + config.getProperty("service.name") + "] " + httpServletRequest.getParameter("redirect_uri"));
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + httpServletRequest.getParameter("document_id"));

                                if (httpServletRequest.getParameter("redirect_uri") != null && !httpServletRequest.getParameter("redirect_uri").equals("") && httpServletRequest.getParameter("document_id") != null && !httpServletRequest.getParameter("document_id").equals("")) {

                                    String url = URLDecoder.decode(httpServletRequest.getParameter("redirect_uri"), "UTF-8");
                                    url += "&patron=" + patronid + "&token=" + token;
                                    url += "&document_id=" + httpServletRequest.getParameter("document_id");
                                    if (documentList.getDoc() != null && documentList.getDoc().size() > 0 && documentList.getDoc().get(0).getError() != null) {
                                        url += "&error_request=" + documentList.getDoc().get(0).getError();
                                    }

                                    this.logger.debug("REDIRECT-URL: " + url);

                                    httpServletResponse.sendRedirect(url);

                                } else {

                                    httpServletResponse.setContentType("application/json");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                    httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");

                                    httpServletResponse.getWriter().println(json);
                                }

                            }
                            else {

                                StringWriter json = new StringWriter();
                                mapper.writeValue(json, documentList);
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                                this.logger.debug("[" + config.getProperty("service.name") + "] " + httpServletRequest.getParameter("redirect_uri"));
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + httpServletRequest.getParameter("document_id"));

                                if (httpServletRequest.getParameter("redirect_uri") != null && !httpServletRequest.getParameter("redirect_uri").equals("") && httpServletRequest.getParameter("document_id") != null && !httpServletRequest.getParameter("document_id").equals("")) {

                                    String url = URLDecoder.decode(httpServletRequest.getParameter("redirect_uri"), "UTF-8");
                                    url += "&patron=" + patronid + "&token=" + token;
                                    url += "&document_id=" + httpServletRequest.getParameter("document_id");

                                    this.logger.debug("[" + config.getProperty("service.name") + "] " + "REDIRECT-URL: " + url);

                                    httpServletResponse.sendRedirect(url);

                                } else {

                                    httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "write_items");
                                    httpServletResponse.setHeader("X-OAuth-Scopes", scope);
                                    httpServletResponse.setContentType("application/json");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                    httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");

                                    httpServletResponse.getWriter().println(json);
                                }
                            }
                        }
                        else {

                            // TODO Was muss hier passieren?
                        }

                        break;
                    }
                    case "cancel": {

                        DocumentList documentList = null;

                        documentList = integratedLibrarySystem.cancel(patronid, documents);

                        if (documentList != null) {

                            if (documentList.getDoc() != null && documentList.getDoc().size() > 0 && documentList.getDoc().get(0).getError() != null && !documentList.getDoc().get(0).getError().equals("")) {

                                StringWriter json = new StringWriter();
                                mapper.writeValue(json, documentList);
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                                this.logger.debug("[" + config.getProperty("service.name") + "] " + httpServletRequest.getParameter("redirect_uri"));
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + httpServletRequest.getParameter("document_id"));

                                if (httpServletRequest.getParameter("redirect_uri") != null && !httpServletRequest.getParameter("redirect_uri").equals("") && httpServletRequest.getParameter("document_id") != null && !httpServletRequest.getParameter("document_id").equals("")) {

                                    String url = URLDecoder.decode(httpServletRequest.getParameter("redirect_uri"), "UTF-8");
                                    url += "&patron=" + patronid + "&token=" + token;
                                    url += "&document_id=" + httpServletRequest.getParameter("document_id");
                                    url += "&error_request=" + documentList.getDoc().get(0).getError();

                                    this.logger.debug("[" + config.getProperty("service.name") + "] " + "REDIRECT-URL: " + url);

                                    httpServletResponse.sendRedirect(url);

                                } else {

                                    httpServletResponse.setContentType("application/json");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                    httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");

                                    httpServletResponse.getWriter().println(json);
                                }

                            }
                            else {

                                StringWriter json = new StringWriter();
                                mapper = new ObjectMapper();
                                mapper.writeValue(json, documentList);
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                                // If request contains parameter 'redirect_uri', then redirect mit access_token and patronid
                                if (httpServletRequest.getParameter("redirect_uri") != null) {
                                    this.logger.debug("[" + config.getProperty("service.name") + "] " + "REDIRECT-URI ? " + httpServletRequest.getParameter("redirect_uri"));

                                    if (httpServletRequest.getParameter("redirect_uri").contains("?")) {
                                        httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "&patron=" + patronid + "&token=" + token);
                                    } else {
                                        httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "?patron=" + patronid + "&token=" + token);
                                    }

                                } else {

                                    httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "write_items");
                                    httpServletResponse.setHeader("X-OAuth-Scopes", scope);
                                    httpServletResponse.setContentType("application/json");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                    httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");

                                    httpServletResponse.getWriter().println(json);
                                }
                            }
                        }
                        else {

                            // TODO Was muss hier passieren?
                        }

                        break;
                    }
                    case "fees": {

                        FeeList feeList = null;

                        feeList = integratedLibrarySystem.fees(patronid);

                        if (feeList != null) {
                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, feeList);
                            this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

                            // If request contains parameter 'redirect_uri', then redirect mit access_token and patronid
                            if (httpServletRequest.getParameter("redirect_uri") != null) {
                                this.logger.debug("[" + config.getProperty("service.name") + "] " + "REDIRECT-URI ? " + httpServletRequest.getParameter("redirect_uri"));

                                if (httpServletRequest.getParameter("redirect_uri").contains("?")) {
                                    httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "&patron=" + patronid + "&token=" + token);
                                }
                                else {
                                    httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_uri") + "?patron=" + patronid + "&token=" + token);
                                }
                            }
                            else {

                                httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_fees");
                                httpServletResponse.setHeader("X-OAuth-Scopes", scope);
                                httpServletResponse.setContentType("application/json");
                                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                httpServletResponse.setHeader("Access-Control-Allow-Origin","*");

                                httpServletResponse.getWriter().println(json);
                            }
                        }
                        else {

                            // TODO Was muss hier passieren?
                        }

                        break;
                    }
                }
            }
            catch (ILSException e) {

                StringWriter json = new StringWriter();

                if (e.getMessage().contains("570-unknown patron")) {

                    this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_NOT_FOUND + ": '" + patronid + "'");

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
                        httpServletResponse.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    }

                    // Json für Response body
                    RequestError requestError = new RequestError();
                    requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_NOT_FOUND)));
                    requestError.setCode(HttpServletResponse.SC_NOT_FOUND);
                    requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_NOT_FOUND) + ".description"));
                    requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_NOT_FOUND) + ".uri"));

                    mapper.writeValue(json, requestError);
                    this.logger.debug("[" + config.getProperty("service.name") + "] " + json);
                }
                else {

                    this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_SERVICE_UNAVAILABLE + ": ILS!");

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
                    requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_SERVICE_UNAVAILABLE)));
                    requestError.setCode(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                    requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_SERVICE_UNAVAILABLE) + ".description"));
                    requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_SERVICE_UNAVAILABLE) + ".uri"));

                    mapper.writeValue(json, requestError);
                    this.logger.debug("[" + config.getProperty("service.name") + "] " + json);
                }

                // send response
                httpServletResponse.getWriter().println(json);
            }
            catch (Exception e) {

                e.printStackTrace();
            }
        }
        else {

            this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_SERVICE_UNAVAILABLE+ ": Config Error!");

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
            requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_SERVICE_UNAVAILABLE)));
            requestError.setCode(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_SERVICE_UNAVAILABLE) + ".description"));
            requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_SERVICE_UNAVAILABLE) + ".uri"));

            StringWriter json = new StringWriter();
            mapper.writeValue(json, requestError);
            this.logger.debug("[" + config.getProperty("service.name") + "] " + json);

            // send response
            httpServletResponse.getWriter().println(json);
        }
    }
}
