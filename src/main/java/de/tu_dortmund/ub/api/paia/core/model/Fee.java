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

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "fee")
@JsonSerialize(include= JsonSerialize.Inclusion.NON_NULL)
public class Fee {

    private String amount;
    private String date;
    private String about;
    private String item;
    private String edition;
    private String feetype;
    private String feeid;

    private String success;
    private String errorcode;
    private String errortext;

    @XmlElement
    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    @XmlElement
    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        String tmp[] = date.split("\\.");
        this.date = tmp[2] + "-" + tmp[1] + "-" + tmp[0];
    }

    @XmlElement
    public String getAbout() {
        return about;
    }

    public void setAbout(String about) {
        this.about = about;
    }

    @XmlElement
    public String getItem() {
        return item;
    }

    public void setItem(String item) {
        this.item = item;
    }

    @XmlElement
    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    @XmlElement
    public String getFeetype() {
        return feetype;
    }

    public void setFeetype(String feetype) {
        this.feetype = feetype;
    }

    @XmlElement
    public String getFeeid() {
        return feeid;
    }

    public void setFeeid(String feeid) {
        this.feeid = feeid;
    }

    @XmlElement
    public String getSuccess() {
        return success;
    }

    public void setSuccess(String success) {
        this.success = success;
    }

    @XmlElement
    public String getErrorcode() {
        return errorcode;
    }

    public void setErrorcode(String errorcode) {
        this.errorcode = errorcode;
    }

    @XmlElement
    public String getErrortext() {
        return errortext;
    }

    public void setErrortext(String errortext) {
        this.errortext = errortext;
    }
}
