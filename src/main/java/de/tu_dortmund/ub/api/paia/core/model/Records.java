package de.tu_dortmund.ub.api.paia.core.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;

/**
 * Created by ubmit on 13.06.2016.
 */

@JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
@XmlRootElement(name = "records")
public class Records {
    private ArrayList<Favorite> recordids;

    @XmlElement
    public ArrayList<Favorite> getRecordids() {
        return recordids;
    }

    public void setRecordids(ArrayList<Favorite> recordids) {
        this.recordids = recordids;
    }
}
