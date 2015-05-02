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
import de.tu_dortmund.ub.api.paia.model.RequestError;
import de.tu_dortmund.ub.api.paia.auth.model.LoginRequest;
import de.tu_dortmund.ub.api.paia.auth.model.LoginResponse;
import net.sf.saxon.s9api.*;
import org.apache.commons.io.IOUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.transform.JDOMSource;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.PropertyException;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

/**
 * This class represents the PAIA Auth component.
 *
 * @author Hans-Georg Becker
 * @version 2015-05-04
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
            System.out.println("FATAL ERROR: Die Datei '" + this.conffile + "' konnte nicht geöffnet werden!");
        }

        // init logger
        PropertyConfigurator.configure(this.config.getProperty("service.log4j-conf"));

        this.logger.info("[" + this.config.getProperty("service.name") + "] " + "Starting 'PAIAauth Auth Endpoint' ...");
        this.logger.info("[" + this.config.getProperty("service.name") + "] " + "conf-file = " + this.conffile);
        this.logger.info("[" + this.config.getProperty("service.name") + "] " + "log4j-conf-file = " + this.config.getProperty("service.log4j-conf"));
    }

    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        this.doPost(httpServletRequest, httpServletResponse);
    }

    protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        ObjectMapper mapper = new ObjectMapper();

        this.logger.debug("[" + this.config.getProperty("service.name") + "] " + "PathInfo = " + httpServletRequest.getPathInfo());
        this.logger.debug("[" + this.config.getProperty("service.name") + "] " + "QueryString = " + httpServletRequest.getQueryString());

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
            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + "headerNameKey = " + headerNameKey + " / headerNameValue = " + httpServletRequest.getHeader(headerNameKey));

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

        if (access_token.equals("") && httpServletRequest.getParameter("access_token") != null) {

            access_token = httpServletRequest.getParameter("access_token");
        }

        this.logger.debug("[" + this.config.getProperty("service.name") + "] " + "Service: " + service);
        this.logger.debug("[" + this.config.getProperty("service.name") + "] " + "Accept: " + accept);
        this.logger.debug("[" + this.config.getProperty("service.name") + "] " + "Access_token: " + access_token);

        StringBuffer jb = new StringBuffer();
        String line = null;
        try {
            BufferedReader reader = httpServletRequest.getReader();
            while ((line = reader.readLine()) != null)
                jb.append(line);
        } catch (Exception e) { /*report an error*/ }

        String requestBody = jb.toString();

        // 2. Schritt: Service
        if (service.equals("login") || service.equals("logout") || service.equals("change")) {

            this.paiaAuth(httpServletRequest, httpServletResponse, service, access_token, requestBody);
        }
        else {

            this.logger.error("[" + this.config.getProperty("service.name") + "] " + HttpServletResponse.SC_METHOD_NOT_ALLOWED + ": " + "POST for '" + service + "' not allowed!");

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
            this.logger.debug("[" + this.config.getProperty("service.name") + "] " + json);

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
    private void paiaAuth(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String service, String access_token, String requestBody) throws IOException {

        ObjectMapper mapper = new ObjectMapper();

        String format = "html";

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

        String redirect_url = "";

        switch (service) {

            case "login": {

                LoginRequest loginRequest = null;
                try {

                    loginRequest = mapper.readValue(requestBody, LoginRequest.class);
                }
                catch (Exception e) {

                    String[] params = requestBody.split("&");

                    if (params.length > 1) {

                        loginRequest = new LoginRequest();

                        for (String param : params) {

                            if (param.startsWith("grant_type")) {
                                loginRequest.setGrant_type(param.split("=")[1]);
                            }
                            else if (param.startsWith("username")) {
                                loginRequest.setUsername(param.split("=")[1]);
                            }
                            else if (param.startsWith("password")) {
                                loginRequest.setPassword(param.split("=")[1]);
                            }
                            else if (param.startsWith("format")) {

                                format = param.split("=")[1];
                                this.logger.info("format = " + format);
                            }
                            else if (param.startsWith("redirect_url")) {

                                redirect_url = URLDecoder.decode(param.split("=")[1],"UTF-8");
                                this.logger.info("redirect_url = " + redirect_url);
                            }
                            else {
                                // Tu nix
                            }
                        }
                    }
                    else {

                        loginRequest = null;
                    }
                }

                if (loginRequest != null && loginRequest.getUsername() != null && loginRequest.getPassword() != null && loginRequest.getGrant_type() != null && loginRequest.getGrant_type().equals("password")) {

                    LoginResponse loginResponse = null;

                    String scope = "read_patron read_fees read_items write_items";
                    if (loginRequest.getScope() != null && !loginRequest.getScope().equals("")) {

                        scope = loginRequest.getScope();
                    }

                    // HTTP POST /oauth20/tokens
                    CloseableHttpClient httpclient = HttpClients.createDefault();

                    try {
                        String tokenRequestBody = "grant_type=password&username=" + loginRequest.getUsername()
                                + "&password=" + loginRequest.getPassword()
                                + "&scope=" + scope
                                + "&client_id=" + this.config.getProperty("service.auth.ubdo.client_id")
                                + "&client_secret=" + this.config.getProperty("service.auth.ubdo.client_secret");

                        HttpPost httpPost = new HttpPost(this.config.getProperty("service.auth.ubdo.tokenendpoint"));
                        StringEntity stringEntity = new StringEntity(tokenRequestBody, ContentType.create("application/x-www-form-urlencoded", Consts.UTF_8));
                        httpPost.setEntity(stringEntity);

                        this.logger.info("[" + this.config.getProperty("service.name") + "] " + "request : " + httpPost.getRequestLine());

                        CloseableHttpResponse httpResponse = httpclient.execute(httpPost);

                        try {

                            int statusCode = httpResponse.getStatusLine().getStatusCode();
                            HttpEntity httpEntity = httpResponse.getEntity();

                            switch (statusCode) {

                                case 200: {

                                    this.logger.info("[" + this.config.getProperty("service.name") + "] " + statusCode + " : " + httpResponse.getStatusLine().getReasonPhrase());

                                    StringWriter writer = new StringWriter();
                                    IOUtils.copy(httpEntity.getContent(), writer, "UTF-8");
                                    String responseJson = writer.toString();

                                    this.logger.info("[" + this.config.getProperty("service.name") + "] responseJson : " + responseJson);

                                    loginResponse = mapper.readValue(responseJson, LoginResponse.class);

                                    break;
                                }
                                default: {

                                    this.logger.error("[" + this.config.getProperty("service.name") + "] " + statusCode + " : " + httpResponse.getStatusLine().getReasonPhrase());

                                }
                            }

                            EntityUtils.consume(httpEntity);
                        } finally {
                            httpResponse.close();
                        }
                    } finally {
                        httpclient.close();
                    }

                    if (loginResponse != null) {

                        // anpassen des loginResponse
                        loginResponse.setRefresh_token(null);
                        loginResponse.setRefresh_expires_in(null);
                        loginResponse.setPatron(loginRequest.getUsername());

                        httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                        // add cookie
                        StringWriter stringWriter = new StringWriter();
                        mapper.writeValue(stringWriter, loginResponse);
                        Cookie cookie = new Cookie("PaiaService", URLEncoder.encode(stringWriter.toString(), "UTF-8"));
                        cookie.setMaxAge(-1);
                        httpServletResponse.addCookie(cookie);

                        // XML-Ausgabe mit JAXB
                        if (format.equals("xml")) {

                            try {

                                JAXBContext context = JAXBContext.newInstance(LoginResponse.class);
                                Marshaller m = context.createMarshaller();
                                m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                // Write to HttpResponse
                                httpServletResponse.setContentType("application/xml;charset=UTF-8");
                                m.marshal(loginResponse, httpServletResponse.getWriter());
                            } catch (JAXBException e) {
                                this.logger.error(e.getMessage(), e.getCause());
                                httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering the results.");
                            }
                        }

                        // JSON-Ausgabe mit Jackson
                        if (format.equals("json")) {

                            httpServletResponse.setContentType("application/json;charset=UTF-8");
                            mapper.writeValue(httpServletResponse.getWriter(), loginResponse);
                        }

                        // html >> redirect
                        if (format.equals("html")) {

                            // if QueryString contains redirect_url and value of it contains /paia/core/ >> expand URL with username
                            if (redirect_url.contains("/paia/core/")) {

                                redirect_url += loginResponse.getPatron();
                            }
                            this.logger.info("redirect_url = " + redirect_url);

                            httpServletResponse.sendRedirect(redirect_url);
                        }
                    }
                    else {

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

                        // html
                        if (format.equals("html")) {

                            try {

                                JAXBContext context = JAXBContext.newInstance(RequestError.class);
                                Marshaller m = context.createMarshaller();
                                m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                // Write to HttpResponse
                                StringWriter stringWriter = new StringWriter();
                                m.marshal(requestError, stringWriter);

                                Document doc = new SAXBuilder().build(new StringReader(stringWriter.toString()));

                                HashMap<String,String> parameters = new HashMap<String,String>();
                                parameters.put("lang", "de");
                                parameters.put("redirect_uri_params", URLDecoder.decode(redirect_url, "UTF-8"));

                                String html = htmlOutputter(doc, this.config.getProperty("service.requesterror.xslt"), parameters);

                                httpServletResponse.setContentType("text/html;charset=UTF-8");
                                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                httpServletResponse.getWriter().println(html);

                            }
                            catch (JAXBException e) {
                                this.logger.error(e.getMessage(), e.getCause());
                                httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message. Message is 'Document not found'.");
                            }
                            catch (JDOMException e) {
                                httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message. Message is 'Document not found'.");
                            }
                        }
                    }
                }
                // else Baue HTML-Seite mit login-Formular mittels XSLT
                else {

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

                    // html > Login-Seite
                    if (format.equals("html")) {

                        try {
                            String provider = "http://" + httpServletRequest.getServerName() + ":" + httpServletRequest.getServerPort() + this.config.getProperty("service.endpoint.auth") + "/" + service;

                            Document doc = new Document();

                            HashMap<String, String> parameters = new HashMap<String, String>();
                            parameters.put("lang", "de");
                            parameters.put("redirect_uri_params", URLDecoder.decode(httpServletRequest.getQueryString(), "UTF-8"));
                            parameters.put("formURL", provider);

                            String html = htmlOutputter(doc, this.config.getProperty("service.endpoint.auth.login.xslt"), parameters);

                            httpServletResponse.setContentType("text/html;charset=UTF-8");
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                            httpServletResponse.getWriter().println(html);
                        } catch (Exception e) {

                            e.printStackTrace();
                        }
                    }
                }

                break;
            }
            case "logout": {

                CloseableHttpClient httpclient = HttpClients.createDefault();

                String tokenRequestBody = "{ \"access_token\"=\"" + access_token + "\","
                        + "\"client_id\"=\"" + this.config.getProperty("service.auth.ubdo.client_id") + "\","
                        + "\"client_secret\"=\"" + this.config.getProperty("service.auth.ubdo.client_secret") + "\""
                        + "}";

                HttpPost httpPost = new HttpPost(this.config.getProperty("service.auth.ubdo.tokenendpoint") + "/revoke");
                StringEntity stringEntity = new StringEntity(tokenRequestBody, ContentType.create("application/json", Consts.UTF_8));
                httpPost.setEntity(stringEntity);

                CloseableHttpResponse httpResponse = httpclient.execute(httpPost);

                try {

                    int statusCode = httpResponse.getStatusLine().getStatusCode();
                    HttpEntity httpEntity = httpResponse.getEntity();

                    switch (statusCode) {

                        case 200: {

                            this.logger.info("[" + this.config.getProperty("service.name") + "] " + "ERROR in logout(): HTTP STATUS = " + statusCode);

                            break;
                        }
                        default: {

                            this.logger.error("[" + this.config.getProperty("service.name") + "] " + "ERROR in logout(): HTTP STATUS = " + statusCode);
                        }
                    }

                    EntityUtils.consume(httpEntity);
                } finally {
                    httpResponse.close();
                }

                httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
                httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                // add cookie
                Cookie cookie = new Cookie("PaiaService", null);
                cookie.setMaxAge(0);
                httpServletResponse.addCookie(cookie);

                // html >> redirect
                if (format.equals("html")) {

                    if (httpServletRequest.getParameter("redirect_url") != null && !httpServletRequest.getParameter("redirect_url").equals("")) {
                        httpServletResponse.sendRedirect(httpServletRequest.getParameter("redirect_url"));
                    }
                    else {

                        format = "json";
                    }
                }

                if (format.equals("json")) {
                    httpServletResponse.setContentType("application/json;charset=UTF-8");
                    httpServletResponse.getWriter().println("{\"logged out\":\"true\"}");
                }

                if (format.equals("xml")) {
                    httpServletResponse.setContentType("application/xml;charset=UTF-8");
                    httpServletResponse.getWriter().println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><logout status=\"true\" />");
                }

                break;
            }
            case "change": {

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
                    httpServletResponse.setStatus(HttpServletResponse.SC_NOT_IMPLEMENTED);
                }

                // Json für Response body
                RequestError requestError = new RequestError();
                requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_NOT_IMPLEMENTED)));
                requestError.setCode(HttpServletResponse.SC_NOT_IMPLEMENTED);
                requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_NOT_IMPLEMENTED) + ".description"));
                requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_NOT_IMPLEMENTED) + ".uri"));

                // XML-Ausgabe mit JAXB
                if (format.equals("xml")) {

                    try {

                        JAXBContext context = JAXBContext.newInstance(RequestError.class);
                        Marshaller m = context.createMarshaller();
                        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                        // Write to HttpResponse
                        httpServletResponse.setContentType("application/xml;charset=UTF-8");
                        m.marshal(requestError, httpServletResponse.getWriter());
                    }
                    catch (PropertyException e) {
                        this.logger.error(e.getMessage(), e.getCause());
                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering the results.");
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

                // html
                if (format.equals("html")) {

                    try {

                        JAXBContext context = JAXBContext.newInstance(RequestError.class);
                        Marshaller m = context.createMarshaller();
                        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                        // Write to HttpResponse
                        StringWriter stringWriter = new StringWriter();
                        m.marshal(requestError, stringWriter);

                        Document doc = new SAXBuilder().build(new StringReader(stringWriter.toString()));

                        HashMap<String,String> parameters = new HashMap<String,String>();
                        parameters.put("lang", "de");
                        parameters.put("redirect_uri_params", URLDecoder.decode(redirect_url, "UTF-8"));

                        String html = htmlOutputter(doc, this.config.getProperty("service.requesterror.xslt"), parameters);

                        httpServletResponse.setContentType("text/html;charset=UTF-8");
                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                        httpServletResponse.getWriter().println(html);

                    }
                    catch (JAXBException e) {
                        this.logger.error(e.getMessage(), e.getCause());
                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message. Message is 'Document not found'.");
                    }
                    catch (JDOMException e) {
                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message. Message is 'Document not found'.");
                    }
                }

                break;
            }
            default: {

                this.logger.error("[" + this.config.getProperty("service.name") + "] " + HttpServletResponse.SC_BAD_REQUEST + "Unknown function! (" + service + ")");

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

                // XML-Ausgabe mit JAXB
                if (format.equals("xml")) {

                    try {

                        JAXBContext context = JAXBContext.newInstance(RequestError.class);
                        Marshaller m = context.createMarshaller();
                        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                        // Write to HttpResponse
                        httpServletResponse.setContentType("application/xml;charset=UTF-8");
                        m.marshal(requestError, httpServletResponse.getWriter());
                    }
                    catch (PropertyException e) {
                        this.logger.error(e.getMessage(), e.getCause());
                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering the results.");
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

                // html
                if (format.equals("html")) {

                    try {

                        JAXBContext context = JAXBContext.newInstance(RequestError.class);
                        Marshaller m = context.createMarshaller();
                        m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                        // Write to HttpResponse
                        StringWriter stringWriter = new StringWriter();
                        m.marshal(requestError, stringWriter);

                        Document doc = new SAXBuilder().build(new StringReader(stringWriter.toString()));

                        HashMap<String,String> parameters = new HashMap<String,String>();
                        parameters.put("lang", "de");
                        parameters.put("redirect_uri_params", URLDecoder.decode(redirect_url, "UTF-8"));

                        String html = htmlOutputter(doc, this.config.getProperty("service.requesterror.xslt"), parameters);

                        httpServletResponse.setContentType("text/html;charset=UTF-8");
                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                        httpServletResponse.getWriter().println(html);

                    }
                    catch (JAXBException e) {
                        this.logger.error(e.getMessage(), e.getCause());
                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message. Message is 'Document not found'.");
                    }
                    catch (JDOMException e) {
                        httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error: Error while rendering a HTML message. Message is 'Document not found'.");
                    }
                }
            }
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
