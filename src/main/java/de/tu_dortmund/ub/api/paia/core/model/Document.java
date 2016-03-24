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

package de.tu_dortmund.ub.api.paia.core.model;


import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
@XmlRootElement(name = "doc")
public class Document {

    // 1..1 document status		status (0, 1, 2, 3, 4, or 5)
    private int status;
    // 0..1 URI 				URI of a particular copy
    private String item;
    // 0..1 URI 				URI of a the document (no particular copy)
    private String edition;
    // 0..1 string 				textual description of the document
    private String about;
    // 0..1 string 				call number, shelf mark or similar item label
    private String label;

    // 0..1 int                 number of waiting requests for the document or item
    private int queue;

    // 0..1 int              	number of times the document has been renewed
    private int renewals;
    // 0..1 int              	number of times the patron has been reminded
    private int reminder;

    // 0..1	boolean         	whether an ordered or provided document can be canceled
    private  boolean cancancel;
    // 0..1	boolean         	whether a document can be renewed
    private  boolean canrenew;

    // 0..1 date 				date and time when the status began
    private String starttime;
    // 0..1 date 				date and time when the status will expire
    private String endtime;

    // 0..1 date 				date of expiry of the document statue (most times loan)
    private String duedate;
    // 0..1 string 				location of the document
    private String storage;
    // 0..1 URI 				location URI
    private String storage_id;

    // 0..1 error               error message, for instance if a request was rejected
    private String error;

    // additional parameters
    private boolean recalled; // whether the document is recalled

    private boolean renewable; // whether the document is renewable

    @XmlElement
    public int getStatus() {
        return status;
    }
    public void setStatus(int status) {
        this.status = status;
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
    public String getAbout() {
        return about;
    }
    public void setAbout(String about) {
        this.about = about;
    }
    @XmlElement
    public String getLabel() {
        return label;
    }
    public void setLabel(String label) {
        this.label = label;
    }

    @XmlElement
    public String getStarttime() {
        return starttime;
    }

    public void setStarttime(String starttime) {
        String tmp[] = starttime.split("\\.");
        this.starttime = tmp[2] + "-" + tmp[1] + "-" + tmp[0];
    }

    @XmlElement
    public String getEndtime() {
        return endtime;
    }

    public void setEndtime(String endtime) {
        String tmp[] = endtime.split("\\.");
        this.endtime = tmp[2] + "-" + tmp[1] + "-" + tmp[0];
    }

    @XmlElement
    public String getDuedate() {
        return duedate;
    }
    public void setDuedate(String duedate) {
        String tmp[] = duedate.split("\\.");
        this.duedate = tmp[2] + "-" + tmp[1] + "-" + tmp[0];
    }
    @XmlElement
    public String getStorage() {
        return storage;
    }
    public void setStorage(String storage) {
        this.storage = storage;
    }
    @XmlElement
    public String getStorage_id() {
        return storage_id;
    }
    public void setStorage_id(String storage_id) {
        this.storage_id = storage_id;
    }

    @XmlElement
    public String getError() {
        return error;
    }
    public void setError(String error) {
        this.error = error;
    }

    @XmlElement
    public int getQueue() {
        return queue;
    }

    public void setQueue(int queue) {
        this.queue = queue;
    }

    @XmlElement
    public int getRenewals() {
        return renewals;
    }

    public void setRenewals(int renewals) {
        this.renewals = renewals;
    }

    @XmlElement
    public int getReminder() {
        return reminder;
    }

    public void setReminder(int reminder) {
        this.reminder = reminder;
    }

    @XmlElement ( name = "cancancel")
    public boolean isCancancel() {
        return cancancel;
    }

    public void setCancancel(boolean cancancel) {
        this.cancancel = cancancel;
    }

    @XmlElement ( name = "canrenew")
    public boolean isCanrenew() {
        return canrenew;
    }

    public void setCanrenew(boolean canrenew) {
        this.canrenew = canrenew;
    }

    @XmlElement
    public boolean isRecalled() {
        return recalled;
    }

    public void setRecalled(boolean recalled) {
        this.recalled = recalled;
    }

    @XmlElement
    public boolean isRenewable() {
        return renewable;
    }

    public void setRenewable(boolean renewable) {
        this.renewable = renewable;
    }
}
