/*
The MIT License (MIT)

Copyright (c) 2015-2016, Hans-Georg Becker, http://orcid.org/0000-0003-0432-294X

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
import de.tu_dortmund.ub.api.paia.auth.model.ChangeRequest;
import de.tu_dortmund.ub.api.paia.auth.model.NewPasswordRequest;
import de.tu_dortmund.ub.api.paia.core.model.DocumentList;
import de.tu_dortmund.ub.api.paia.core.model.FeeList;
import de.tu_dortmund.ub.api.paia.core.model.Patron;
import de.tu_dortmund.ub.api.paia.interfaces.AuthorizationException;
import de.tu_dortmund.ub.api.paia.interfaces.AuthorizationInterface;
import de.tu_dortmund.ub.api.paia.interfaces.LibraryManagementSystem;
import de.tu_dortmund.ub.api.paia.interfaces.LibraryManagementSystemException;
import de.tu_dortmund.ub.api.paia.model.RequestError;
import de.tu_dortmund.ub.api.paia.auth.model.LoginRequest;
import de.tu_dortmund.ub.api.paia.auth.model.LoginResponse;
import de.tu_dortmund.ub.util.impl.Lookup;
import de.tu_dortmund.ub.util.impl.Mailer;
import de.tu_dortmund.ub.util.output.ObjectToHtmlTransformation;
import de.tu_dortmund.ub.util.output.TransformationException;
import org.apache.commons.io.IOUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.jdom2.Document;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Properties;

/**
 * This class represents the PAIA Auth component.
 *
 * @author Hans-Georg Becker
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

        this.logger.info("Starting 'PAIA Auth Endpoint' ...");
        this.logger.info("conf-file = " + this.conffile);
        this.logger.info("log4j-conf-file = " + this.config.getProperty("service.log4j-conf"));
    }

    protected void doGet(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        this.doPost(httpServletRequest, httpServletResponse);
    }

    protected void doPost(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        ObjectMapper mapper = new ObjectMapper();

        String format;
        String language;
        String redirect_url;

        this.logger.debug("PathInfo = " + httpServletRequest.getPathInfo());
        this.logger.debug("QueryString = " + httpServletRequest.getQueryString());

        String service = "";
        String authorization = "";

        String path = httpServletRequest.getPathInfo();
        String[] params = path.substring(1,path.length()).split("/");

        if (params.length == 1) {
            service = params[0];
        }

        format = "html";
        language = "";

        // Hole 'Accept' und 'Authorization' aus dem Header;
        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        while ( headerNames.hasMoreElements() ) {

            String headerNameKey = (String) headerNames.nextElement();
            this.logger.debug("headerNameKey = " + headerNameKey + " / headerNameValue = " + httpServletRequest.getHeader(headerNameKey));

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
            if (headerNameKey.equals("Accept-Language")) {
                language = httpServletRequest.getHeader( headerNameKey );
                this.logger.debug("Accept-Language: " + language);
            }
            if (headerNameKey.equals("Authorization")) {
                authorization = httpServletRequest.getHeader( headerNameKey );
            }
        }

        this.logger.debug("Service: " + service);

        if (httpServletRequest.getParameter("format") != null && !httpServletRequest.getParameter("format").equals("")) {

            format = httpServletRequest.getParameter("format");
        }

        this.logger.info("format = " + format);

        if (format.equals("html") && Lookup.lookupAll(ObjectToHtmlTransformation.class).size() == 0) {

            this.logger.error(HttpServletResponse.SC_BAD_REQUEST + ": " + "html not implemented!");

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

            this.sendRequestError(httpServletResponse, requestError, format, language, "");
        }
        else {

            // redirect_url
            redirect_url = "";

            if (httpServletRequest.getParameter("redirect_url") != null && !httpServletRequest.getParameter("redirect_url").equals("")) {

                if (httpServletRequest.getParameter("redirect_url").contains("redirect_url=")) {
                    String tmp[] = httpServletRequest.getParameter("redirect_url").split("redirect_url=");

                    redirect_url = tmp[0] + "redirect_url=" + URLEncoder.encode(tmp[1], "UTF-8");
                }
                else {
                    redirect_url = httpServletRequest.getParameter("redirect_url");
                }
            }

            this.logger.info("redirect_url = " + redirect_url);

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

            this.logger.info("language = " + language);

            if (authorization.equals("") && httpServletRequest.getParameter("access_token") != null) {

                authorization = httpServletRequest.getParameter("access_token");
            }

            if (authorization.equals("")) {

                // if exists PaiaService-Cookie: read content
                Cookie[] cookies = httpServletRequest.getCookies();

                if (cookies != null) {
                    for (Cookie cookie : cookies) {
                        if (cookie.getName().equals("PaiaService")) {

                            String value = URLDecoder.decode(cookie.getValue(), "UTF-8");
                            this.logger.info(value);
                            LoginResponse loginResponse = mapper.readValue(value, LoginResponse.class);

                            authorization = loginResponse.getAccess_token();

                            break;
                        }
                    }
                }
            }

            this.logger.debug("Access_token: " + authorization);

            StringBuffer jb = new StringBuffer();
            String line = null;
            try {
                BufferedReader reader = httpServletRequest.getReader();
                while ((line = reader.readLine()) != null)
                    jb.append(line);
            } catch (Exception e) { /*report an error*/ }

            String requestBody = jb.toString();

            this.logger.info(requestBody);

            httpServletResponse.setHeader("Access-Control-Allow-Origin", this.config.getProperty("Access-Control-Allow-Origin"));
            httpServletResponse.setHeader("Cache-Control", this.config.getProperty("Cache-Control"));

            // 2. Schritt: Service
            if (service.equals("login") || service.equals("logout") || service.equals("change") || service.equals("renew")) {

                this.provideService(httpServletRequest, httpServletResponse, service, authorization, requestBody, format, language, redirect_url);
            }
            else {

                this.logger.error(HttpServletResponse.SC_METHOD_NOT_ALLOWED + ": " + "POST for '" + service + "' not allowed!");

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

                this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
            }
        }
    }

    protected void doOptions(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {

        httpServletResponse.setHeader("Access-Control-Allow-Methods", this.config.getProperty("Access-Control-Allow-Methods"));
        httpServletResponse.addHeader("Access-Control-Allow-Headers", this.config.getProperty("Access-Control-Allow-Headers"));
        httpServletResponse.setHeader("Accept", this.config.getProperty("Accept"));
        httpServletResponse.setHeader("Access-Control-Allow-Origin", this.config.getProperty("Access-Control-Allow-Origin"));
        httpServletResponse.setHeader("Cache-Control", this.config.getProperty("Cache-Control"));

        httpServletResponse.getWriter().println();
    }

    /**
     * PAIAauth services: Prüfe jeweils die scopes und liefere die Daten
     */
    private void provideService(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String service, String access_token, String requestBody, String format, String language, String redirect_url) throws IOException {

        ObjectMapper mapper = new ObjectMapper();

        switch (service) {

            case "login": {

                if (Lookup.lookupAll(AuthorizationInterface.class).size() > 0) {

                    AuthorizationInterface authorizationInterface = Lookup.lookup(AuthorizationInterface.class);
                    // init Authorization Service
                    authorizationInterface.init(this.config);

                    // if access_token not equals "" >> delete token + new login
                    if (!access_token.equals("")) {

                        // AuthorizationInterface.revokeToken()
                        try {

                            boolean isRevoked = authorizationInterface.revokeToken(access_token);
                        }
                        catch (AuthorizationException e) {

                            // TODO correct error handling
                            this.logger.error(HttpServletResponse.SC_UNAUTHORIZED + "!");
                        }

                        // delete cookie
                        Cookie cookie = new Cookie("PaiaService", null);
                        if (this.config.getProperty("service.cookie.domain") != null && !this.config.getProperty("service.cookie.domain").equals("")) {
                            cookie.setDomain(this.config.getProperty("service.cookie.domain"));
                        }
                        cookie.setMaxAge(0);
                        cookie.setPath("/");
                        httpServletResponse.addCookie(cookie);

                        // cleanup variable
                        access_token = "";
                    }

                    // analyse on request data
                    LoginRequest loginRequest = null;
                    try {

                        loginRequest = mapper.readValue(requestBody, LoginRequest.class);

                        if (httpServletRequest.getParameter("redirect_url") != null && !httpServletRequest.getParameter("redirect_url").equals("")) {

                            redirect_url = httpServletRequest.getParameter("redirect_url");
                        }
                    }
                    catch (Exception e) {

                        if (requestBody != null && !requestBody.equals("")) {

                            String[] params = requestBody.split("&");

                            if (params.length > 1) {

                                loginRequest = new LoginRequest();

                                for (String param : params) {

                                    if (param.startsWith("grant_type")) {
                                        loginRequest.setGrant_type(param.split("=")[1]);
                                    } else if (param.startsWith("username")) {
                                        loginRequest.setUsername(param.split("=")[1]);
                                    } else if (param.startsWith("password")) {
                                        loginRequest.setPassword(param.split("=")[1]);
                                    } else if (param.startsWith("scope")) {
                                        loginRequest.setScope(param.split("=")[1]);
                                    } else if (param.startsWith("format")) {
                                        format = param.split("=")[1];
                                        this.logger.info("format = " + format);
                                    } else if (param.startsWith("redirect_url")) {
                                        redirect_url = URLDecoder.decode(param.split("=")[1], "UTF-8");
                                        this.logger.info("redirect_url = " + redirect_url);
                                    } else {
                                        // Tu nix
                                    }
                                }
                            }
                        }
                        else if (httpServletRequest.getParameter("grant_type") != null && !httpServletRequest.getParameter("grant_type").equals("") &&
                                httpServletRequest.getParameter("username") != null && !httpServletRequest.getParameter("username").equals("") &&
                                httpServletRequest.getParameter("password") != null && !httpServletRequest.getParameter("password").equals("")
                                ) {

                            loginRequest = new LoginRequest();
                            loginRequest.setGrant_type(httpServletRequest.getParameter("grant_type"));
                            loginRequest.setUsername(httpServletRequest.getParameter("username"));
                            loginRequest.setPassword(httpServletRequest.getParameter("password"));
                            if (httpServletRequest.getParameter("scope") != null && !httpServletRequest.getParameter("scope").equals("")) {
                                loginRequest.setScope(httpServletRequest.getParameter("scope"));
                            }
                            if (httpServletRequest.getParameter("redirect_url") != null && !httpServletRequest.getParameter("redirect_url").equals("")) {

                                redirect_url = httpServletRequest.getParameter("redirect_url");
                            }
                        }
                        else {
                            loginRequest = null;
                        }
                    }

                    // do login
                    if (loginRequest != null && loginRequest.getUsername() != null && loginRequest.getPassword() != null && loginRequest.getGrant_type() != null && loginRequest.getGrant_type().equals("password")) {

                        String scope = "read_patron read_fees read_items write_items"; // TODO config-properties
                        if (loginRequest.getScope() != null && !loginRequest.getScope().equals("")) {

                            scope = loginRequest.getScope();
                        }

                        // AuthorizationInterface.getToken()
                        String responseJson = "";
                        try {

                            responseJson = authorizationInterface.getToken(scope, loginRequest.getUsername(), loginRequest.getPassword());
                        }
                        catch (AuthorizationException e) {

                            // TODO correct error handling
                            this.logger.error(HttpServletResponse.SC_UNAUTHORIZED + "!");
                        }

                        if (!responseJson.equals("")) {

                            LoginResponse loginResponse = mapper.readValue(responseJson, LoginResponse.class);

                            // anpassen des loginResponse
                            loginResponse.setRefresh_token(null);
                            loginResponse.setRefresh_expires_in(null);
                            loginResponse.setPatron(loginRequest.getUsername());

                            httpServletResponse.setHeader("Access-Control-Allow-Origin", this.config.getProperty("Access-Control-Allow-Origin"));
                            httpServletResponse.setHeader("Cache-Control", this.config.getProperty("Cache-Control"));
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                            // add cookie
                            StringWriter stringWriter = new StringWriter();
                            mapper.writeValue(stringWriter, loginResponse);
                            Cookie cookie = new Cookie("PaiaService", URLEncoder.encode(stringWriter.toString(), "UTF-8"));
                            if (this.config.getProperty("service.cookie.domain") != null && !this.config.getProperty("service.cookie.domain").equals("")) {
                                cookie.setDomain(this.config.getProperty("service.cookie.domain"));
                            }
                            cookie.setMaxAge(-1);
                            cookie.setPath("/");
                            httpServletResponse.addCookie(cookie);

                            // extent redirect_url
                            this.logger.info("redirect_url: " + redirect_url);
                            if (redirect_url.startsWith(this.config.getProperty("service.base_url") + "/core")) {

                                if (redirect_url.endsWith("core/")) {
                                    redirect_url += loginResponse.getPatron();
                                }
                                else if (redirect_url.endsWith("core")) {
                                    redirect_url += "/" + loginResponse.getPatron();
                                }
                                else if (redirect_url.contains("/patronid/")) {

                                    redirect_url = redirect_url.replaceAll("/patronid/", "/" + loginResponse.getPatron() + "/");
                                }
                                else {
                                    // nix
                                }
                            }
                            this.logger.info("redirect_url: " + redirect_url);

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

                                    // TODO redirect_url += loginResponse.getPatron();
                                }
                                this.logger.info("redirect_url = " + redirect_url);

                                httpServletResponse.sendRedirect(redirect_url);
                            }
                        }
                        else {

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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                        }
                    }
                    // else Baue HTML-Seite mit login-Formular mittels XSLT
                    else {

                        httpServletResponse.setHeader("WWW-Authentificate", "Bearer");
                        httpServletResponse.setHeader("WWW-Authentificate", "Bearer realm=\"PAIA auth\"");
                        httpServletResponse.setContentType("application/json");
                        httpServletResponse.setHeader("Access-Control-Allow-Origin", config.getProperty("Access-Control-Allow-Origin"));
                        httpServletResponse.setHeader("Cache-Control", config.getProperty("Cache-Control"));

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

                        if (format.equals("html")) {

                            if (Lookup.lookupAll(ObjectToHtmlTransformation.class).size() > 0) {

                                try {
                                    ObjectToHtmlTransformation htmlTransformation = Lookup.lookup(ObjectToHtmlTransformation.class);
                                    // init transformator
                                    htmlTransformation.init(this.config);

                                    HashMap<String, String> parameters = new HashMap<String, String>();
                                    parameters.put("lang", language);
                                    parameters.put("redirect_url", redirect_url);

                                    //String provider = "http://" + httpServletRequest.getServerName() + ":" + httpServletRequest.getServerPort() + this.config.getProperty("service.endpoint.auth") + "/" + service;
                                    String provider = this.config.getProperty("service.base_url") + this.config.getProperty("service.endpoint.auth") + "/" + service;
                                    parameters.put("formURL", provider);

                                    httpServletResponse.setContentType("text/html;charset=UTF-8");
                                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                                    httpServletResponse.getWriter().println(htmlTransformation.transform(new Document(), parameters));
                                }
                                catch (TransformationException e) {
                                    e.printStackTrace();
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
                }
                else {

                    this.logger.error(HttpServletResponse.SC_SERVICE_UNAVAILABLE + ": Config Error!");

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
            case "logout": {

                if (Lookup.lookupAll(AuthorizationInterface.class).size() > 0) {

                    AuthorizationInterface authorizationInterface = Lookup.lookup(AuthorizationInterface.class);
                    // init Authorization Service
                    authorizationInterface.init(this.config);

                    if (!access_token.equals("")) {

                        // AuthorizationInterface.revokeToken()
                        try {

                            boolean isRevoked = authorizationInterface.revokeToken(access_token);
                        }
                        catch (AuthorizationException e) {

                            // TODO correct error handling
                            this.logger.error(HttpServletResponse.SC_UNAUTHORIZED + "!");
                        }
                    }

                    httpServletResponse.setHeader("Access-Control-Allow-Origin", config.getProperty("Access-Control-Allow-Origin"));
                    httpServletResponse.setHeader("Cache-Control", config.getProperty("Cache-Control"));
                    httpServletResponse.setStatus(HttpServletResponse.SC_OK);

                    // delete cookie
                    Cookie cookie = new Cookie("PaiaService", null);
                    if (this.config.getProperty("service.cookie.domain") != null && !this.config.getProperty("service.cookie.domain").equals("")) {
                        cookie.setDomain(this.config.getProperty("service.cookie.domain"));
                    }
                    cookie.setMaxAge(0);
                    cookie.setPath("/");
                    httpServletResponse.addCookie(cookie);

                    // html >> redirect
                    if (format.equals("html")) {

                        if (httpServletRequest.getParameter("redirect_url") != null && !httpServletRequest.getParameter("redirect_url").equals("")) {

                            redirect_url = httpServletRequest.getParameter("redirect_url");
                        }
                        else {

                            redirect_url = this.config.getProperty("service.auth.logout.redirect.default");
                        }

                        httpServletResponse.sendRedirect(redirect_url);
                    }

                    if (format.equals("json")) {
                        httpServletResponse.setContentType("application/json;charset=UTF-8");
                        httpServletResponse.getWriter().println("{\"logged out\":\"true\"}");
                    }

                    if (format.equals("xml")) {
                        httpServletResponse.setContentType("application/xml;charset=UTF-8");
                        httpServletResponse.getWriter().println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><logout status=\"true\" />");
                    }

                }
                else {

                    this.logger.error(HttpServletResponse.SC_SERVICE_UNAVAILABLE + ": Config Error!");

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
            case "change": {

                // build ChangeRequest object
                ChangeRequest changeRequest = mapper.readValue(requestBody, ChangeRequest.class);

                // check token ...
                boolean isAuthorized = false;

                if (access_token != null && !access_token.equals("")) {

                    if (Lookup.lookupAll(AuthorizationInterface.class).size() > 0) {

                        AuthorizationInterface authorizationInterface = Lookup.lookup(AuthorizationInterface.class);
                        // init Authorization Service
                        authorizationInterface.init(this.config);

                        try {

                            isAuthorized = authorizationInterface.isTokenValid(httpServletResponse, service, changeRequest.getPatron(), access_token);
                        }
                        catch (AuthorizationException e) {

                            // TODO correct error handling
                            this.logger.error(HttpServletResponse.SC_UNAUTHORIZED + "!");
                        }
                    }
                    else {

                        // TODO correct error handling
                        this.logger.error(HttpServletResponse.SC_INTERNAL_SERVER_ERROR + ": " + "Authorization Interface not implemented!");
                    }
                }

                this.logger.debug("Authorization: " + access_token + " - " + isAuthorized);

                if (!isAuthorized) {

                    // Authorization
                    this.authorize(httpServletRequest, httpServletResponse, format);
                }
                else {


                    if (Lookup.lookupAll(LibraryManagementSystem.class).size() > 0) {

                        LibraryManagementSystem libraryManagementSystem = Lookup.lookup(LibraryManagementSystem.class);
                        // init ILS
                        libraryManagementSystem.init(this.config);

                        // exists patron?
                        // use LibraryManagementSystem.patron(): failed = Exception!
                        try {

                            Patron patron = libraryManagementSystem.patron(changeRequest.getPatron(), false);

                            boolean isChanged = libraryManagementSystem.changePassword(changeRequest);

                            if (isChanged) {


                                // E-Mail to user
                                Mailer mailer = new Mailer(this.config.getProperty("service.mailer.conf"));

                                try {

                                    // TODO patron.getEMail()
                                    mailer.postMail(this.config.getProperty("service.mailer.change.subject"), this.config.getProperty("service.mailer.change.message"));

                                } catch (MessagingException e1) {

                                    this.logger.error(e1.getMessage(), e1.getCause());
                                }

                                this.logger.info("Password changed. Mail send to '" + patron.getEmail() + "'.");

                                // 200 OK
                                if (format.equals("html")) {

                                    format = "json"; // TODO or what else?
                                }

                                Patron responsePatron = new Patron();
                                responsePatron.setUsername(patron.getUsername());
                                responsePatron.setStatus(patron.getStatus());
                                responsePatron.setEmail(new InternetAddress(patron.getEmail()));

                                if (format.equals("json")) {

                                    httpServletResponse.setContentType("application/json;charset=UTF-8");
                                    mapper.writeValue(httpServletResponse.getWriter(), responsePatron);
                                }

                                if (format.equals("xml")) {

                                    JAXBContext context = JAXBContext.newInstance(Patron.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    httpServletResponse.setContentType("application/xml;charset=UTF-8");
                                    m.marshal(responsePatron, httpServletResponse.getWriter());
                                }
                            } else {

                                // 401 UNAUTHORIZED
                                this.logger.error(HttpServletResponse.SC_UNAUTHORIZED + ": Wrong old password!");

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

                                this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                            }
                        } catch (LibraryManagementSystemException e) {

                            // 401 UNAUTHORIZED
                            this.logger.error(HttpServletResponse.SC_UNAUTHORIZED + ": " + e.getMessage());

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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                        } catch (Exception e) {

                            this.logger.error(HttpServletResponse.SC_INTERNAL_SERVER_ERROR + ": Config Error!");

                            // Error handling mit suppress_response_codes=true
                            if (httpServletRequest.getParameter("suppress_response_codes") != null) {
                                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                            }
                            // Error handling mit suppress_response_codes=false (=default)
                            else {
                                httpServletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            }

                            // Json für Response body
                            RequestError requestError = new RequestError();
                            requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)));
                            requestError.setCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR) + ".description"));
                            requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR) + ".uri"));

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                        }
                    } else {

                        this.logger.error(HttpServletResponse.SC_INTERNAL_SERVER_ERROR + ": Config Error!");

                        // Error handling mit suppress_response_codes=true
                        if (httpServletRequest.getParameter("suppress_response_codes") != null) {
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                        }
                        // Error handling mit suppress_response_codes=false (=default)
                        else {
                            httpServletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        }

                        // Json für Response body
                        RequestError requestError = new RequestError();
                        requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)));
                        requestError.setCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR) + ".description"));
                        requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR) + ".uri"));

                        this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                    }
                }

                break;
            }
            case "renew": {

                if (Lookup.lookupAll(LibraryManagementSystem.class).size() > 0) {

                    LibraryManagementSystem libraryManagementSystem = Lookup.lookup(LibraryManagementSystem.class);
                    // init ILS
                    libraryManagementSystem.init(this.config);

                    // exists patron?
                    // use LibraryManagementSystem.patron(): failed = Exception!
                    try {

                        // build NewPasswordRequest object
                        NewPasswordRequest newPasswordRequest = mapper.readValue(requestBody, NewPasswordRequest.class);

                        Patron patron = libraryManagementSystem.patron(newPasswordRequest.getPatron(), true);

                        if (patron.getEmail() != null && !patron.getEmail().equals("")) {

                            boolean isRenewed = libraryManagementSystem.renewPassword(newPasswordRequest, patron);

                            if (isRenewed) {

                                // E-Mail to user
                                Mailer mailer = new Mailer(this.config.getProperty("service.mailer.conf"));

                                try {

                                    // TODO patron.getEMail()
                                    mailer.postMail(this.config.getProperty("service.mailer.renew.subject"), this.config.getProperty("service.mailer.renew.message"));

                                } catch (MessagingException e1) {

                                    this.logger.error(e1.getMessage(), e1.getCause());
                                }

                                this.logger.info("Password resetted. Mail send to '" + patron.getEmail() + "'.");

                                // 200 OK
                                if (format.equals("html")) {

                                    format = "json"; // TODO or what else?
                                }

                                Patron responsePatron = new Patron();
                                responsePatron.setUsername(patron.getUsername());
                                responsePatron.setStatus(patron.getStatus());
                                responsePatron.setEmail(new InternetAddress(patron.getEmail()));

                                if (format.equals("json")) {

                                    httpServletResponse.setContentType("application/json;charset=UTF-8");
                                    mapper.writeValue(httpServletResponse.getWriter(), responsePatron);
                                }

                                if (format.equals("xml")) {

                                    JAXBContext context = JAXBContext.newInstance(Patron.class);
                                    Marshaller m = context.createMarshaller();
                                    m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                                    // Write to HttpResponse
                                    httpServletResponse.setContentType("application/xml;charset=UTF-8");
                                    m.marshal(responsePatron, httpServletResponse.getWriter());
                                }
                            }
                            else {

                                // 401 SC_UNAUTHORIZED
                                this.logger.error(HttpServletResponse.SC_UNAUTHORIZED + ": Wrong usergroup!");

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

                                this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                            }
                        }
                        else {

                            // 401 SC_UNAUTHORIZED
                            this.logger.error(HttpServletResponse.SC_UNAUTHORIZED + ": No E-Mail-Address exists!");

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

                            this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                        }
                    }
                    catch (LibraryManagementSystemException e) {

                        e.printStackTrace();

                        // 400 SC_BAD_REQUEST
                        this.logger.error(HttpServletResponse.SC_BAD_REQUEST + ": " + e.getMessage());

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

                        this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                    }
                    catch (Exception e) {

                        this.logger.error(HttpServletResponse.SC_INTERNAL_SERVER_ERROR + ": Config Error!");

                        // Error handling mit suppress_response_codes=true
                        if (httpServletRequest.getParameter("suppress_response_codes") != null) {
                            httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                        }
                        // Error handling mit suppress_response_codes=false (=default)
                        else {
                            httpServletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        }

                        // Json für Response body
                        RequestError requestError = new RequestError();
                        requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)));
                        requestError.setCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR) + ".description"));
                        requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR) + ".uri"));

                        this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                    }
                }
                else {

                    this.logger.error(HttpServletResponse.SC_INTERNAL_SERVER_ERROR + ": Config Error!");

                    // Error handling mit suppress_response_codes=true
                    if (httpServletRequest.getParameter("suppress_response_codes") != null) {
                        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
                    }
                    // Error handling mit suppress_response_codes=false (=default)
                    else {
                        httpServletResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    }

                    // Json für Response body
                    RequestError requestError = new RequestError();
                    requestError.setError(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)));
                    requestError.setCode(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    requestError.setDescription(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR) + ".description"));
                    requestError.setErrorUri(this.config.getProperty("error." + Integer.toString(HttpServletResponse.SC_INTERNAL_SERVER_ERROR) + ".uri"));

                    this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
                }

                break;
            }
            default: {

                this.logger.error(HttpServletResponse.SC_BAD_REQUEST + "Unknown function! (" + service + ")");

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

                this.sendRequestError(httpServletResponse, requestError, format, language, redirect_url);
            }
        }
    }

    /**
     *
     * @param httpServletRequest
     * @param httpServletResponse
     * @throws IOException
     */
    private void authorize(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, String format) throws IOException {

        httpServletResponse.setHeader("Access-Control-Allow-Origin", config.getProperty("Access-Control-Allow-Origin"));
        httpServletResponse.setHeader("Cache-Control", config.getProperty("Cache-Control"));

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

        // html > redirect zu "PAIA auth - login" mit redirect_url = "PAIA auth - service"
        if (format.equals("html")) {

            httpServletResponse.setContentType("text/html;charset=UTF-8");

            String redirect_url = this.config.getProperty("service.base_url") + this.config.getProperty("service.endpoint.auth") + httpServletRequest.getPathInfo();
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

    private void sendRequestError(HttpServletResponse httpServletResponse, RequestError requestError, String format, String language, String redirect_url) {

        httpServletResponse.setHeader("Access-Control-Allow-Origin", config.getProperty("Access-Control-Allow-Origin"));
        httpServletResponse.setHeader("Cache-Control", config.getProperty("Cache-Control"));

        ObjectMapper mapper = new ObjectMapper();

        httpServletResponse.setHeader("WWW-Authentificate", "Bearer");
        httpServletResponse.setHeader("WWW-Authentificate", "Bearer realm=\"PAIA auth\"");
        httpServletResponse.setContentType("application/json");

        try {

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
