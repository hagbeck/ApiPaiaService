package de.tu_dortmund.ub.api.paia.core.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;

/**
 * Created by ubmit on 18.02.2016.
 */
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
@XmlRootElement(name = "favorites")
public class FavoriteList {

    private String list;
    private ArrayList<Favorite> recordids;

    @XmlElement
    public ArrayList<Favorite> getRecordids() {return recordids;}

    public void setRecordids(ArrayList<Favorite> favs) {this.recordids = favs;}

    @XmlAttribute
    public String getList() {return list;}

    public void setList(String name) {this.list = name;}
}
