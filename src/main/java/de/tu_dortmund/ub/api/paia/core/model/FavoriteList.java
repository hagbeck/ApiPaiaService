package de.tu_dortmund.ub.api.paia.core.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;

/**
 * Created by ubmit (Kolt) on 29.02.2016.
 */
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
@XmlRootElement(name = "favoriteList")
public class FavoriteList {
    private String list;
    private String application;

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    private ArrayList<Favorite> favorites;

    @XmlElement
    public ArrayList<Favorite> getFavorites() {
        return favorites;
    }

    public void setFavorites(ArrayList<Favorite> favorites) {
        this.favorites = favorites;
    }

    @XmlElement
    public String getList() {return list;}

    public void setList(String listName) {
        this.list = listName;
    }

}
