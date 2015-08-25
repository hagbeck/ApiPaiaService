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
import de.tu_dortmund.ub.api.paia.auth.AuthorizationException;
import de.tu_dortmund.ub.api.paia.auth.AuthorizationInterface;
import de.tu_dortmund.ub.api.paia.auth.model.LoginResponse;
import de.tu_dortmund.ub.api.paia.core.ils.ILSException;
import de.tu_dortmund.ub.api.paia.core.ils.IntegratedLibrarySystem;
import de.tu_dortmund.ub.api.paia.core.model.Patron;
import de.tu_dortmund.ub.api.paia.core.model.Document;
import de.tu_dortmund.ub.api.paia.core.model.DocumentList;
import de.tu_dortmund.ub.api.paia.core.model.FeeList;
import de.tu_dortmund.ub.api.paia.model.RequestError;
import de.tu_dortmund.ub.util.impl.Lookup;
import de.tu_dortmund.ub.util.output.ObjectToHtmlTransformation;
import de.tu_dortmund.ub.util.output.TransformationException;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

/**
 * This class represents the PAIA Core component.
 *
 * @author Hans-Georg Becker
 * @version 2015-05-06
 */
public class PaiaCoreEndpoint extends HttpServlet {

    // Configuration
    private String conffile  = "";
    private Properties config = new Properties();
    private Logger logger = Logger.getLogger(PaiaCoreEndpoint.class.getName());

    /**
     *
     * @throws IOException
     */
    public PaiaCoreEndpoint() throws IOException {

        this("conf/paia.properties");
    }

    /**
     *
     * @param conffile
     * @throws IOException
     */
    public PaiaCoreEndpoint(String conffile) throws IOException {

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
    }

    /**
     *
     * @param httpServletRequest
     * @param httpServletResponse
     * @throws ServletException
     * @throws IOException
     */
    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        this.doPost(httpServletRequest, httpServletResponse);
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

        String format;
        String language;
        String redirect_url;

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
            else if (params[1].equals("items") && params.length > 2) {
                patronid = params[0];
                for (int i = 1; i < params.length; i++) {

                    service += params[i];
                    if (i < params.length-1) {
                        service += "/";
                    }
                }
            }
        }

        if (patronid.equals("patronid")) {

            patronid = "";
        }

        this.logger.debug("[" + config.getProperty("service.name") + "] " + "Service: " + service);
        this.logger.debug("[" + config.getProperty("service.name") + "] " + "Patron: " + patronid);

        format = "html";

        if (httpServletRequest.getParameter("format") != null && !httpServletRequest.getParameter("format").equals("")) {

            format = httpServletRequest.getParameter("format");
        }
        else {

            Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
            while ( headerNames.hasMoreElements() ) {
                String headerNameKey = headerNames.nextElement();

                if (headerNameKey.equals("Accept")) {

                    this.logger.debug("headerNameKey = " + httpServletRequest.getHeader( headerNameKey ));

                    if (httpServletRequest.getHeader( headerNameKey ).contains("text/html")) {
                        format = "html";
                    }
                    else if (httpServletRequest.getHeader( headerNameKey ).contains("application/xml")) {
                        format = "xml";
                    }
                    else if (httpServletRequest.getHeader( headerNameKey ).contains("application/json")) {
                        format = "json";
                    }
                }
            }
        }

        this.logger.info("format = " + format);

        if (format.equals("html") && Lookup.lookupAll(ObjectToHtmlTransformation.class).size() == 0) {

            this.logger.error("[" + this.config.getProperty("service.name") + "] " + HttpServletResponse.SC_BAD_REQUEST + ": " + "html not implemented!");

            // Error handling mit suppress_response_codes=true
            if (httpServletRequest.getParameter("suppress_response_codes") != null) {
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

            this.sendRequestError(httpServletResponse, requestError, format, "", "");
        }
        else {

            // read requestBody
            StringBuffer jb = new StringBuffer();
            String line = null;
            try {
                BufferedReader reader = httpServletRequest.getReader();
                while ((line = reader.readLine()) != null)
                    jb.append(line);
            } catch (Exception e) { /*report an error*/ }

            String requestBody = jb.toString();

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

            if (patronid.equals("")) {

                // Authorization
                this.authorize(httpServletRequest, httpServletResponse, format, documentList);
            }
            else {

                redirect_url = "";

                if (httpServletRequest.getParameter("redirect_url") != null && !httpServletRequest.getParameter("redirect_url").equals("")) {

                    redirect_url = httpServletRequest.getParameter("redirect_url");
                }

                this.logger.info("redirect_url = " + redirect_url);

                language = "";

                // PAIA core - function
                if ((httpServletRequest.getMethod().equals("GET") && (service.equals("patron") || service.equals("fullpatron") ||
                        service.equals("items") || service.startsWith("items/ordered") || service.startsWith("items/reserved") ||
                        service.startsWith("items/borrowed") || service.startsWith("items/borrowed/ill") || service.startsWith("items/borrowed/renewed") || service.startsWith("items/borrowed/recalled") ||
                        service.equals("fees") || service.equals("request"))) ||
                        (httpServletRequest.getMethod().equals("POST") && (service.equals("request") || service.equals("renew") || service.equals("cancel")))) {

                    // get 'Accept' and 'Authorization' from Header
                    Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
                    while (headerNames.hasMoreElements()) {

                        String headerNameKey = (String) headerNames.nextElement();
                        this.logger.debug("[" + config.getProperty("service.name") + "] " + "headerNameKey = " + headerNameKey + " / headerNameValue = " + httpServletRequest.getHeader(headerNameKey));

                        if (headerNameKey.equals("Accept-Language")) {
                            language = httpServletRequest.getHeader(headerNameKey);
                            this.logger.debug("[" + config.getProperty("service.name") + "] " + "Accept-Language: " + language);
                        }
                        if (headerNameKey.equals("Accept")) {
                            accept = httpServletRequest.getHeader(headerNameKey);
                            this.logger.debug("[" + config.getProperty("service.name") + "] " + "Accept: " + accept);
                        }
                        if (headerNameKey.equals("Authorization")) {
                            authorization = httpServletRequest.getHeader(headerNameKey);
                        }
                    }

                    // language
                    if (language.startsWith("de")) {
                        language = "de";
                    } else if (language.startsWith("en")) {
                        language = "en";
                    } else if (httpServletRequest.getParameter("l") != null) {
                        language = httpServletRequest.getParameter("l");
                    } else {
                        language = "de";
                    }

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

                    httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");

                    // check token ...
                    boolean isAuthorized = false;

                    if (!authorization.equals("")) {

                        if (Lookup.lookupAll(AuthorizationInterface.class).size() > 0) {

                            AuthorizationInterface authorizationInterface = Lookup.lookup(AuthorizationInterface.class);
                            // init Authorization Service
                            authorizationInterface.init(this.config);

                            try {

                                isAuthorized = authorizationInterface.isTokenValid(httpServletResponse, service, patronid, authorization);
                            }
                            catch (AuthorizationException e) {

                                // TODO correct error handling
                                this.logger.error("[" + config.getProperty("service.name") + "] " + HttpServletResponse.SC_UNAUTHORIZED + "!");
                            }
                        } else {

                            // TODO correct error handling
                            this.logger.error("[" + this.config.getProperty("service.name") + "] " + HttpServletResponse.SC_INTERNAL_SERVER_ERROR + ": " + "Authorization Interface not implemented!");
                        }
                    }

                    this.logger.debug("[" + config.getProperty("service.name") + "] " + "Authorization: " + authorization + " - " + isAuthorized);

                    // ... - if not is authorized - against DFN-AAI service
                    if (!isAuthorized) {

                        // TODO if exists OpenAM-Session-Cookie: read content
                        this.logger.debug("[" + config.getProperty("service.name") + "] " + "Authorization: " + authorization + " - " + isAuthorized);
                    }

                    if (isAuthorized) {

                        // execute query
                        this.provideService(httpServletRequest, httpServletResponse, patronid, service, format, language, redirect_url, documentList);
                    }
                    else {

                        // Authorization
                        this.authorize(httpServletRequest, httpServletResponse, format, documentList);
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

                    this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                }
            }
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
     * @throws IOException
     */
    private void authorize(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String format, DocumentList documents) throws IOException {

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
        if (format.equals("xml")) {

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
        if (format.equals("json")) {

            httpServletResponse.setContentType("application/json;charset=UTF-8");
            mapper.writeValue(httpServletResponse.getWriter(), requestError);
        }

        // html > redirect zu "PAIA auth - login" mit redirect_url = "PAIA core - service"
        if (format.equals("html")) {

            httpServletResponse.setContentType("text/html;charset=UTF-8");

            if (documents != null) {
                // set Cookie with urlencoded DocumentList-JSON
                StringWriter stringWriter = new StringWriter();
                mapper.writeValue(stringWriter, documents);
                Cookie cookie = new Cookie("PaiaServiceDocumentList", URLEncoder.encode(stringWriter.toString(), "UTF-8"));
                if (this.config.getProperty("service.cookie.domain") != null && !this.config.getProperty("service.cookie.domain").equals("")) {
                    cookie.setDomain(this.config.getProperty("service.cookie.domain"));
                }
                cookie.setMaxAge(-1);
                cookie.setPath("/");
                httpServletResponse.addCookie(cookie);
            }

            //String redirect_url = "http://" + httpServletRequest.getServerName() + ":" + httpServletRequest.getServerPort() + this.config.getProperty("service.endpoint.core") + httpServletRequest.getPathInfo();
            String redirect_url = this.config.getProperty("service.base_url") + this.config.getProperty("service.endpoint.core") + httpServletRequest.getPathInfo();
            if (httpServletRequest.getQueryString() != null && !httpServletRequest.getQueryString().equals("")) {
                redirect_url += "?" + httpServletRequest.getQueryString();
            }
            this.logger.info("redirect_url = " + redirect_url);

            //String login_url = "http://" + httpServletRequest.getServerName() + ":" + httpServletRequest.getServerPort() + this.config.getProperty("service.endpoint.auth") + "/login?redirect_url=" + redirect_url;
            String login_url = this.config.getProperty("service.base_url") + this.config.getProperty("service.endpoint.auth") + "/login?redirect_url=" + redirect_url;
            this.logger.info("login_url = " + login_url);

            httpServletResponse.sendRedirect(login_url);
        }
    }

    /**
     * PAIA core services: Prüfe jeweils die scopes und liefere die Daten
     */
    private void provideService(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String patronid, String service, String format, String language, String redirect_url, DocumentList documents) throws IOException {

        ObjectMapper mapper = new ObjectMapper();

        if (Lookup.lookupAll(IntegratedLibrarySystem.class).size() > 0) {

            try {
                IntegratedLibrarySystem integratedLibrarySystem = Lookup.lookup(IntegratedLibrarySystem.class);
                // init ILS
                integratedLibrarySystem.init(this.config);

                switch (service) {

                    case "patron": {

                        Patron patron = integratedLibrarySystem.patron(patronid, false);

                        if (patron != null) {

                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, patron);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_patron");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            if (format.equals("html")) {

                                if (Lookup.lookupAll(ObjectToHtmlTransformation.class).size() > 0) {

                                    try {
                                        ObjectToHtmlTransformation htmlTransformation = Lookup.lookup(ObjectToHtmlTransformation.class);
                                        // init transformator
                                        htmlTransformation.init(this.config);

                                        HashMap<String, String> parameters = new HashMap<String, String>();
                                        parameters.put("lang", language);
                                        parameters.put("service", service);

                                        httpServletResponse.setContentType("text/html;charset=UTF-8");
                                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                        httpServletResponse.getWriter().println(htmlTransformation.transform(patron, parameters));
                                    }
                                    catch (TransformationException e) {
                                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                    }
                                }
                                else {
                                    this.logger.error("ObjectToHtmlTransformation not configured! Switch to JSON.");
                                    format = "json";
                                }
                            }

                            // XML-Ausgabe mit JAXB
                            if (format.equals("xml")) {

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
                            if (format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), patron);
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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                        }

                        break;
                    }
                    case "fullpatron": {

                        Patron patron = integratedLibrarySystem.patron(patronid, true);

                        if (patron != null) {

                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, patron);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "write_patron");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            if (format.equals("html")) {

                                if (Lookup.lookupAll(ObjectToHtmlTransformation.class).size() > 0) {

                                    try {
                                        ObjectToHtmlTransformation htmlTransformation = Lookup.lookup(ObjectToHtmlTransformation.class);
                                        // init transformator
                                        htmlTransformation.init(this.config);

                                        HashMap<String, String> parameters = new HashMap<String, String>();
                                        parameters.put("lang", language);
                                        parameters.put("service", service);

                                        httpServletResponse.setContentType("text/html;charset=UTF-8");
                                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                        httpServletResponse.getWriter().println(htmlTransformation.transform(patron, parameters));
                                    }
                                    catch (TransformationException e) {
                                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                    }
                                }
                                else {
                                    this.logger.error("ObjectToHtmlTransformation not configured! Switch to JSON.");
                                    format = "json";
                                }
                            }

                            // XML-Ausgabe mit JAXB
                            if (format.equals("xml")) {

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
                            if (format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), patron);
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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
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

                            if (format.equals("html")) {

                                if (Lookup.lookupAll(ObjectToHtmlTransformation.class).size() > 0) {

                                    try {
                                        ObjectToHtmlTransformation htmlTransformation = Lookup.lookup(ObjectToHtmlTransformation.class);
                                        // init transformator
                                        htmlTransformation.init(this.config);

                                        HashMap<String, String> parameters = new HashMap<String, String>();
                                        parameters.put("lang", language);
                                        parameters.put("service", service);

                                        httpServletResponse.setContentType("text/html;charset=UTF-8");
                                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                        httpServletResponse.getWriter().println(htmlTransformation.transform(documentList, parameters));
                                    }
                                    catch (TransformationException e) {
                                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                    }
                                }
                                else {
                                    this.logger.error("ObjectToHtmlTransformation not configured! Switch to JSON.");
                                    format = "json";
                                }
                            }

                            // XML-Ausgabe mit JAXB
                            if (format.equals("xml")) {

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
                            if (format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
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

                            if (format.equals("html")) {

                                if (Lookup.lookupAll(ObjectToHtmlTransformation.class).size() > 0) {

                                    try {
                                        ObjectToHtmlTransformation htmlTransformation = Lookup.lookup(ObjectToHtmlTransformation.class);
                                        // init transformator
                                        htmlTransformation.init(this.config);

                                        HashMap<String, String> parameters = new HashMap<String, String>();
                                        parameters.put("lang", language);
                                        parameters.put("service", service);

                                        httpServletResponse.setContentType("text/html;charset=UTF-8");
                                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                        httpServletResponse.getWriter().println(htmlTransformation.transform(documentList, parameters));
                                    }
                                    catch (TransformationException e) {
                                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                    }
                                }
                                else {
                                    this.logger.error("ObjectToHtmlTransformation not configured! Switch to JSON.");
                                    format = "json";
                                }
                            }

                            // XML-Ausgabe mit JAXB
                            if (format.equals("xml")) {

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
                            if (format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                        }

                        break;
                    }
                    case "items/borrowed/ill": {

                        DocumentList documentList = integratedLibrarySystem.items(patronid, "borrowed", "ill");

                        if (documentList != null) {
                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, documentList);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_items");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            if (format.equals("html")) {

                                if (Lookup.lookupAll(ObjectToHtmlTransformation.class).size() > 0) {

                                    try {
                                        ObjectToHtmlTransformation htmlTransformation = Lookup.lookup(ObjectToHtmlTransformation.class);
                                        // init transformator
                                        htmlTransformation.init(this.config);

                                        HashMap<String, String> parameters = new HashMap<String, String>();
                                        parameters.put("lang", language);
                                        parameters.put("service", service);

                                        httpServletResponse.setContentType("text/html;charset=UTF-8");
                                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                        httpServletResponse.getWriter().println(htmlTransformation.transform(documentList, parameters));
                                    }
                                    catch (TransformationException e) {
                                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                    }
                                }
                                else {
                                    this.logger.error("ObjectToHtmlTransformation not configured! Switch to JSON.");
                                    format = "json";
                                }
                            }

                            // XML-Ausgabe mit JAXB
                            if (format.equals("xml")) {

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
                            if (format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                        }

                        break;
                    }
                    case "items/borrowed/renewed": {

                        DocumentList documentList = integratedLibrarySystem.items(patronid, "borrowed", "renewed");

                        if (documentList != null) {
                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, documentList);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_items");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            if (format.equals("html")) {

                                if (Lookup.lookupAll(ObjectToHtmlTransformation.class).size() > 0) {

                                    try {
                                        ObjectToHtmlTransformation htmlTransformation = Lookup.lookup(ObjectToHtmlTransformation.class);
                                        // init transformator
                                        htmlTransformation.init(this.config);

                                        HashMap<String, String> parameters = new HashMap<String, String>();
                                        parameters.put("lang", language);
                                        parameters.put("service", service);

                                        httpServletResponse.setContentType("text/html;charset=UTF-8");
                                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                        httpServletResponse.getWriter().println(htmlTransformation.transform(documentList, parameters));
                                    }
                                    catch (TransformationException e) {
                                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                    }
                                }
                                else {
                                    this.logger.error("ObjectToHtmlTransformation not configured! Switch to JSON.");
                                    format = "json";
                                }
                            }

                            // XML-Ausgabe mit JAXB
                            if (format.equals("xml")) {

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
                            if (format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                        }

                        break;
                    }
                    case "items/borrowed/recalled": {

                        DocumentList documentList = integratedLibrarySystem.items(patronid, "borrowed", "recalled");

                        if (documentList != null) {
                            StringWriter json = new StringWriter();
                            mapper = new ObjectMapper();
                            mapper.writeValue(json, documentList);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "read_items");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            if (format.equals("html")) {

                                if (Lookup.lookupAll(ObjectToHtmlTransformation.class).size() > 0) {

                                    try {
                                        ObjectToHtmlTransformation htmlTransformation = Lookup.lookup(ObjectToHtmlTransformation.class);
                                        // init transformator
                                        htmlTransformation.init(this.config);

                                        HashMap<String, String> parameters = new HashMap<String, String>();
                                        parameters.put("lang", language);
                                        parameters.put("service", service);

                                        httpServletResponse.setContentType("text/html;charset=UTF-8");
                                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                        httpServletResponse.getWriter().println(htmlTransformation.transform(documentList, parameters));
                                    }
                                    catch (TransformationException e) {
                                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                    }
                                }
                                else {
                                    this.logger.error("ObjectToHtmlTransformation not configured! Switch to JSON.");
                                    format = "json";
                                }
                            }

                            // XML-Ausgabe mit JAXB
                            if (format.equals("xml")) {

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
                            if (format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
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

                            if (format.equals("html")) {

                                if (Lookup.lookupAll(ObjectToHtmlTransformation.class).size() > 0) {

                                    try {
                                        ObjectToHtmlTransformation htmlTransformation = Lookup.lookup(ObjectToHtmlTransformation.class);
                                        // init transformator
                                        htmlTransformation.init(this.config);

                                        HashMap<String, String> parameters = new HashMap<String, String>();
                                        parameters.put("lang", language);
                                        parameters.put("service", service);

                                        httpServletResponse.setContentType("text/html;charset=UTF-8");
                                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                        httpServletResponse.getWriter().println(htmlTransformation.transform(documentList, parameters));
                                    }
                                    catch (TransformationException e) {
                                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                    }
                                }
                                else {
                                    this.logger.error("ObjectToHtmlTransformation not configured! Switch to JSON.");
                                    format = "json";
                                }
                            }

                            // XML-Ausgabe mit JAXB
                            if (format.equals("xml")) {

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
                            if (format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
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

                            if (format.equals("html")) {

                                if (Lookup.lookupAll(ObjectToHtmlTransformation.class).size() > 0) {

                                    try {
                                        ObjectToHtmlTransformation htmlTransformation = Lookup.lookup(ObjectToHtmlTransformation.class);
                                        // init transformator
                                        htmlTransformation.init(this.config);

                                        HashMap<String, String> parameters = new HashMap<String, String>();
                                        parameters.put("lang", language);
                                        parameters.put("service", service);

                                        httpServletResponse.setContentType("text/html;charset=UTF-8");
                                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                        httpServletResponse.getWriter().println(htmlTransformation.transform(documentList, parameters));
                                    }
                                    catch (TransformationException e) {
                                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                    }
                                }
                                else {
                                    this.logger.error("ObjectToHtmlTransformation not configured! Switch to JSON.");
                                    format = "json";
                                }
                            }

                            // XML-Ausgabe mit JAXB
                            if (format.equals("xml")) {

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
                            if (format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                        }

                        break;
                    }
                    case "request": {

                        DocumentList documentList = integratedLibrarySystem.request(patronid, documents);

                        if (documentList != null) {

                            StringWriter json = new StringWriter();
                            mapper.writeValue(json, documentList);
                            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

                            // set Cookie with new value for urlencoded DocumentList-JSON
                            StringWriter stringWriter = new StringWriter();
                            mapper.writeValue(stringWriter, documents);
                            Cookie cookie = new Cookie("PaiaServiceDocumentList", URLEncoder.encode(stringWriter.toString(), "UTF-8"));
                            if (this.config.getProperty("service.cookie.domain") != null && !this.config.getProperty("service.cookie.domain").equals("")) {
                                cookie.setDomain(this.config.getProperty("service.cookie.domain"));
                            }
                            cookie.setMaxAge(-1);
                            cookie.setPath("/");
                            httpServletResponse.addCookie(cookie);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "write_items");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            if (format.equals("html")) {

                                if (Lookup.lookupAll(ObjectToHtmlTransformation.class).size() > 0) {

                                    this.logger.info("redirect_url = " + redirect_url);
                                    if (!redirect_url.equals("")) {

                                        httpServletResponse.sendRedirect(redirect_url);
                                    } else {

                                        try {
                                            ObjectToHtmlTransformation htmlTransformation = Lookup.lookup(ObjectToHtmlTransformation.class);
                                            // init transformator
                                            htmlTransformation.init(this.config);

                                            HashMap<String, String> parameters = new HashMap<String, String>();
                                            parameters.put("lang", language);
                                            parameters.put("service", service);

                                            httpServletResponse.setContentType("text/html;charset=UTF-8");
                                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                            httpServletResponse.getWriter().println(htmlTransformation.transform(documentList, parameters));
                                        } catch (TransformationException e) {
                                            httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                        }
                                    }
                                }
                                else{
                                    this.logger.error("ObjectToHtmlTransformation not configured! Switch to JSON.");
                                    format = "json";
                                }
                            }

                            // XML-Ausgabe mit JAXB
                            if (format.equals("xml")) {

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
                            if (format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
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
                            if (this.config.getProperty("service.cookie.domain") != null && !this.config.getProperty("service.cookie.domain").equals("")) {
                                cookie.setDomain(this.config.getProperty("service.cookie.domain"));
                            }
                            cookie.setMaxAge(0);
                            httpServletResponse.addCookie(cookie);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "write_items");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            if (format.equals("html")) {

                                if (Lookup.lookupAll(ObjectToHtmlTransformation.class).size() > 0) {

                                    try {
                                        ObjectToHtmlTransformation htmlTransformation = Lookup.lookup(ObjectToHtmlTransformation.class);
                                        // init transformator
                                        htmlTransformation.init(this.config);

                                        HashMap<String, String> parameters = new HashMap<String, String>();
                                        parameters.put("lang", language);
                                        parameters.put("service", service);

                                        httpServletResponse.setContentType("text/html;charset=UTF-8");
                                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                        httpServletResponse.getWriter().println(htmlTransformation.transform(documentList, parameters));
                                    }
                                    catch (TransformationException e) {
                                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                    }
                                }
                                else {
                                    this.logger.error("ObjectToHtmlTransformation not configured! Switch to JSON.");
                                    format = "json";
                                }
                            }

                            // XML-Ausgabe mit JAXB
                            if (format.equals("xml")) {

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
                            if (format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
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
                            if (this.config.getProperty("service.cookie.domain") != null && !this.config.getProperty("service.cookie.domain").equals("")) {
                                cookie.setDomain(this.config.getProperty("service.cookie.domain"));
                            }
                            cookie.setMaxAge(0);
                            httpServletResponse.addCookie(cookie);

                            httpServletResponse.setHeader("X-Accepted-OAuth-Scopes", "write_items");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            if (format.equals("html")) {

                                if (Lookup.lookupAll(ObjectToHtmlTransformation.class).size() > 0) {

                                    try {
                                        ObjectToHtmlTransformation htmlTransformation = Lookup.lookup(ObjectToHtmlTransformation.class);
                                        // init transformator
                                        htmlTransformation.init(this.config);

                                        HashMap<String, String> parameters = new HashMap<String, String>();
                                        parameters.put("lang", language);
                                        parameters.put("service", service);

                                        httpServletResponse.setContentType("text/html;charset=UTF-8");
                                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                        httpServletResponse.getWriter().println(htmlTransformation.transform(documentList, parameters));
                                    }
                                    catch (TransformationException e) {
                                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                    }
                                }
                                else {
                                    this.logger.error("ObjectToHtmlTransformation not configured! Switch to JSON.");
                                    format = "json";
                                }
                            }

                            // XML-Ausgabe mit JAXB
                            if (format.equals("xml")) {

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
                            if (format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), documentList);
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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
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

                            if (format.equals("html")) {

                                if (Lookup.lookupAll(ObjectToHtmlTransformation.class).size() > 0) {

                                    try {
                                        ObjectToHtmlTransformation htmlTransformation = Lookup.lookup(ObjectToHtmlTransformation.class);
                                        // init transformator
                                        htmlTransformation.init(this.config);

                                        HashMap<String, String> parameters = new HashMap<String, String>();
                                        parameters.put("lang", language);
                                        parameters.put("service", service);

                                        httpServletResponse.setContentType("text/html;charset=UTF-8");
                                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                        httpServletResponse.getWriter().println(htmlTransformation.transform(feeList, parameters));
                                    }
                                    catch (TransformationException e) {
                                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                                    }
                                }
                                else {
                                    this.logger.error("ObjectToHtmlTransformation not configured! Switch to JSON.");
                                    format = "json";
                                }
                            }

                            // XML-Ausgabe mit JAXB
                            if (format.equals("xml")) {

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
                            if (format.equals("json")) {

                                httpServletResponse.setContentType("application/json;charset=UTF-8");
                                mapper.writeValue(httpServletResponse.getWriter(), feeList);
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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
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

                    this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
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

                    this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
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

            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
        }
    }

    private void sendRequestError(HttpServletResponse httpServletResponse, RequestError requestError, String format, String language, String redirect_url) {

        ObjectMapper mapper = new ObjectMapper();

        httpServletResponse.setHeader("WWW-Authentificate", "Bearer");
        httpServletResponse.setHeader("WWW-Authentificate", "Bearer realm=\"PAIA core\"");
        httpServletResponse.setContentType("application/json");

        try {

            // html
            if (format.equals("html")) {

                if (Lookup.lookupAll(ObjectToHtmlTransformation.class).size() > 0) {

                    try {
                        ObjectToHtmlTransformation htmlTransformation = Lookup.lookup(ObjectToHtmlTransformation.class);
                        // init transformator
                        htmlTransformation.init(this.config);

                        HashMap<String, String> parameters = new HashMap<String, String>();
                        parameters.put("lang", language);
                        parameters.put("redirect_uri_params", URLDecoder.decode(redirect_url, "UTF-8"));

                        httpServletResponse.setContentType("text/html;charset=UTF-8");
                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                        httpServletResponse.getWriter().println(htmlTransformation.transform(requestError, parameters));
                    }
                    catch (TransformationException e) {
                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message.");
                    }
                }
                else {
                    this.logger.error("ObjectToHtmlTransformation not configured! Switch to JSON.");
                    format = "json";
                }
            }

            // XML-Ausgabe mit JAXB
            if (format.equals("xml")) {

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
            if (format.equals("json")) {

                httpServletResponse.setContentType("application/json;charset=UTF-8");
                mapper.writeValue(httpServletResponse.getWriter(), requestError);
            }
        }
        catch (Exception e) {

            e.printStackTrace();
        }
    }
}
