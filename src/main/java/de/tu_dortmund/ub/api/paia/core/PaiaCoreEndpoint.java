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
import de.tu_dortmund.ub.api.paia.PaiaServiceException;
import de.tu_dortmund.ub.api.paia.auth.model.LoginResponse;
import de.tu_dortmund.ub.api.paia.core.ils.ILSException;
import de.tu_dortmund.ub.api.paia.core.ils.IntegratedLibrarySystem;
import de.tu_dortmund.ub.api.paia.core.model.Patron;
import de.tu_dortmund.ub.api.paia.core.model.Document;
import de.tu_dortmund.ub.api.paia.core.model.DocumentList;
import de.tu_dortmund.ub.api.paia.core.model.FeeList;
import de.tu_dortmund.ub.api.paia.model.RequestError;
import de.tu_dortmund.ub.util.impl.Lookup;
import net.sf.saxon.s9api.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.transform.JDOMSource;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
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

    private String format;
    private String language;
    private String redirect_url;

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

        this.doPost(httpServletRequest,httpServletResponse);
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
        this.logger.debug("[" + config.getProperty("service.name") + "] " + "Service: " + service);
        this.logger.debug("[" + config.getProperty("service.name") + "] " + "Patron: " + patronid);

        this.format = "html";

        if (httpServletRequest.getParameter("format") != null && !httpServletRequest.getParameter("format").equals("")) {

            this.format = httpServletRequest.getParameter("format");
        }
        else {

            Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
            while ( headerNames.hasMoreElements() ) {
                String headerNameKey = headerNames.nextElement();

                if (headerNameKey.equals("Accept")) {

                    this.logger.debug("headerNameKey = " + httpServletRequest.getHeader( headerNameKey ));

                    if (httpServletRequest.getHeader( headerNameKey ).contains("text/html")) {
                        this.format = "html";
                    }
                    else if (httpServletRequest.getHeader( headerNameKey ).contains("application/xml")) {
                        this.format = "xml";
                    }
                    else if (httpServletRequest.getHeader( headerNameKey ).contains("application/json")) {
                        this.format = "json";
                    }
                }
            }
        }

        this.logger.info("format = " + this.format);

        this.redirect_url = "";

        if (httpServletRequest.getParameter("redirect_url") != null && !httpServletRequest.getParameter("redirect_url").equals("")) {

            this.redirect_url = httpServletRequest.getParameter("redirect_url");
        }

        this.logger.info("redirect_url = " + this.redirect_url);

        httpServletResponse.setHeader("Access-Control-Allow-Origin","*");

        this.language = "";

        // PAIA core - function
        if ( (httpServletRequest.getMethod().equals("GET") && (service.equals("patron") || service.equals("fullpatron") || service.equals("items") || service.startsWith("items/ordered") || service.startsWith("items/borrowed") || service.startsWith("items/reserved") || service.equals("fees")) ) ||
                (httpServletRequest.getMethod().equals("POST") && (service.equals("request") || service.equals("renew") || service.equals("cancel"))) ) {

            // 1. Schritt: Hole 'Accept' und 'Authorization' aus dem Header;
            Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
            while ( headerNames.hasMoreElements() ) {

                String headerNameKey = (String) headerNames.nextElement();
                this.logger.debug("[" + config.getProperty("service.name") + "] " + "headerNameKey = " + headerNameKey + " / headerNameValue = " + httpServletRequest.getHeader(headerNameKey));

                if (headerNameKey.equals("Accept-Language")) {
                    this.language = httpServletRequest.getHeader( headerNameKey );
                    this.logger.debug("[" + config.getProperty("service.name") + "] " + "Accept-Language: " + this.language);
                }
                if (headerNameKey.equals("Accept")) {
                    accept = httpServletRequest.getHeader( headerNameKey );
                    this.logger.debug("[" + config.getProperty("service.name") + "] " + "Accept: " + accept);
                }
                if (headerNameKey.equals("Authorization")) {
                    authorization = httpServletRequest.getHeader( headerNameKey );
                }
            }

            // language
            if (this.language.startsWith("de")) {
                this.language = "de";
            }
            else if (this.language.startsWith("en")) {
                this.language = "en";
            }
            else if (httpServletRequest.getParameter("l") != null) {
                this.language = httpServletRequest.getParameter("l");
            }
            else {
                this.language = "de";
            }

            // read rquestBody
            StringBuffer jb = new StringBuffer();
            String line = null;
            try {
                BufferedReader reader = httpServletRequest.getReader();
                while ((line = reader.readLine()) != null)
                    jb.append(line);
            } catch (Exception e) { /*report an error*/ }

            String requestBody = jb.toString();

            // if not exists token: read request parameter
            if (authorization.equals("") && httpServletRequest.getParameter("access_token") != null && !httpServletRequest.getParameter("access_token").equals("")) {
                authorization = httpServletRequest.getParameter("access_token");
            }

            // if not exists token
            if (authorization.equals("")) {

                // if exists PaiaService-Cookie: read content
                Cookie[] cookies = httpServletRequest.getCookies();

                if (cookies != null) {
                    for (Cookie cookie : cookies) {
                        if (cookie.getName().equals("PaiaService")) {

                            String value = URLDecoder.decode(cookie.getValue(), "UTF-8");
                            this.logger.info(value);
                            LoginResponse loginResponse = mapper.readValue(value, LoginResponse.class);

                            // A C H T U N G: ggf. andere patronID im Cookie als in Request (UniAccount vs. BibAccount)
                            if (loginResponse.getPatron().equals(patronid)) {
                                authorization = loginResponse.getAccess_token();
                            }

                            break;
                        }
                    }
                }
            }

            // check token ...
            try {

                boolean isAuthorized = false;

                if (!authorization.equals("")) {
                    // ... against local OAuth 2.0 service
                    isAuthorized = this.checkToken(httpServletResponse, service, authorization);
                }
                this.logger.debug("[" + config.getProperty("service.name") + "] " + "Authorization: " + authorization + " - " + isAuthorized);

                // ... - if not is authorized - against DFN-AAI service
                if (!isAuthorized) {

                    // TODO if exists OpenAM-Session-Cookie: read content
                    this.logger.debug("[" + config.getProperty("service.name") + "] " + "Authorization: " + authorization + " - " + isAuthorized);
                }

                // read document list
                DocumentList documentList = null;

                try {

                    // read DocumentList
                    documentList = mapper.readValue(requestBody, DocumentList.class);
                }
                catch (Exception e) {

                    if (!requestBody.equals("")) {

                        String[] params = requestBody.split("&");

                        if (params.length > 1) {

                            documentList = new DocumentList();
                            documentList.setDoc(new ArrayList<Document>());

                            for (String param : params) {

                                if (param.startsWith("document_id")) {
                                    Document document = new Document();
                                    document.setEdition(param.split("=")[1]);
                                    documentList.getDoc().add(document);
                                }
                            }
                        }
                    }
                    else if (httpServletRequest.getParameter("document_id") != null && !httpServletRequest.getParameter("document_id").equals("")) {

                        Document document = new Document();
                        document.setEdition(httpServletRequest.getParameter("document_id"));

                        documentList = new DocumentList();
                        documentList.setDoc(new ArrayList<Document>());
                        documentList.getDoc().add(document);
                    }
                    else {

                        // if exists cookie with name "PaiaServiceDocumentList": read it
                        Cookie[] cookies = httpServletRequest.getCookies();

                        if (cookies != null) {
                            for (Cookie cookie : cookies) {
                                if (cookie.getName().equals("PaiaServiceDocumentList")) {

                                    if (cookie.getValue() != null && !cookie.getValue().equals("") && !cookie.getValue().equals("null")) {

                                        String value = URLDecoder.decode(cookie.getValue(), "UTF-8");
                                        this.logger.info(value);
                                        documentList = mapper.readValue(value, DocumentList.class);
                                    }

                                    break;
                                }
                            }
                        }
                    }
                }

                if (isAuthorized) {

                    // execute query
                    this.paiaCore(httpServletRequest, httpServletResponse, patronid, service, documentList);
                }
                else {

                    // Authorization
                    this.authorize(httpServletRequest, httpServletResponse, service, patronid, documentList);
                }

            }
            catch (PaiaServiceException e) {

                // Error handling mit suppress_response_codes=true
                if (httpServletRequest.getParameter("suppress_response_codes") != null) {
                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                }
                // Error handling mit suppress_response_codes=false (=default)
                else {
                    httpServletResponse.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                }

                RequestError requestError = new RequestError();
                requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_SERVICE_UNAVAILABLE)));
                requestError.setCode(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
                requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_SERVICE_UNAVAILABLE) + ".description"));
                requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_SERVICE_UNAVAILABLE) + ".uri"));

                this.sendRequestError(httpServletResponse, requestError);
            }
        }
        else {

            this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_METHOD_NOT_ALLOWED + ": " + httpServletRequest.getMethod() + " for '" + service + "' not allowed!");

            // Error handling mit suppress_response_codes=true
            if (httpServletRequest.getParameter("suppress_response_codes") != null) {
                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
            }
            // Error handling mit suppress_response_codes=false (=default)
            else {
                httpServletResponse.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }

            RequestError requestError = new RequestError();
            requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_METHOD_NOT_ALLOWED)));
            requestError.setCode(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_METHOD_NOT_ALLOWED) + ".description"));
            requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_METHOD_NOT_ALLOWED) + ".uri"));

            this.sendRequestError(httpServletResponse, requestError);
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
     * @param httpServletResponse
     * @param service
     * @param access_token
     * @return
     * @throws IOException
     */
    private boolean checkToken(HttpServletResponse httpServletResponse, String service, String access_token) throws PaiaServiceException {

        boolean isAuthorized = false;

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

        try {

            if (this.config.getProperty("service.auth.ubdo.tokenendpoint") != null && !this.config.getProperty("service.auth.ubdo.tokenendpoint").equals("")) {

                String url = this.config.getProperty("service.auth.ubdo.tokenendpoint") + "/validate?token=" + access_token;
                this.logger.debug("[" + config.getProperty("service.name") + "] " + "TOKEN-ENDPOINT-URL: " + url);
                HttpGet httpGet = new HttpGet(url);

                CloseableHttpResponse httpResponse = httpclient.execute(httpGet);

                try {

                    int statusCode = httpResponse.getStatusLine().getStatusCode();
                    HttpEntity httpEntity = httpResponse.getEntity();

                    switch (statusCode) {

                        case HttpServletResponse.SC_OK: {

                            // {"valid":true,"scope":[scope],"created":[time_created],"token":[access_token],"expiresIn":[expiration],"userId":"","refreshToken":"","type":"Bearer","clientId":[client_id]}
                            StringWriter writer = new StringWriter();
                            IOUtils.copy(httpEntity.getContent(), writer, "UTF-8");
                            String responseJson = writer.toString();

                            logger.info("[" + config.getProperty("service.name") + "] responseJson : " + responseJson);

                            // prüfe 'valid' und 'scopes'
                            JsonReader jsonReader = Json.createReader(IOUtils.toInputStream(responseJson, "UTF-8"));
                            JsonObject jsonObject = jsonReader.readObject();

                            boolean valid = jsonObject.getBoolean("valid");
                            String scopes = jsonObject.getString("scope");

                            // valid token?
                            if (valid && scopes.contains(scope)) {

                                this.logger.debug("[" + config.getProperty("service.name") + "] " + "PaiaService." + service + " kann ausgeführt werden!");

                                isAuthorized = true;
                                httpServletResponse.setHeader("X-OAuth-Scopes", scope);
                            }
                            else {

                                // valid api_key?
                                if (apikeys != null && apikeys.containsKey(access_token) && apikeys.getProperty(access_token).contains(scope)) {

                                    this.logger.debug("[" + config.getProperty("service.name") + "] " + "PaiaService." + service + " kann ausgeführt werden!");

                                    isAuthorized = true;
                                    httpServletResponse.setHeader("X-OAuth-Scopes", scope);
                                }
                                else {

                                    isAuthorized = false;
                                }
                            }

                            break;
                        }
                        case HttpServletResponse.SC_UNAUTHORIZED: {

                            // valid api_key?
                            if (apikeys != null && apikeys.containsKey(access_token) && apikeys.getProperty(access_token).contains(scope)) {

                                this.logger.debug("[" + config.getProperty("service.name") + "] " + "PaiaService." + service + " kann ausgeführt werden!");

                                isAuthorized = true;
                                httpServletResponse.setHeader("X-OAuth-Scopes", scope);
                            }
                            else {

                                isAuthorized = false;
                            }

                            break;
                        }
                        default: {

                            this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_SERVICE_UNAVAILABLE + " - Unable to validate the given token!");

                            throw new PaiaServiceException(HttpServletResponse.SC_SERVICE_UNAVAILABLE + " - Unable to validate the given token!");
                        }
                    }

                    EntityUtils.consume(httpEntity);
                } finally {
                    httpResponse.close();
                }
            }
        }
        catch (IOException e) {

            throw new PaiaServiceException(HttpServletResponse.SC_SERVICE_UNAVAILABLE + " - Unable to validate the given token!");
        }
        finally {

            try {

                httpclient.close();

            }
            catch (IOException e) {

                throw new PaiaServiceException(HttpServletResponse.SC_SERVICE_UNAVAILABLE + " - Unable to validate the given token!");
            }
        }

        return isAuthorized;
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

        // Error handling mit suppress_response_codes=true
        if (httpServletRequest.getParameter("suppress_response_codes") != null) {
            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
        }
        // Error handling mit suppress_response_codes=false (=default)
        else {
            httpServletResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }

        // Json für Response body
        RequestError requestError = new RequestError();
        requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_UNAUTHORIZED)));
        requestError.setCode(HttpServletResponse.SC_UNAUTHORIZED);
        requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_UNAUTHORIZED) + ".description"));
        requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_UNAUTHORIZED) + ".uri"));

        // XML-Ausgabe mit JAXB
        if (this.format.equals("xml")) {

            try {

                JAXBContext context = JAXBContext.newInstance(RequestError.class);
                Marshaller m = context.createMarshaller();
                m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                // Write to HttpResponse
                httpServletResponse.setContentType("application/xml;charset=UTF-8");
                m.marshal(requestError, httpServletResponse.getWriter());

            } catch (JAXBException e) {
                this.logger.error(e.getMessage(), e.getCause());
                httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering the results.");
            }
        }

        // JSON-Ausgabe mit Jackson
        if (this.format.equals("json")) {

            httpServletResponse.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(httpServletResponse.getWriter(), requestError);
        }

        // html > redirect zu "PAIA auth - login" mit redirect_url = "PAIA core - service"
        if (this.format.equals("html")) {

            if (documents != null) {
                // set Cookie with urlencoded DocumentList-JSON
                StringWriter stringWriter = new StringWriter();
                mapper.writeValue(stringWriter, documents);
                Cookie cookie = new Cookie("PaiaServiceDocumentList", URLEncoder.encode(stringWriter.toString(), "UTF-8"));
                cookie.setMaxAge(-1);
                cookie.setPath("/");
                httpServletResponse.addCookie(cookie);
            }

            String redirect_url = "http://" + httpServletRequest.getServerName() + ":" + httpServletRequest.getServerPort() + this.config.getProperty("service.endpoint.core") + httpServletRequest.getPathInfo();
            String login_url = "http://" + httpServletRequest.getServerName() + ":" + httpServletRequest.getServerPort() + this.config.getProperty("service.endpoint.auth") + "/login?redirect_url=" + redirect_url;

            httpServletResponse.sendRedirect(login_url);
        }
    }

    /**
     * PAIAcore services: Prüfe jeweils die scopes und liefere die Daten
     */
    private void paiaCore(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String patronid, String service, DocumentList documents) throws IOException {

        ObjectMapper mapper = new ObjectMapper();

        if (Lookup.lookupAll(IntegratedLibrarySystem.class).size() > 0) {

            try {
                IntegratedLibrarySystem integratedLibrarySystem = Lookup.lookup(IntegratedLibrarySystem.class);
                // init ILS
                integratedLibrarySystem.init(this.config);

                switch (service) {

                    case "patron": {

                        de.tu_dortmund.ub.api.paia.core.ils.model.Patron patron = integratedLibrarySystem.patron(patronid, false);

                        if (patron != null) {

                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, patron);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_patron");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            // XML-Ausgabe mit JAXB
                            if (this.format.equals("xml")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(Patron.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    httpServletResponse.setContentType("application/xml;charset=UTF-8");
                                    m.marshal(patron, httpServletResponse.getWriter());

                                } catch (JAXBException e) {
                                    this.logger.error(e.getMessage(), e.getCause());
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering the results.");
                                }
                            }

                            // JSON-Ausgabe mit Jackson
                            if (this.format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), patron);
                            }

                            // html
                            if (this.format.equals("html")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(Patron.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    StringWriter stringWriter = new StringWriter();
                                    m.marshal(patron, stringWriter);

                                    org.jdom2.Document doc = new SAXBuilder().build(new StringReader(stringWriter.toString()));

                                    HashMap<String,String> parameters = new HashMap<String,String>();
                                    parameters.put("lang", this.language);
                                    parameters.put("service", service);

                                    String html = htmlOutputter(doc, this.config.getProperty("service.endpoint.core.service.xslt"), parameters);

                                    httpServletResponse.setContentType("text/html;charset=UTF-8");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                    httpServletResponse.getWriter().println(html);

                                }
                                catch (JAXBException | JDOMException e) {
                                    e.printStackTrace();
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                }
                            }
                        }
                        else {

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

                            this.sendRequestError(httpServletResponse, requestError);
                        }

                        break;
                    }
                    case "fullpatron": {

                        de.tu_dortmund.ub.api.paia.core.ils.model.Patron patron = integratedLibrarySystem.patron(patronid, true);

                        if (patron != null) {

                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, patron);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "write_patron");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            // XML-Ausgabe mit JAXB
                            if (this.format.equals("xml")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(Patron.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    httpServletResponse.setContentType("application/xml;charset=UTF-8");
                                    m.marshal(patron, httpServletResponse.getWriter());

                                } catch (JAXBException e) {
                                    this.logger.error(e.getMessage(), e.getCause());
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering the results.");
                                }
                            }

                            // JSON-Ausgabe mit Jackson
                            if (this.format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), patron);
                            }

                            // html
                            if (this.format.equals("html")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(Patron.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    StringWriter stringWriter = new StringWriter();
                                    m.marshal(patron, stringWriter);

                                    org.jdom2.Document doc = new SAXBuilder().build(new StringReader(stringWriter.toString()));

                                    HashMap<String,String> parameters = new HashMap<String,String>();
                                    parameters.put("lang", this.language);
                                    parameters.put("service", service);

                                    String html = htmlOutputter(doc, this.config.getProperty("service.endpoint.core.service.xslt"), parameters);

                                    httpServletResponse.setContentType("text/html;charset=UTF-8");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                    httpServletResponse.getWriter().println(html);

                                }
                                catch (JAXBException | JDOMException e) {
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                }
                            }
                        }
                        else {

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

                            this.sendRequestError(httpServletResponse, requestError);
                        }

                        break;
                    }
                    case "items": {

                        DocumentList documentList = integratedLibrarySystem.items(patronid, "all");

                        if (documentList != null) {
                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, documentList);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_items");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            // XML-Ausgabe mit JAXB
                            if (this.format.equals("xml")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(DocumentList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    httpServletResponse.setContentType("application/xml;charset=UTF-8");
                                    m.marshal(documentList, httpServletResponse.getWriter());

                                } catch (JAXBException e) {
                                    this.logger.error(e.getMessage(), e.getCause());
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering the results.");
                                }
                            }

                            // JSON-Ausgabe mit Jackson
                            if (this.format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
                            }

                            // html
                            if (this.format.equals("html")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(DocumentList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    StringWriter stringWriter = new StringWriter();
                                    m.marshal(documentList, stringWriter);

                                    org.jdom2.Document doc = new SAXBuilder().build(new StringReader(stringWriter.toString()));

                                    HashMap<String,String> parameters = new HashMap<String,String>();
                                    parameters.put("lang", this.language);
                                    parameters.put("service", service);

                                    String html = htmlOutputter(doc, this.config.getProperty("service.endpoint.core.service.xslt"), parameters);

                                    httpServletResponse.setContentType("text/html;charset=UTF-8");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                    httpServletResponse.getWriter().println(html);

                                }
                                catch (JAXBException | JDOMException e) {
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                }
                            }
                        }
                        else {

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

                            this.sendRequestError(httpServletResponse, requestError);
                        }

                        break;
                    }
                    case "items/borrowed": {

                        DocumentList documentList = integratedLibrarySystem.items(patronid, "borrowed");

                        if (documentList != null) {
                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, documentList);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_items");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            // XML-Ausgabe mit JAXB
                            if (this.format.equals("xml")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(DocumentList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    httpServletResponse.setContentType("application/xml;charset=UTF-8");
                                    m.marshal(documentList, httpServletResponse.getWriter());

                                } catch (JAXBException e) {
                                    this.logger.error(e.getMessage(), e.getCause());
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering the results.");
                                }
                            }

                            // JSON-Ausgabe mit Jackson
                            if (this.format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
                            }

                            // html
                            if (this.format.equals("html")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(DocumentList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    StringWriter stringWriter = new StringWriter();
                                    m.marshal(documentList, stringWriter);

                                    org.jdom2.Document doc = new SAXBuilder().build(new StringReader(stringWriter.toString()));

                                    HashMap<String,String> parameters = new HashMap<String,String>();
                                    parameters.put("lang", this.language);
                                    parameters.put("service", service);

                                    String html = htmlOutputter(doc, this.config.getProperty("service.endpoint.core.service.xslt"), parameters);

                                    httpServletResponse.setContentType("text/html;charset=UTF-8");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                    httpServletResponse.getWriter().println(html);

                                }
                                catch (JAXBException | JDOMException e) {
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                }
                            }
                        }
                        else {

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

                            this.sendRequestError(httpServletResponse, requestError);
                        }

                        break;
                    }
                    case "items/ordered": {

                        DocumentList documentList = integratedLibrarySystem.items(patronid, "ordered");

                        if (documentList != null) {
                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, documentList);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_items");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            // XML-Ausgabe mit JAXB
                            if (this.format.equals("xml")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(DocumentList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    httpServletResponse.setContentType("application/xml;charset=UTF-8");
                                    m.marshal(documentList, httpServletResponse.getWriter());

                                } catch (JAXBException e) {
                                    this.logger.error(e.getMessage(), e.getCause());
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering the results.");
                                }
                            }

                            // JSON-Ausgabe mit Jackson
                            if (this.format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
                            }

                            // html
                            if (this.format.equals("html")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(DocumentList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    StringWriter stringWriter = new StringWriter();
                                    m.marshal(documentList, stringWriter);

                                    org.jdom2.Document doc = new SAXBuilder().build(new StringReader(stringWriter.toString()));

                                    HashMap<String,String> parameters = new HashMap<String,String>();
                                    parameters.put("lang", this.language);
                                    parameters.put("service", service);

                                    String html = htmlOutputter(doc, this.config.getProperty("service.endpoint.core.service.xslt"), parameters);

                                    httpServletResponse.setContentType("text/html;charset=UTF-8");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                    httpServletResponse.getWriter().println(html);

                                }
                                catch (JAXBException | JDOMException e) {
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                }
                            }
                        }
                        else {

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

                            this.sendRequestError(httpServletResponse, requestError);
                        }

                        break;
                    }
                    case "items/reserved": {

                        DocumentList documentList = integratedLibrarySystem.items(patronid, "reserved");

                        if (documentList != null) {
                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, documentList);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_items");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            // XML-Ausgabe mit JAXB
                            if (this.format.equals("xml")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(DocumentList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    httpServletResponse.setContentType("application/xml;charset=UTF-8");
                                    m.marshal(documentList, httpServletResponse.getWriter());

                                } catch (JAXBException e) {
                                    this.logger.error(e.getMessage(), e.getCause());
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering the results.");
                                }
                            }

                            // JSON-Ausgabe mit Jackson
                            if (this.format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
                            }

                            // html
                            if (this.format.equals("html")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(DocumentList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    StringWriter stringWriter = new StringWriter();
                                    m.marshal(documentList, stringWriter);

                                    org.jdom2.Document doc = new SAXBuilder().build(new StringReader(stringWriter.toString()));

                                    HashMap<String,String> parameters = new HashMap<String,String>();
                                    parameters.put("lang", this.language);
                                    parameters.put("service", service);

                                    String html = htmlOutputter(doc, this.config.getProperty("service.endpoint.core.service.xslt"), parameters);

                                    httpServletResponse.setContentType("text/html;charset=UTF-8");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                    httpServletResponse.getWriter().println(html);

                                }
                                catch (JAXBException | JDOMException e) {
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                }
                            }
                        }
                        else {

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

                            this.sendRequestError(httpServletResponse, requestError);
                        }

                        break;
                    }
                    case "request": {

                        DocumentList documentList = integratedLibrarySystem.request(patronid, documents);

                        if (documentList != null) {

                            StringWriter json = new StringWriter();
                            mapper.writeValue(json, documentList);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            // delete DocumentList cookie
                            Cookie cookie = new Cookie("PaiaServiceDocumentList", null);
                            cookie.setMaxAge(0);
                            httpServletResponse.addCookie(cookie);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "write_items");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            // XML-Ausgabe mit JAXB
                            if (this.format.equals("xml")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(DocumentList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    httpServletResponse.setContentType("application/xml;charset=UTF-8");
                                    m.marshal(documentList, httpServletResponse.getWriter());

                                } catch (JAXBException e) {
                                    this.logger.error(e.getMessage(), e.getCause());
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering the results.");
                                }
                            }

                            // JSON-Ausgabe mit Jackson
                            if (this.format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
                            }

                            // html
                            if (this.format.equals("html")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(DocumentList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    StringWriter stringWriter = new StringWriter();
                                    m.marshal(documentList, stringWriter);

                                    org.jdom2.Document doc = new SAXBuilder().build(new StringReader(stringWriter.toString()));

                                    HashMap<String,String> parameters = new HashMap<String,String>();
                                    parameters.put("lang", this.language);
                                    parameters.put("service", service);

                                    String html = htmlOutputter(doc, this.config.getProperty("service.endpoint.core.service.xslt"), parameters);

                                    httpServletResponse.setContentType("text/html;charset=UTF-8");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                    httpServletResponse.getWriter().println(html);

                                }
                                catch (JAXBException | JDOMException e) {
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                }
                            }
                        }
                        else {

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

                            this.sendRequestError(httpServletResponse, requestError);
                        }

                        break;
                    }
                    case "renew": {

                        DocumentList documentList = integratedLibrarySystem.renew(patronid, documents);

                        if (documentList != null) {

                            StringWriter json = new StringWriter();
                            mapper.writeValue(json, documentList);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            // delete DocumentList cookie
                            Cookie cookie = new Cookie("PaiaServiceDocumentList", null);
                            cookie.setMaxAge(0);
                            httpServletResponse.addCookie(cookie);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "write_items");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            // XML-Ausgabe mit JAXB
                            if (this.format.equals("xml")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(DocumentList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    httpServletResponse.setContentType("application/xml;charset=UTF-8");
                                    m.marshal(documentList, httpServletResponse.getWriter());

                                } catch (JAXBException e) {
                                    this.logger.error(e.getMessage(), e.getCause());
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering the results.");
                                }
                            }

                            // JSON-Ausgabe mit Jackson
                            if (this.format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
                            }

                            // html
                            if (this.format.equals("html")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(DocumentList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    StringWriter stringWriter = new StringWriter();
                                    m.marshal(documentList, stringWriter);

                                    org.jdom2.Document doc = new SAXBuilder().build(new StringReader(stringWriter.toString()));

                                    HashMap<String,String> parameters = new HashMap<String,String>();
                                    parameters.put("lang", this.language);
                                    parameters.put("service", service);

                                    String html = htmlOutputter(doc, this.config.getProperty("service.endpoint.core.service.xslt"), parameters);

                                    httpServletResponse.setContentType("text/html;charset=UTF-8");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                    httpServletResponse.getWriter().println(html);

                                }
                                catch (JAXBException | JDOMException e) {
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                }
                            }
                        }
                        else {

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

                            this.sendRequestError(httpServletResponse, requestError);
                        }

                        break;
                    }
                    case "cancel": {

                        DocumentList documentList = integratedLibrarySystem.cancel(patronid, documents);

                        if (documentList != null) {

                            StringWriter json = new StringWriter();
                            mapper.writeValue(json, documentList);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            // delete DocumentList cookie
                            Cookie cookie = new Cookie("PaiaServiceDocumentList", null);
                            cookie.setMaxAge(0);
                            httpServletResponse.addCookie(cookie);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "write_items");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            // XML-Ausgabe mit JAXB
                            if (this.format.equals("xml")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(DocumentList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    httpServletResponse.setContentType("application/xml;charset=UTF-8");
                                    m.marshal(documentList, httpServletResponse.getWriter());

                                } catch (JAXBException e) {
                                    this.logger.error(e.getMessage(), e.getCause());
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering the results.");
                                }
                            }

                            // JSON-Ausgabe mit Jackson
                            if (this.format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
                            }

                            // html
                            if (this.format.equals("html")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(DocumentList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    StringWriter stringWriter = new StringWriter();
                                    m.marshal(documentList, stringWriter);

                                    org.jdom2.Document doc = new SAXBuilder().build(new StringReader(stringWriter.toString()));

                                    HashMap<String,String> parameters = new HashMap<String,String>();
                                    parameters.put("lang", this.language);
                                    parameters.put("service", service);

                                    String html = htmlOutputter(doc, this.config.getProperty("service.endpoint.core.service.xslt"), parameters);

                                    httpServletResponse.setContentType("text/html;charset=UTF-8");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                    httpServletResponse.getWriter().println(html);

                                }
                                catch (JAXBException | JDOMException e) {
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                }
                            }
                        }
                        else {

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

                            this.sendRequestError(httpServletResponse, requestError);
                        }

                        break;
                    }
                    case "fees": {

                        FeeList feeList = integratedLibrarySystem.fees(patronid);

                        if (feeList != null) {
                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, feeList);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_fees");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            // XML-Ausgabe mit JAXB
                            if (this.format.equals("xml")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(FeeList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    httpServletResponse.setContentType("application/xml;charset=UTF-8");
                                    m.marshal(feeList, httpServletResponse.getWriter());

                                } catch (JAXBException e) {
                                    this.logger.error(e.getMessage(), e.getCause());
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering the results.");
                                }
                            }

                            // JSON-Ausgabe mit Jackson
                            if (this.format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), feeList);
                            }

                            // html
                            if (this.format.equals("html")) {

                                try {

                                    JAXBContext context = JAXBContext.newInstance(FeeList.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    StringWriter stringWriter = new StringWriter();
                                    m.marshal(feeList, stringWriter);

                                    org.jdom2.Document doc = new SAXBuilder().build(new StringReader(stringWriter.toString()));

                                    HashMap<String,String> parameters = new HashMap<String,String>();
                                    parameters.put("lang", this.language);
                                    parameters.put("service", service);

                                    String html = htmlOutputter(doc, this.config.getProperty("service.endpoint.core.service.xslt"), parameters);

                                    httpServletResponse.setContentType("text/html;charset=UTF-8");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                    httpServletResponse.getWriter().println(html);

                                }
                                catch (JAXBException | JDOMException e) {
                                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                }
                            }
                        }
                        else {

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

                            this.sendRequestError(httpServletResponse, requestError);
                        }

                        break;
                    }
                }
            }
            catch (ILSException e) {

                StringWriter json = new StringWriter();

                // TODO Frage nach "570-unknown patron" ist nicht gut! Lösung: Welche Typen von ILSExceptions treten auf? Erzeuge für jeden Typ eine eigene Exception!
                if (e.getMessage().contains("570-unknown patron")) {

                    this.logger.error("[" + this.config.getProperty("service.name") + "] " + HttpServletResponse.SC_NOT_FOUND + ": '" + patronid + "'");

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

                    this.sendRequestError(httpServletResponse, requestError);
                }
                else {

                    this.logger.error("[" + this.config.getProperty("service.name") + "] " + HttpServletResponse.SC_SERVICE_UNAVAILABLE + ": ILS!");

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

                    this.sendRequestError(httpServletResponse, requestError);
                }
            }
            catch (Exception e) {

                e.printStackTrace();
            }
        }
        else {

            this.logger.error("[" + this.config.getProperty("service.name") + "] " + HttpServletResponse.SC_SERVICE_UNAVAILABLE + ": Config Error!");

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

            this.sendRequestError(httpServletResponse, requestError);
        }
    }

    private void sendRequestError(HttpServletResponse httpServletResponse, RequestError requestError) {

        ObjectMapper mapper = new ObjectMapper();

        httpServletResponse.setHeader("WWW-Authentificate", "Bearer");
        httpServletResponse.setHeader("WWW-Authentificate", "Bearer realm=\"PAIA core\"");
        httpServletResponse.setContentType("application/json");

        try {

            // XML-Ausgabe mit JAXB
            if (this.format.equals("xml")) {

                try {

                    JAXBContext context = JAXBContext.newInstance(RequestError.class);
                    Marshaller m = context.createMarshaller();
                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                    // Write to HttpResponse
                    httpServletResponse.setContentType("application/xml;charset=UTF-8");
                    m.marshal(requestError, httpServletResponse.getWriter());
                } catch (JAXBException e) {
                    this.logger.error(e.getMessage(), e.getCause());
                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering the results.");
                }
            }

            // JSON-Ausgabe mit Jackson
            if (this.format.equals("json")) {

                httpServletResponse.setContentType("application/json;charset=UTF-8");
                mapper.writeValue(httpServletResponse.getWriter(), requestError);
            }

            // html
            if (this.format.equals("html")) {

                try {

                    JAXBContext context = JAXBContext.newInstance(RequestError.class);
                    Marshaller m = context.createMarshaller();
                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                    // Write to HttpResponse
                    StringWriter stringWriter = new StringWriter();
                    m.marshal(requestError, stringWriter);

                    org.jdom2.Document doc = new SAXBuilder().build(new StringReader(stringWriter.toString()));

                    HashMap<String, String> parameters = new HashMap<String, String>();
                    parameters.put("lang", this.language);
                    parameters.put("redirect_uri_params", URLDecoder.decode(this.redirect_url, "UTF-8"));

                    String html = htmlOutputter(doc, this.config.getProperty("service.requesterror.xslt"), parameters);

                    httpServletResponse.setContentType("text/html;charset=UTF-8");
                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                    httpServletResponse.getWriter().println(html);

                } catch (JAXBException e) {
                    this.logger.error(e.getMessage(), e.getCause());
                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                } catch (JDOMException e) {
                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                }
            }
        }
        catch (Exception e) {

            e.printStackTrace();
        }
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
    private String htmlOutputter(org.jdom2.Document doc, String xslt, HashMap<String,String> params) throws IOException {

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
