package de.tu_dortmund.ub.api.paia.core.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.Set;

/**
 * Created by ubmit on 14.07.2016.
 */
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
@XmlRootElement(name = "listOfLists")
public class FavoriteListList {
    // private String patron;
    private Set<String> favoriteLists;

    @XmlElement
    public Set<String> getFavoriteLists() {
        return favoriteLists;
    }
    public void setFavoriteLists(Set<String> favoriteLists) {
        this.favoriteLists = favoriteLists;
    }

    /*
    @XmlElement
    public String getPatron() {
        return patron;
    }
    public void setPatron(String patron) {
        this.patron = patron;
    }
    */
}
