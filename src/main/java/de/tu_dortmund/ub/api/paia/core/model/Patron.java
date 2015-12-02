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
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by cihabe on 10.10.14.
 */
@XmlRootElement(name = "patron")
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
public class Patron {

    private String name;

    // valide E-Mail-Adresse
    private InternetAddress email;

    // YYYY-MM-DD
    private String expires;

    // 0 - active
    // 1 - inactive
    // 2 - inactive because account expired
    // 3 - inactive because of outstanding fees
    // 4 - inactive because account expired and outstanding fees
    private int status = -1;

    private String username;
    private String old_password;
    private String new_password;

    // additional parameters
    private String gender;
    private String dateofbirth;

    private String country;
    private String city;
    private String postalcode;
    private String street;
    private String addresssupplement;
    private String phone;

    private String country2;
    private String city2;
    private String postalcode2;
    private String street2;
    private String addresssupplement2;
    private String phone2;

    private String usergroup;
    private String externalid;
    private String faculty;


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

    @XmlElement
    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {

        if (gender.equals("M")) {
            this.gender = "1";
        }
        else if (gender.equals("F")) {
            this.gender = "2";
        }
        else {
            this.gender = gender;
        }
    }

    @XmlElement
    public String getDateofbirth() {
        return dateofbirth;
    }

    public void setDateofbirth(String dateofbirth) {
        String tmp[] = dateofbirth.split("\\.");
        this.dateofbirth = tmp[2] + "-" + tmp[1] + "-" + tmp[0];
    }

    @XmlElement
    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    @XmlElement
    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    @XmlElement
    public String getPostalcode() {
        return postalcode;
    }

    public void setPostalcode(String postalcode) {
        this.postalcode = postalcode;
    }

    @XmlElement
    public String getAddresssupplement() {
        return addresssupplement;
    }

    public void setAddresssupplement(String addresssupplement) {
        this.addresssupplement = addresssupplement;
    }

    @XmlElement
    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    @XmlElement
    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    @XmlElement
    public String getCountry2() {
        return country2;
    }

    public void setCountry2(String country2) {
        this.country2 = country2;
    }

    @XmlElement
    public String getCity2() {
        return city2;
    }

    public void setCity2(String city2) {
        this.city2 = city2;
    }

    @XmlElement
    public String getPostalcode2() {
        return postalcode2;
    }

    public void setPostalcode2(String postalcode2) {
        this.postalcode2 = postalcode2;
    }

    @XmlElement
    public String getStreet2() {
        return street2;
    }

    public void setStreet2(String street2) {
        this.street2 = street2;
    }

    @XmlElement
    public String getAddresssupplement2() {
        return addresssupplement2;
    }

    public void setAddresssupplement2(String addresssupplement2) {
        this.addresssupplement2 = addresssupplement2;
    }

    @XmlElement
    public String getPhone2() {
        return phone2;
    }

    public void setPhone2(String phone2) {
        this.phone2 = phone2;
    }

    @XmlElement
    public String getUsergroup() {
        return usergroup;
    }

    public void setUsergroup(String usergroup) {
        this.usergroup = usergroup;
    }

    @XmlElement
    public String getExternalid() {
        return externalid;
    }

    public void setExternalid(String externalid) {
        this.externalid = externalid;
    }

    @XmlElement
    public String getFaculty() {
        return faculty;
    }

    public void setFaculty(String faculty) {
        this.faculty = faculty;
    }

}
