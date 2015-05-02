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

package de.tu_dortmund.ub.api.paia.core.model;


import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import javax.mail.internet.InternetAddress;
import javax.xml.bind.annotation.XmlElement;

/**
 * Created by cihabe on 10.10.14.
 */
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class Patron {

    private String name = "";

    // valide E-Mail-Adresse
    private InternetAddress email = null;

    // YYYY-MM-DD
    private String expires = null;

    // 0 - active
    // 1 - inactive
    // 2 - inactive because account expired
    // 3 - inactive because of outstanding fees
    // 4 - inactive because account expired and outstanding fees
    private int status = -1;

    private String username = "";
    private String old_password = "";
    private String new_password = "";

    @XmlElement
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @XmlElement
    public String getEmail() {

        String email = "";

        if (this.email != null) {
            email = this.email.getAddress();
        }

        return email;
    }

    public void setEmail(InternetAddress email) {
        this.email = email;
    }

    @XmlElement
    public String getExpires() {
        return expires;
    }

    public void setExpires(String expires) {
        String tmp[] = expires.split("\\.");
        this.expires = tmp[2] + "-" + tmp[1] + "-" + tmp[0];
    }

    @XmlElement
    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    @XmlElement
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    @XmlElement
    public String getOld_password() {
        return old_password;
    }

    public void setOld_password(String old_password) {
        this.old_password = old_password;
    }

    @XmlElement
    public String getNew_password() {
        return new_password;
    }

    public void setNew_password(String new_password) {
        this.new_password = new_password;
    }
}
