package de.tu_dortmund.ub.api.paia.core.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Created by ubmit on 18.02.2016.
 */
@Deprecated
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
@XmlRootElement(name = "fav")
public class Favorite {

    private String id;

    @XmlElement
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
