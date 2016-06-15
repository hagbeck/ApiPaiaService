package de.tu_dortmund.ub.api.paia.core.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by ubmit (Kolt) on 29.02.2016.
 */
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
@XmlRootElement(name = "favoriteRequest")
public class FavoriteRequest {
    private String list;
    private String recordid;

    @XmlElement
    public String getList() {return list;}

    public void setList(String listName) {
        this.list = listName;
    }

    @XmlElement
    public String getRecordid() {
        return recordid;
    }

    public void setRecordid(String favoriteId) {
        this.recordid = favoriteId;
    }
}
