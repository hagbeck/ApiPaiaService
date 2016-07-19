package de.tu_dortmund.ub.api.paia.core.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;

/**
 * Created by ubmit (Kolt) on 29.02.2016.
 */
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
@XmlRootElement(name = "favoriteRequest")
public class FavoriteList {
    private String list;
    private ArrayList<String> recordids;

    @XmlElement
    public String getList() {return list;}

    public void setList(String listName) {
        this.list = listName;
    }

    @XmlElement
    public ArrayList<String> getRecordids() {
        return recordids;
    }

    public void setRecordids(ArrayList<String> recordids) {
        this.recordids = recordids;
    }
}
