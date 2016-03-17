/*
The MIT License (MIT)

Copyright (c) 2015-2016, Hans-Georg Becker

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

package de.tu_dortmund.ub.api.paia.interfaces;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Properties;

/**
 * Interface for Authorization services, e.g. OAuth or Shibboleth
 */
public interface AuthorizationInterface {

    void init(Properties properties);

    HashMap<String,String> health(Properties properties);

    /**
     * returns an OAuth 2.0-like JSON string or null
     *
     * @param scope
     * @param username
     * @param password
     * @return
     * @throws AuthorizationException
     */
    String getToken(String scope, String username, String password) throws AuthorizationException;

    boolean isTokenValid(HttpServletResponse httpServletResponse, String service, String patronid, String access_token) throws AuthorizationException;

    boolean revokeToken(String token) throws AuthorizationException;

    String getAuthCookies(Cookie[] cookies) throws AuthorizationException;
}