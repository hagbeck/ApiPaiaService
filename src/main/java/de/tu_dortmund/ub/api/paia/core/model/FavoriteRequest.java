package de.tu_dortmund.ub.api.paia.core.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;

/**
 * Created by ubmit on 13.09.2016.
 */
@Deprecated
@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
@XmlRootElement(name = "favoriteRequest")
public class FavoriteRequest {
    private String list;
    private ArrayList<Favorite> favorites;

    @XmlElement
    public ArrayList<Favorite> getFavorites() {
        return favorites;
    }

    public void setFavorites(ArrayList<Favorite> favorites) {
        this.favorites = favorites;
    }

    @XmlElement
    public String getList() {
        return list;
    }

    public void setList(String list) {
        this.list = list;
    }


}
