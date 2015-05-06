package de.tu_dortmund.ub.api.paia.core.ils;

import de.tu_dortmund.ub.api.paia.core.model.Patron;
import de.tu_dortmund.ub.api.paia.core.model.Document;
import de.tu_dortmund.ub.api.paia.core.model.DocumentList;
import de.tu_dortmund.ub.api.paia.core.model.Fee;
import de.tu_dortmund.ub.api.paia.core.model.FeeList;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.oclcpica.xslnp.client.SLNPAllUserData.Request_SLNPAllUserData;
import org.oclcpica.xslnp.client.SLNPAllUserData.Response_SLNPAllUserData;
import org.oclcpica.xslnp.client.SLNPAllUserData.SLNPAllUserDataInterface;
import org.oclcpica.xslnp.client.SLNPAllUserData.SLNPAllUserData_Impl;
import org.oclcpica.xslnp.client.SLNPCancelOrderReservation.Request_SLNPCancelOrderReservation;
import org.oclcpica.xslnp.client.SLNPCancelOrderReservation.Response_SLNPCancelOrderReservation;
import org.oclcpica.xslnp.client.SLNPCancelOrderReservation.SLNPCancelOrderReservationInterface;
import org.oclcpica.xslnp.client.SLNPCancelOrderReservation.SLNPCancelOrderReservation_Impl;
import org.oclcpica.xslnp.client.SLNPOpsHitListPresent.*;
import org.oclcpica.xslnp.client.SLNPOpsSearch.Request_SLNPOpsSearch;
import org.oclcpica.xslnp.client.SLNPOpsSearch.Response_SLNPOpsSearch;
import org.oclcpica.xslnp.client.SLNPOpsSearch.SLNPOpsSearchInterface;
import org.oclcpica.xslnp.client.SLNPOpsSearch.SLNPOpsSearch_Impl;
import org.oclcpica.xslnp.client.SLNPReservation.Request_SLNPReservation;
import org.oclcpica.xslnp.client.SLNPReservation.Response_SLNPReservation;
import org.oclcpica.xslnp.client.SLNPReservation.SLNPReservationInterface;
import org.oclcpica.xslnp.client.SLNPReservation.SLNPReservation_Impl;
import org.oclcpica.xslnp.client.SLNPSingleRenewal.*;
import org.oclcpica.xslnp.client.SLNPUserAccount.*;
import org.oclcpica.xslnp.client.SLNPUserAccountShort.Request_SLNPUserAccountShort;
import org.oclcpica.xslnp.client.SLNPUserAccountShort.Response_SLNPUserAccountShort;
import org.oclcpica.xslnp.client.SLNPUserAccountShort.SLNPUserAccountShortInterface;
import org.oclcpica.xslnp.client.SLNPUserAccountShort.SLNPUserAccountShort_Impl;
import org.oclcpica.xslnp.client.SLNPWSLogin.Request_SLNPWSLogin;
import org.oclcpica.xslnp.client.SLNPWSLogin.Response_SLNPWSLogin;
import org.oclcpica.xslnp.client.SLNPWSLogin.SLNPWSLoginInterface;
import org.oclcpica.xslnp.client.SLNPWSLogin.SLNPWSLogin_Impl;
import org.oclcpica.xslnp.client.SLNPWSLogout.*;
import org.oclcpica.xslnp.client.SLNPWSLogout.InvalidSessionException_Exception;
import org.oclcpica.xslnp.client.SLNPWSLogout.SLNPException_Exception;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.xml.rpc.Stub;
import java.io.*;
import java.net.URLEncoder;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Properties;

/**
 * Created by cihabe on 10.02.14.
 */
public class IlsSunRise implements IntegratedLibrarySystem {

    private Properties config = null;
    private Logger logger = null;

    private String sessionID = null;

    @Override
    public void init(Properties properties) {

        this.config = properties;
        PropertyConfigurator.configure(this.config.getProperty("service.log4j-conf"));
        this.logger = Logger.getLogger(IlsSunRise.class.getName());
    }

    @Override
    public Patron patron(String patronid, boolean extended) throws ILSException {

        Patron patron = new Patron();
        patron.setUsername(patronid);

        this.logger.debug("patronid = " + patronid);
        String borrowerNumber = this.getBorrowerNumber(patronid);
        if (borrowerNumber != null) {
            patronid = borrowerNumber;
        }
        this.logger.debug("patronid = " + patronid);

        Stub stub = createSLNPAllUserDataProxy();

        stub._setProperty(javax.xml.rpc.Stub.ENDPOINT_ADDRESS_PROPERTY, this.config.getProperty("ils.xslnp.service.url") + "SLNPAllUserData");
        SLNPAllUserDataInterface x2 = (SLNPAllUserDataInterface) stub;

        Request_SLNPAllUserData req2 = new Request_SLNPAllUserData();
        req2.setSessionID(this.sessionID);
        req2.setBorrowerNumber(patronid);

        try {
            Response_SLNPAllUserData res2 = x2.executeSLNPAllUserData(req2);

            patron.setName(res2.getFullName());

            if (res2.getEmail1() != null) {
                patron.setEmail(new InternetAddress(res2.getEmail1()));
            }

            patron.setExpires(res2.getCardExpiry());

            patron.setUsergroup(res2.getUserGroup());

            if (res2.getDateOfAnnualFee() != null && !res2.getDateOfAnnualFee().equals("")) {

                if (patron.getUsergroup().equals("40") || patron.getUsergroup().equals("45")) {
                    patron.setExpires(res2.getDateOfAnnualFee());
                }
            }

            patron.setFaculty(res2.getFaculty());

            patron.setCity(res2.getCity1());
            patron.setPostalcode(res2.getPostalCode1());
            patron.setAddresssupplement(res2.getAddressSupplement1());
            patron.setStreet(res2.getStreet1());

            String blockingreason = this.getBlockingReason(patronid);

            if (blockingreason == null) {

                patron.setStatus(0);
            }
            else {
                switch (blockingreason) {

                    case "UserBlocked" : {
                        patron.setStatus(1);
                        break;
                    }
                    case "UserDeleteBlock" : {
                        patron.setStatus(2);
                        break;
                    }
                    case "UserCardExpired" : {
                        patron.setStatus(2);
                        break;
                    }
                    case "UserReachedFeeLimit" : {
                        patron.setStatus(3);
                        break;
                    }
                }
            }

            // erweiterte Kontoinformationen
            if (extended) {

                patron.setGender(res2.getGender());
                patron.setDateofbirth(res2.getDateOfBirth());
                patron.setExternalid(res2.getExternalNumber());
            }

        } catch (org.oclcpica.xslnp.client.SLNPAllUserData.InvalidSessionException_Exception e) {
            e.printStackTrace();
            throw new ILSException();
        } catch (org.oclcpica.xslnp.client.SLNPAllUserData.SLNPException_Exception e) {
            e.printStackTrace();
            throw new ILSException();
        } catch (AddressException e) {
            e.printStackTrace();
            throw new ILSException();
        } catch (RemoteException e) {
            e.printStackTrace();
            throw new ILSException();
        }
        this.logger.debug(patron.toString());

        if (this.sessionID != null) {

            try {
                closeSession();
            } catch (RemoteException e) {
                throw new ILSException(e.getMessage(), e.getCause());
            } catch (org.oclcpica.xslnp.client.SLNPWSLogout.InvalidSessionException_Exception e) {
                throw new ILSException(e.getMessage(), e.getCause());
            } catch (org.oclcpica.xslnp.client.SLNPWSLogout.SLNPException_Exception e) {
                throw new ILSException(e.getMessage(), e.getCause());
            }
        }

        return patron;
    }

    @Override
    public DocumentList items(String patronid, String type) throws ILSException {

        this.logger.debug("patronid = " + patronid);
        String borrowerNumber = this.getBorrowerNumber(patronid);
        if (borrowerNumber != null) {
            patronid = borrowerNumber;
        }
        this.logger.debug("patronid = " + patronid);

        Stub stub = createSLNPUserAccountProxy();

        stub._setProperty(javax.xml.rpc.Stub.ENDPOINT_ADDRESS_PROPERTY, this.config.getProperty("ils.xslnp.service.url") + "SLNPUserAccount");
        SLNPUserAccountInterface x2 = (SLNPUserAccountInterface) stub;

        Request_SLNPUserAccount req2 = new Request_SLNPUserAccount();
        req2.setSessionID(this.sessionID);
        req2.setBorrowerNumber(patronid);
        req2.setAccountName("ALL");
        req2.setBranchLibrary("0");

        DocumentList documentList = new DocumentList();
        documentList.setDoc(new ArrayList<Document>());

        try {
            Response_SLNPUserAccount res2 = x2.executeSLNPUserAccount(req2);

            Response_SpecificAccountData[] res3 = res2.getResponse_SpecificAccountData();

            if (res3.length > 0) {
                for (int i = 0; i < res3.length; i++) {

                    if (res3[i].getAccountName().equals("BORROWEDITEMS") && (type.equals("all") || type.equals("borrowed"))) {

                        Response_MediaData[] res4 = res3[i].getResponse_MediaData();

                        if (res4.length > 0) {
                            for (int j = 0; j < res4.length; j++) {

                                Document bitem = new Document();
                                bitem.setStatus(3);
                                bitem.setItem("http://www.ub.tu-dortmund.de/katalog/exemplar/" + res4[j].getMediaNumber());
                                bitem.setEdition("http://www.ub.tu-dortmund.de/katalog/titel/" + res4[j].getCatKey());
                                bitem.setAbout(res4[j].getAuthor() + ": " + res4[j].getTitle());
                                bitem.setLabel(res4[j].getLocationMark());
                                bitem.setQueue(Integer.parseInt(res4[j].getReservedItemsCount()));
                                bitem.setStarttime(res4[j].getDate());
                                bitem.setEndtime(res4[j].getReturnDate());
                                bitem.setDuedate(res4[j].getReturnDate());
                                if (Integer.parseInt(res4[j].getRenewalMark()) == 1 || Integer.parseInt(res4[j].getRenewalMark()) == 4) {
                                    bitem.setCanrenew(true);
                                }
                                else {
                                    bitem.setCanrenew(false);
                                }
                                bitem.setRenewals(Integer.parseInt(res4[j].getNumberOfRenewals()));
                                bitem.setReminder(Integer.parseInt(res4[j].getReminderLevel()));
                                bitem.setStorage(res4[j].getLendingBranchLibraryText());
                                bitem.setStorage_id("http://data.ub.tu-dortmund.de/open/resource/library/290/" + res4[j].getLendingBranchLibrary());

                                if (res4[j].getRecallMark().equals("Y") || Integer.parseInt(res4[j].getReservedItemsCount()) > 0) {

                                    bitem.setRecalled(true);
                                }
                                else {

                                    bitem.setRecalled(false);
                                }

                                String bgr = this.patron(patronid, false).getUsergroup();

                                if (res4[j].getTypeOfMedia().equals("02") || res4[j].getTypeOfMedia().equals("04") || res4[j].getTypeOfMedia().equals("05") ||
                                        res4[j].getTypeOfMedia().equals("10") || res4[j].getTypeOfMedia().equals("11") || res4[j].getTypeOfMedia().equals("31") ||
                                        res4[j].getTypeOfMedia().equals("32") || res4[j].getTypeOfMedia().equals("33") || res4[j].getTypeOfMedia().equals("34") ||
                                        res4[j].getTypeOfMedia().equals("35") || res4[j].getTypeOfMedia().equals("36") || res4[j].getTypeOfMedia().equals("38") ||
                                        res4[j].getTypeOfMedia().equals("39") || res4[j].getTypeOfMedia().equals("40") || res4[j].getTypeOfMedia().equals("42") ||
                                        res4[j].getTypeOfMedia().equals("51") || res4[j].getTypeOfMedia().equals("55") || res4[j].getTypeOfMedia().equals("56") ||
                                        res4[j].getTypeOfMedia().equals("57") ||
                                        bgr.equals("80") || bgr.equals("95") ) {

                                    bitem.setRenewable(false);
                                }
                                else {

                                    bitem.setRenewable(true);
                                }

                                documentList.getDoc().add(bitem);

                            }
                        }
                    }
                    if (res3[i].getAccountName().equals("ORDERS") && (type.equals("all") || type.equals("ordered"))) {

                        Response_MediaData[] res4 = res3[i].getResponse_MediaData();

                        if (res4.length > 0) {

                            for (int j = 0; j < res4.length; j++) {

                                Document oitem = new Document();

                                oitem.setItem("http://www.ub.tu-dortmund.de/katalog/exemplar/" + res4[j].getMediaNumber());
                                oitem.setEdition("http://www.ub.tu-dortmund.de/katalog/titel/" + res4[j].getCatKey());
                                oitem.setAbout(res4[j].getAuthor() + ": " + res4[j].getTitle());
                                oitem.setLabel(res4[j].getLocationMark());

                                switch (Integer.parseInt(res4[j].getOrderStatus())) {

                                    case 0 : {

                                        // no status
                                        oitem.setStatus(0);
                                        break;
                                    }
                                    case 1 : {

                                        // Ordered media on the way - bestellt
                                        oitem.setStatus(2);
                                        oitem.setStarttime(res4[j].getDate());
                                        oitem.setEndtime(res4[j].getReturnDate());
                                        oitem.setDuedate(res4[j].getDate());
                                        break;
                                    }
                                    case 2 : {

                                        // ordered media ready to be collected - abholbar
                                        oitem.setStatus(4);
                                        oitem.setStarttime(res4[j].getDate());
                                        oitem.setEndtime(res4[j].getReturnDate());
                                        oitem.setDuedate(res4[j].getDate());
                                        break;
                                    }
                                    case 3 : {

                                        //  ILL-order cancelled
                                        oitem.setStatus(5);
                                        oitem.setStarttime(res4[j].getDate());
                                        oitem.setEndtime(res4[j].getReturnDate());
                                        oitem.setDuedate(res4[j].getDate());
                                        break;
                                    }
                                }

                                oitem.setStorage(res4[j].getLendingBranchLibraryText());
                                oitem.setStorage_id("http://data.ub.tu-dortmund.de/open/resource/library/290/" + res4[j].getLendingBranchLibrary());

                                documentList.getDoc().add(oitem);

                            }
                        }
                    }
                    if (res3[i].getAccountName().equals("RESERVATIONS") && (type.equals("all") || type.equals("reserved"))) {

                        Response_MediaData[] res4 = res3[i].getResponse_MediaData();

                        if (res4.length > 0) {

                            for (int j = 0; j < res4.length; j++) {

                                Document ritem = new Document();
                                ritem.setStatus(1);
                                ritem.setItem("http://www.ub.tu-dortmund.de/katalog/exemplar/" + res4[j].getMediaNumber());
                                ritem.setEdition("http://www.ub.tu-dortmund.de/katalog/titel/" + res4[j].getCatKey());
                                ritem.setAbout(res4[j].getAuthor() + ": " + res4[j].getTitle());
                                ritem.setLabel(res4[j].getLocationMark());
                                ritem.setStarttime(res4[j].getDate());
                                ritem.setEndtime(res4[j].getReturnDate());
                                ritem.setDuedate(res4[j].getReservationExpiryDate());
                                ritem.setStorage(res4[j].getLendingBranchLibraryText());
                                ritem.setStorage_id("http://data.ub.tu-dortmund.de/open/resource/library/290/" + res4[j].getLendingBranchLibrary());
                                ritem.setCancancel(true);

                                documentList.getDoc().add(ritem);

                            }
                        }
                    }
                }
            }

        } catch (org.oclcpica.xslnp.client.SLNPUserAccount.InvalidSessionException_Exception e) {
            e.printStackTrace();
            throw new ILSException();
        } catch (org.oclcpica.xslnp.client.SLNPUserAccount.SLNPException_Exception e) {
            if (!e.getMessage().contains("contains no data")) {
                e.printStackTrace();
                throw new ILSException();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            throw new ILSException();
        }

        if (this.sessionID != null) {

            try {
                closeSession();
            } catch (RemoteException e) {
                throw new ILSException(e.getMessage(), e.getCause());
            } catch (org.oclcpica.xslnp.client.SLNPWSLogout.InvalidSessionException_Exception e) {
                throw new ILSException(e.getMessage(), e.getCause());
            } catch (org.oclcpica.xslnp.client.SLNPWSLogout.SLNPException_Exception e) {
                throw new ILSException(e.getMessage(), e.getCause());
            }
        }

        return documentList;
    }

    @Override
    public FeeList fees(String patronid) throws ILSException {

        this.logger.debug("patronid = " + patronid);
        String borrowerNumber = this.getBorrowerNumber(patronid);
        if (borrowerNumber != null) {
            patronid = borrowerNumber;
        }
        this.logger.debug("patronid = " + patronid);

        Stub stub = createSLNPUserAccountProxy();

        stub._setProperty(javax.xml.rpc.Stub.ENDPOINT_ADDRESS_PROPERTY, this.config.getProperty("ils.xslnp.service.url") + "SLNPUserAccount");
        SLNPUserAccountInterface x2 = (SLNPUserAccountInterface) stub;

        Request_SLNPUserAccount req2 = new Request_SLNPUserAccount();
        req2.setSessionID(this.sessionID);
        req2.setBorrowerNumber(patronid);
        req2.setAccountName("ALL");
        req2.setBranchLibrary("0");

        FeeList fee = new FeeList();
        double amount = 0.0;

        try {
            Response_SLNPUserAccount res2 = x2.executeSLNPUserAccount(req2);

            Response_SpecificAccountData[] res3 = res2.getResponse_SpecificAccountData();

            if (res3.length > 0) {
                for (int i = 0; i < res3.length; i++) {

                    if (res3[i].getAccountName().equals("FEES")) {

                        Response_MediaData[] res4 = res3[i].getResponse_MediaData();

                        if (res4.length > 0) {
                            for (int j = 0; j < res4.length; j++) {

                                Fee f = new Fee();
                                f.setAmount(res4[j].getFee().replaceAll(",",".") + " EUR");
                                amount += Double.parseDouble(res4[j].getFee().replaceAll(",","."));

                                f.setDate(res4[j].getDate());

                                try {
                                    if (res4[j].getMediaNumber().equals("Unicard-Ersatzgebühr")) {

                                        f.setItem("http://www.ub.tu-dortmund.de/fees/" + res4[j].getMediaNumber());
                                        f.setEdition("http://www.ub.tu-dortmund.de/fees/" + res4[j].getMediaNumber());
                                        f.setFeetype(res4[j].getMediaNumber());
                                        f.setFeeid(res4[j].getTypeOfFee() + " / 4");
                                        f.setAbout("Unicard-Ersatzgebühr");
                                    } else if (res4[j].getMediaNumber().equals("ERSATZAUSWEISGEBÜHR")) {

                                        f.setItem("http://www.ub.tu-dortmund.de/fees/" + res4[j].getMediaNumber());
                                        f.setEdition("http://www.ub.tu-dortmund.de/fees/" + res4[j].getMediaNumber());
                                        f.setFeetype(res4[j].getMediaNumber());
                                        f.setFeeid(res4[j].getTypeOfFee());
                                        f.setAbout("ERSATZAUSWEISGEBÜHR");
                                    } else if (res4[j].getMediaNumber().startsWith("FERNLEIHGEBÜHR")) {

                                        f.setItem("http://www.ub.tu-dortmund.de/katalog/exemplar/" + URLEncoder.encode("@", "UTF-8") + res4[j].getMediaNumber().split("@")[1]);
                                        f.setEdition("http://www.ub.tu-dortmund.de/katalog/titel/" + res4[j].getCatKey());
                                        f.setFeeid(res4[j].getTypeOfFee()); // TODO URIs
                                        f.setAbout("FERNLEIH-Gebühr: " + res4[j].getTitle() + " / " + res4[j].getAuthor() + ". " + res4[j].getLocationMark());
                                    } else if (res4[j].getMediaNumber().startsWith("Fernleihe")) {

                                        f.setItem("http://www.ub.tu-dortmund.de/katalog/exemplar/" + res4[j].getMediaNumber().split("TAN: ")[1]);
                                        f.setEdition("http://www.ub.tu-dortmund.de/katalog/titel/" + res4[j].getCatKey());
                                        f.setFeeid(res4[j].getTypeOfFee()); // TODO URIs
                                        f.setAbout("FERNLEIH-Gebühr: " + res4[j].getMediaNumber().split(", ")[1]);
                                    } else if (res4[j].getText() != null && res4[j].getText().startsWith("SM-Gebühr")) {

                                        f.setItem("http://www.ub.tu-dortmund.de/katalog/exemplar/" + res4[j].getMediaNumber());
                                        f.setEdition("http://www.ub.tu-dortmund.de/katalog/titel/" + res4[j].getCatKey());
                                        f.setFeeid(res4[j].getTypeOfFee()); // TODO URIs
                                        String title = "k.A.";
                                        if (res4[j].getTitle() != null) {

                                            title = res4[j].getTitle();
                                        }
                                        String author = "k.A.";
                                        if (res4[j].getAuthor() != null) {

                                            author = res4[j].getAuthor();
                                        }
                                        String location = "k.A.";
                                        if (res4[j].getLocationMark() != null) {

                                            location = res4[j].getLocationMark();
                                        }
                                        f.setAbout("Säumnisgebühr: " + title + " / " + author + ". " + location);
                                    } else {

                                        f.setItem("http://www.ub.tu-dortmund.de/katalog/exemplar/" + res4[j].getMediaNumber());
                                        f.setEdition("http://www.ub.tu-dortmund.de/katalog/titel/" + res4[j].getCatKey());
                                        f.setFeeid(res4[j].getTypeOfFee()); // TODO URIs
                                        String reason = res4[j].getText();

                                        switch (Integer.parseInt(res4[j].getTypeOfFee())) {

                                            case 64: {

                                                reason = "Jahresentgeld";
                                            }
                                            default: {
                                                if (res4[j].getTitle() != null) {

                                                    reason += ": " + res4[j].getTitle();
                                                }
                                            }
                                        }
                                        f.setAbout(reason);
                                    }
                                }
                                catch (NullPointerException e) {
                                    e.printStackTrace();
                                }

                                if (fee.getFee() == null) {
                                    fee.setFee(new ArrayList<Fee>());
                                }
                                fee.getFee().add(f);
                            }
                        }
                    }
                }
            }

        } catch (org.oclcpica.xslnp.client.SLNPUserAccount.InvalidSessionException_Exception e) {
            e.printStackTrace();
            throw new ILSException();
        } catch (org.oclcpica.xslnp.client.SLNPUserAccount.SLNPException_Exception e) {

            if (!e.getMessage().contains("contains no data")) {
                e.printStackTrace();
                throw new ILSException();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            throw new ILSException();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        if (this.sessionID != null) {

            try {
                closeSession();
            } catch (RemoteException e) {
                throw new ILSException(e.getMessage(), e.getCause());
            } catch (org.oclcpica.xslnp.client.SLNPWSLogout.InvalidSessionException_Exception e) {
                throw new ILSException(e.getMessage(), e.getCause());
            } catch (org.oclcpica.xslnp.client.SLNPWSLogout.SLNPException_Exception e) {
                throw new ILSException(e.getMessage(), e.getCause());
            }
        }

        fee.setAmount(Double.toString(amount) + "0 EUR");

        return fee;
    }

    @Override
    public DocumentList request(String patronid, DocumentList documentList) throws ILSException {

        this.logger.debug("patronid = " + patronid);
        String borrowerNumber = this.getBorrowerNumber(patronid);
        if (borrowerNumber != null) {
            patronid = borrowerNumber;
        }
        this.logger.debug("patronid = " + patronid);

        DocumentList responseList = new DocumentList();
        this.logger.debug("documentList.getDoc().size() = " + documentList.getDoc().size());

        if (documentList.getDoc().size() != 1) {
            // TODO not supported!
        }
        else {

            Document document = documentList.getDoc().get(0);

            if (this.isBlocked(patronid)) {

                document.setError("Patron is blocked because of user is locked, his card is expired, he cannot be deleted or has reached the fee limit");

                if (responseList.getDoc() == null) {
                    responseList.setDoc(new ArrayList<Document>());
                }
                responseList.getDoc().add(document);
            }
            else {

                Stub stub = createSLNPReservationProxy();

                stub._setProperty(javax.xml.rpc.Stub.ENDPOINT_ADDRESS_PROPERTY, this.config.getProperty("ils.xslnp.service.url") + "SLNPReservation");
                SLNPReservationInterface x2 = (SLNPReservationInterface) stub;

                Request_SLNPReservation req2 = new Request_SLNPReservation();
                req2.setSessionID(this.sessionID);
                req2.setClientType("OPC");
                req2.setBorrowerNumber(patronid);

                String edition = "";
                if (document.getEdition() != null) {

                    if (document.getEdition().contains("://")) {
                        edition = document.getEdition().split("/")[document.getEdition().split("/").length - 1];
                    } else {
                        edition = document.getEdition();
                    }
                }

                // ermitteln des Katkey
                try {
                    this.logger.info("edition = '" + edition + "'");
                    Response_SLNPOpsSearch responseSlnpOpsSearch = this.opsSearch("0010", edition);

                    int hits = Integer.parseInt(responseSlnpOpsSearch.getNumberHits());
                    this.logger.info("Treffer = '" + hits + "'");

                    if (responseSlnpOpsSearch == null || hits == 0) {

                        // keine Titel-Treffer => leere ArrayList
                        document.setError("NoSuchMediaAvailable");

                        if (responseList.getDoc() == null) {
                            responseList.setDoc(new ArrayList<Document>());
                        }
                        responseList.getDoc().add(document);
                    }
                    else {

                        try {
                            Response_HitData responseHitData = this.executeSLNPOpsHitListPresent(responseSlnpOpsSearch.getNameHitList(), new Integer("1").toString(), "1");

                            if (responseHitData != null) {

                                req2.setCatKey(responseHitData.getCatKey());

                                if (document.getItem() != null && !document.getItem().equals("")) {
                                    String item = "";
                                    if (document.getItem().contains("://")) {
                                        item = document.getItem().split("/")[document.getItem().split("/").length-1];
                                    }
                                    else {
                                        item = document.getItem();
                                    }
                                    req2.setTypeOfReservation("FULL");
                                    req2.setMediaNumber(item);
                                }
                                else {
                                    req2.setTypeOfReservation("PART");
                                }

                                req2.setBranchLibrary("0");

                                try {
                                    Response_SLNPReservation res2 = x2.executeSLNPReservation(req2);

                                    this.logger.debug("SLNPReservation - " + res2.getExitCode() + ", " + res2.getErrorCode());

                                    switch(res2.getErrorCode()) {

                                        case "SlnpNoError": {

                                            break;
                                        }
                                        case "MultipleCopyNotAllowedLoanOrder": {

                                            document.setError("MultipleCopyNotAllowedLoanOrder");

                                            break;
                                        }
                                        case "MultipleCopyNotAllowedReservation": {

                                            document.setError("MultipleCopyNotAllowedReservation");

                                            break;
                                        }
                                        case "MediaLoanable": {

                                            document.setError("MediaLoanable");

                                            break;
                                        }
                                        default: {
                                            this.logger.debug("Bla");
                                            throw new ILSException();
                                        }
                                    }

                                    if (responseList.getDoc() == null) {
                                        responseList.setDoc(new ArrayList<Document>());
                                    }
                                    responseList.getDoc().add(document);

                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                    throw new ILSException();
                                } catch (org.oclcpica.xslnp.client.SLNPReservation.SLNPException_Exception e) {
                                    e.printStackTrace();
                                    throw new ILSException();
                                } catch (org.oclcpica.xslnp.client.SLNPReservation.InvalidSessionException_Exception e) {
                                    e.printStackTrace();
                                    throw new ILSException();
                                }
                            }
                            else {
                                // TODO ERROR: Titelsuche ohne Exception == null
                            }
                        }
                        catch (org.oclcpica.xslnp.client.SLNPOpsHitListPresent.InvalidSessionException_Exception e) {
                            throw new ILSException(e.getMessage(), e.getCause());
                        } catch (InvalidSessionException_Exception e) {
                            throw new ILSException(e.getMessage(), e.getCause());
                        } catch (org.oclcpica.xslnp.client.SLNPWSLogin.SLNPException_Exception e) {
                            throw new ILSException(e.getMessage(), e.getCause());
                        } catch (org.oclcpica.xslnp.client.SLNPOpsHitListPresent.SLNPException_Exception e) {
                            throw new ILSException(e.getMessage(), e.getCause());
                        } catch (SLNPException_Exception e) {
                            throw new ILSException(e.getMessage(), e.getCause());
                        } catch (org.oclcpica.xslnp.client.SLNPWSLogin.InvalidSessionException_Exception e) {
                            throw new ILSException(e.getMessage(), e.getCause());
                        }
                    }
                } catch (RemoteException e) {
                    throw new ILSException(e.getMessage(), e.getCause());
                } catch (org.oclcpica.xslnp.client.SLNPOpsSearch.SLNPException_Exception e) {
                    throw new ILSException(e.getMessage(), e.getCause());
                } catch (org.oclcpica.xslnp.client.SLNPOpsSearch.InvalidSessionException_Exception e) {
                    throw new ILSException(e.getMessage(), e.getCause());
                }
            }
        }

        if (this.sessionID != null) {

            try {
                closeSession();
            } catch (RemoteException e) {
                throw new ILSException(e.getMessage(), e.getCause());
            } catch (org.oclcpica.xslnp.client.SLNPWSLogout.InvalidSessionException_Exception e) {
                throw new ILSException(e.getMessage(), e.getCause());
            } catch (org.oclcpica.xslnp.client.SLNPWSLogout.SLNPException_Exception e) {
                throw new ILSException(e.getMessage(), e.getCause());
            }
        }

        return responseList;
    }

    @Override
    public DocumentList renew(String patronid, DocumentList documentList) throws ILSException {

        this.logger.debug("patronid = " + patronid);
        String borrowerNumber = this.getBorrowerNumber(patronid);
        if (borrowerNumber != null) {
            patronid = borrowerNumber;
        }
        this.logger.debug("patronid = " + patronid);

        DocumentList responseList = new DocumentList();

        if (documentList.getDoc().size() != 1) {
            // TODO not supported!
        }
        else {

            Document document = documentList.getDoc().get(0);

            if(this.isBlocked(patronid)) {

                document.setError("Patron is blocked because of user is locked, his card is expired, he cannot be deleted or has reached the fee limit");

                if (responseList.getDoc() == null) {
                    responseList.setDoc(new ArrayList<Document>());
                }
                responseList.getDoc().add(document);
            }
            else {

                Stub stub = createSLNPSingleRenewalProxy();

                stub._setProperty(javax.xml.rpc.Stub.ENDPOINT_ADDRESS_PROPERTY, this.config.getProperty("ils.xslnp.service.url") + "SLNPSingleRenewal");
                SLNPSingleRenewalInterface slnpSingleRenewalInterface = (SLNPSingleRenewalInterface) stub;

                Request_SLNPSingleRenewal requestSlnpSingleRenewal = new Request_SLNPSingleRenewal();
                requestSlnpSingleRenewal.setSessionID(this.sessionID);
                requestSlnpSingleRenewal.setClientType("OPC");
                requestSlnpSingleRenewal.setBorrowerNumber(patronid);

                String item = "";
                if (document.getItem().contains("://")) {
                    item = document.getItem().split("/")[document.getItem().split("/").length-1];
                }
                else {
                    item = document.getItem();
                }

                requestSlnpSingleRenewal.setMediaNumber(item);

                try {
                    Response_SLNPSingleRenewal responseSlnpSingleRenewal = slnpSingleRenewalInterface.executeSLNPSingleRenewal(requestSlnpSingleRenewal);

                    this.logger.debug("SLNPSingleRenewal - " + responseSlnpSingleRenewal.getExitCode() + ", " + responseSlnpSingleRenewal.getErrorCode());

                    Response_RenewalData responseRenewalData = responseSlnpSingleRenewal.getResponse_RenewalData()[0];

                    document.setRenewals(Integer.parseInt(responseRenewalData.getNumberOfRenewals()));
                    document.setDuedate(responseRenewalData.getReturnDateNew());

                    switch(responseSlnpSingleRenewal.getErrorCode()) {

                        case "SlnpNoError": {

                            break;
                        }
                        case "MediaNotRenewable": {

                            document.setError("MediaNotRenewable");

                            break;
                        }
                        case "MediaRecalled": {

                            document.setError("MediaRecalled");

                            break;
                        }
                        case "FeeLimitReached": {

                            document.setError("FeeLimitReached");

                            break;
                        }
                        case "MaximumRenewalsReached": {

                            document.setError("MaximumRenewalsReached");

                            break;
                        }
                        default: {
                            this.logger.debug(responseSlnpSingleRenewal.getErrorCode());
                            document.setError(responseSlnpSingleRenewal.getErrorCode());
                        }
                    }

                    if (responseList.getDoc() == null) {
                        responseList.setDoc(new ArrayList<Document>());
                    }
                    responseList.getDoc().add(document);

                } catch (RemoteException e) {
                    e.printStackTrace();
                } catch (org.oclcpica.xslnp.client.SLNPSingleRenewal.SLNPException_Exception e) {
                    e.printStackTrace();
                } catch (org.oclcpica.xslnp.client.SLNPSingleRenewal.InvalidSessionException_Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (this.sessionID != null) {

            try {
                closeSession();
            } catch (RemoteException e) {
                throw new ILSException(e.getMessage(), e.getCause());
            } catch (org.oclcpica.xslnp.client.SLNPWSLogout.InvalidSessionException_Exception e) {
                throw new ILSException(e.getMessage(), e.getCause());
            } catch (org.oclcpica.xslnp.client.SLNPWSLogout.SLNPException_Exception e) {
                throw new ILSException(e.getMessage(), e.getCause());
            }
        }

        return responseList;
    }

    @Override
    public DocumentList cancel(String patronid, DocumentList documentList) throws ILSException {

        this.logger.debug("patronid = " + patronid);
        String borrowerNumber = this.getBorrowerNumber(patronid);
        if (borrowerNumber != null) {
            patronid = borrowerNumber;
        }
        this.logger.debug("patronid = " + patronid);

        DocumentList responseList = new DocumentList();

        if (documentList.getDoc().size() != 1) {
            // TODO not supported!
        }
        else {

            Document document = documentList.getDoc().get(0);

            if(this.isBlocked(patronid)) {

                document.setError("Patron is blocked because of user is locked, his card is expired, he cannot be deleted or has reached the fee limit");

                if (responseList.getDoc() == null) {
                    responseList.setDoc(new ArrayList<Document>());
                }
                responseList.getDoc().add(document);
            }
            else {

                // erst teilqualifiziert, dann - falls Fehler - vollqualifiziert
                Stub stub = createSLNPCancelOrderReservationProxy();

                stub._setProperty(javax.xml.rpc.Stub.ENDPOINT_ADDRESS_PROPERTY, this.config.getProperty("ils.xslnp.service.url") + "SLNPCancelOrderReservation");
                SLNPCancelOrderReservationInterface slnpCancelOrderReservationInterface = (SLNPCancelOrderReservationInterface) stub;

                Request_SLNPCancelOrderReservation requestSlnpCancelOrderReservation = new Request_SLNPCancelOrderReservation();
                requestSlnpCancelOrderReservation.setSessionID(this.sessionID);
                requestSlnpCancelOrderReservation.setBorrowerNumber(patronid);

                String edition = "";
                if (document.getEdition().contains("://")) {
                    edition = document.getEdition().split("/")[document.getEdition().split("/").length-1];
                }
                else {
                    edition = document.getEdition();
                }
                this.logger.debug("SLNPCancelOrderReservation - edition: " + edition);
                requestSlnpCancelOrderReservation.setCatKey(edition);

                requestSlnpCancelOrderReservation.setBranchLibrary("0");

                try {
                    Response_SLNPCancelOrderReservation responseSlnpCancelOrderReservation = slnpCancelOrderReservationInterface.executeSLNPCancelOrderReservation(requestSlnpCancelOrderReservation);

                    this.logger.debug("SLNPCancelOrderReservation - " + responseSlnpCancelOrderReservation.getExitCode() + ", " + responseSlnpCancelOrderReservation.getErrorCode());

                    switch(responseSlnpCancelOrderReservation.getErrorCode()) {

                        case "SlnpNoError": {

                            break;
                        }
                        default: {
                            this.logger.debug(responseSlnpCancelOrderReservation.getErrorCode());
                            document.setError(responseSlnpCancelOrderReservation.getErrorCode());
                        }
                    }

                    if (responseList.getDoc() == null) {
                        responseList.setDoc(new ArrayList<Document>());
                    }
                    responseList.getDoc().add(document);

                } catch (RemoteException e) {
                    e.printStackTrace();
                    throw new ILSException();
                } catch (org.oclcpica.xslnp.client.SLNPCancelOrderReservation.SLNPException_Exception e) {

                    this.logger.debug("SLNPCancelOrderReservation - " + e.getMessage()); //
                    this.logger.debug("SLNPCancelOrderReservation - " + e.getErrorCode()); // 510
                    this.logger.debug("SLNPCancelOrderReservation - " + e.getErrorType()); // NoCancellationPossible

                    // weiterer Versuch als "vollqualifiziert"
                    if (e.getErrorType().equals("NoCancellationPossible")) {

                        requestSlnpCancelOrderReservation.setCatKey(null);
                        String item = "";
                        if (document.getItem().contains("://")) {
                            item = document.getItem().split("/")[document.getItem().split("/").length-1];
                        }
                        else {
                            item = document.getItem();
                        }
                        this.logger.debug("SLNPCancelOrderReservation - item: " + item);
                        requestSlnpCancelOrderReservation.setMediaNumber(item);

                        try {
                            Response_SLNPCancelOrderReservation responseSlnpCancelOrderReservation = slnpCancelOrderReservationInterface.executeSLNPCancelOrderReservation(requestSlnpCancelOrderReservation);

                            this.logger.debug("SLNPCancelOrderReservation - " + responseSlnpCancelOrderReservation.getExitCode() + ", " + responseSlnpCancelOrderReservation.getErrorCode());

                            switch(responseSlnpCancelOrderReservation.getErrorCode()) {

                                case "SlnpNoError": {

                                    break;
                                }
                                default: {
                                    this.logger.debug(responseSlnpCancelOrderReservation.getErrorCode());
                                    document.setError(responseSlnpCancelOrderReservation.getErrorCode());
                                }
                            }

                            if (responseList.getDoc() == null) {
                                responseList.setDoc(new ArrayList<Document>());
                            }
                            responseList.getDoc().add(document);

                        } catch (RemoteException e1) {
                            e.printStackTrace();
                            throw new ILSException();
                        } catch (org.oclcpica.xslnp.client.SLNPCancelOrderReservation.SLNPException_Exception e1) {

                            this.logger.debug("SLNPCancelOrderReservation - " + e.getMessage()); //
                            this.logger.debug("SLNPCancelOrderReservation - " + e.getErrorCode()); // 510
                            this.logger.debug("SLNPCancelOrderReservation - " + e.getErrorType()); // NoCancellationPossible

                            throw new ILSException();

                        } catch (org.oclcpica.xslnp.client.SLNPCancelOrderReservation.InvalidSessionException_Exception e1) {
                            e.printStackTrace();
                            throw new ILSException();
                        }

                    }
                    else {
                        throw new ILSException();
                    }
                } catch (org.oclcpica.xslnp.client.SLNPCancelOrderReservation.InvalidSessionException_Exception e) {
                    e.printStackTrace();
                    throw new ILSException();
                }
            }
        }

        if (this.sessionID != null) {

            try {
                closeSession();
            } catch (RemoteException e) {
                throw new ILSException(e.getMessage(), e.getCause());
            } catch (org.oclcpica.xslnp.client.SLNPWSLogout.InvalidSessionException_Exception e) {
                throw new ILSException(e.getMessage(), e.getCause());
            } catch (org.oclcpica.xslnp.client.SLNPWSLogout.SLNPException_Exception e) {
                throw new ILSException(e.getMessage(), e.getCause());
            }
        }

        return responseList;
    }

    @Override
    public Patron change(String patronid, de.tu_dortmund.ub.api.paia.core.model.Patron patron) throws ILSException {

        this.logger.debug("patronid = " + patronid);
        String borrowerNumber = this.getBorrowerNumber(patronid);
        if (borrowerNumber != null) {
            patronid = borrowerNumber;
        }
        this.logger.debug("patronid = " + patronid);

        // TODO change via XSLNP

        return null;
    }

    private String getBorrowerNumber(String id) throws ILSException {

        if (this.sessionID == null) {

            try {

                this.fetchSessionID();

            } catch (RemoteException e) {
                e.printStackTrace();
                throw new ILSException();
            } catch (org.oclcpica.xslnp.client.SLNPWSLogin.InvalidSessionException_Exception e) {
                e.printStackTrace();
                throw new ILSException();
            } catch (org.oclcpica.xslnp.client.SLNPWSLogin.SLNPException_Exception e) {
                e.printStackTrace();
                throw new ILSException();
            } catch (SLNPException_Exception e) {
                e.printStackTrace();
            } catch (InvalidSessionException_Exception e) {
                e.printStackTrace();
            }
        }

        String borrowerNumber = "";

        Stub stub = createSLNPUserAccountShortProxy();

        stub._setProperty(javax.xml.rpc.Stub.ENDPOINT_ADDRESS_PROPERTY, this.config.getProperty("ils.xslnp.service.url") + "SLNPUserAccountShort");
        SLNPUserAccountShortInterface x2 = (SLNPUserAccountShortInterface) stub;

        Request_SLNPUserAccountShort req2 = new Request_SLNPUserAccountShort();
        req2.setSessionID(this.sessionID);
        req2.setBorrowerNumber(id);

        try {
            Response_SLNPUserAccountShort res2 = x2.executeSLNPUserAccountShort(req2);

            borrowerNumber = res2.getBorrowerNumber();

        } catch (org.oclcpica.xslnp.client.SLNPUserAccountShort.InvalidSessionException_Exception e) {
            e.printStackTrace();
            throw new ILSException();
        } catch (org.oclcpica.xslnp.client.SLNPUserAccountShort.SLNPException_Exception e) {

            if (e.getMessage().contains("No user with this account existing")) {
                throw new ILSException("570-unknown patron");
            }
            else {
                throw new ILSException();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            throw new ILSException();
        }

        return borrowerNumber;
    }

    public boolean isBlocked(String id) throws ILSException {

        boolean isBlocked = true;

        Stub stub = createSLNPUserAccountShortProxy();

        stub._setProperty(javax.xml.rpc.Stub.ENDPOINT_ADDRESS_PROPERTY, this.config.getProperty("ils.xslnp.service.url") + "SLNPUserAccountShort");
        SLNPUserAccountShortInterface x2 = (SLNPUserAccountShortInterface) stub;

        Request_SLNPUserAccountShort req2 = new Request_SLNPUserAccountShort();
        req2.setSessionID(this.sessionID);
        req2.setBorrowerNumber(id);

        try {
            Response_SLNPUserAccountShort res2 = x2.executeSLNPUserAccountShort(req2);

            if(res2.getBlock() == null || (res2.getBlock() != null && res2.getBlock().equals(""))) {
                isBlocked = false;
            }

        } catch (org.oclcpica.xslnp.client.SLNPUserAccountShort.InvalidSessionException_Exception e) {
            e.printStackTrace();
            throw new ILSException();
        } catch (org.oclcpica.xslnp.client.SLNPUserAccountShort.SLNPException_Exception e) {
            //e.printStackTrace();
            throw   new ILSException("570-unknown patron");
        } catch (RemoteException e) {
            e.printStackTrace();
            throw new ILSException();
        }

        return isBlocked;
    }

    public String getBlockingReason(String id) throws ILSException {

        String blockingreason = null;

        Stub stub = createSLNPUserAccountShortProxy();

        stub._setProperty(javax.xml.rpc.Stub.ENDPOINT_ADDRESS_PROPERTY, this.config.getProperty("ils.xslnp.service.url") + "SLNPUserAccountShort");
        SLNPUserAccountShortInterface x2 = (SLNPUserAccountShortInterface) stub;

        Request_SLNPUserAccountShort req2 = new Request_SLNPUserAccountShort();
        req2.setSessionID(this.sessionID);
        req2.setBorrowerNumber(id);

        try {
            Response_SLNPUserAccountShort res2 = x2.executeSLNPUserAccountShort(req2);

            if(res2.getBlock() != null && !res2.getBlock().equals("")) {
                blockingreason = res2.getBlock();
            }

        } catch (org.oclcpica.xslnp.client.SLNPUserAccountShort.InvalidSessionException_Exception e) {
            e.printStackTrace();
            throw new ILSException();
        } catch (org.oclcpica.xslnp.client.SLNPUserAccountShort.SLNPException_Exception e) {
            //e.printStackTrace();
            throw   new ILSException("570-unknown patron");
        } catch (RemoteException e) {
            e.printStackTrace();
            throw new ILSException();
        }

        return blockingreason;
    }

    private Response_SLNPOpsSearch opsSearch (String cat, String value)
            throws org.oclcpica.xslnp.client.SLNPOpsSearch.InvalidSessionException_Exception, RemoteException, org.oclcpica.xslnp.client.SLNPOpsSearch.SLNPException_Exception {

        Stub stub = createSLNPOpsSearchProxy();

        stub._setProperty(javax.xml.rpc.Stub.ENDPOINT_ADDRESS_PROPERTY, this.config.getProperty("ils.xslnp.service.url") + "SLNPOpsSearch");
        SLNPOpsSearchInterface x2 = (SLNPOpsSearchInterface) stub;

        Request_SLNPOpsSearch req2 = new Request_SLNPOpsSearch();
        req2.setSessionID(this.sessionID);
        req2.setQuery(cat + "=\"" + value + "\"");

        return x2.executeSLNPOpsSearch(req2);
    }

    private Stub createLoginProxy() {
        return (Stub) (new SLNPWSLogin_Impl().getSLNPWSLoginInterfacePort());
    }

    private Stub createSLNPUserAccountShortProxy() {
        return (Stub) (new SLNPUserAccountShort_Impl().getSLNPUserAccountShortInterfacePort());
    }

    private Stub createSLNPAllUserDataProxy() {
        return (Stub) (new SLNPAllUserData_Impl().getSLNPAllUserDataInterfacePort());
    }

    private Stub createSLNPUserAccountProxy() {
        return (Stub) (new SLNPUserAccount_Impl().getSLNPUserAccountInterfacePort());
    }

    private Stub createSLNPReservationProxy() {
        return (Stub) (new SLNPReservation_Impl().getSLNPReservationInterfacePort());
    }

    private Stub createSLNPCancelOrderReservationProxy() {
        return (Stub) (new SLNPCancelOrderReservation_Impl().getSLNPCancelOrderReservationInterfacePort());
    }

    private Stub createSLNPOpsSearchProxy() {
        return (Stub) (new SLNPOpsSearch_Impl().getSLNPOpsSearchInterfacePort());
    }

    private String fetchSessionID()
            throws RemoteException, InvalidSessionException_Exception, SLNPException_Exception,
            org.oclcpica.xslnp.client.SLNPWSLogin.InvalidSessionException_Exception,
            org.oclcpica.xslnp.client.SLNPWSLogin.SLNPException_Exception {

        Stub stub = createLoginProxy();
        stub._setProperty(javax.xml.rpc.Stub.ENDPOINT_ADDRESS_PROPERTY, this.config.getProperty("ils.xslnp.service.url") + "SLNPWSLogin");
        this.logger.debug(this.config.getProperty("ils.xslnp.service.url") + "SLNPWSLogin");

        SLNPWSLoginInterface x1 = (SLNPWSLoginInterface) stub;
        Request_SLNPWSLogin req1 = new Request_SLNPWSLogin();

        req1.setLogin(this.config.getProperty("ils.xslnp.admin")); // configured in SIADMIN
        req1.setPassword(this.config.getProperty("ils.xslnp.adminpw"));
        req1.setLanguage(this.config.getProperty("ils.xslnp.lang"));

        Response_SLNPWSLogin res1 = x1.executeSLNPWSLogin(req1);

        if (res1.getSessionID() != null) {
            this.logger.info("SessionID for XSLNP: " + res1.getSessionID());
        }
        else {
            this.logger.info("Problems logging in!");
        }

        this.sessionID = res1.getSessionID();

        return sessionID;
    }

    private Stub createLogoutProxy() {
        return (Stub) (new SLNPWSLogout_Impl().getSLNPWSLogoutInterfacePort());
    }

    public void closeSession()
            throws RemoteException,
            org.oclcpica.xslnp.client.SLNPWSLogout.InvalidSessionException_Exception,
            org.oclcpica.xslnp.client.SLNPWSLogout.SLNPException_Exception {

        Stub stub = createLogoutProxy();
        stub._setProperty(javax.xml.rpc.Stub.ENDPOINT_ADDRESS_PROPERTY, this.config.getProperty("ils.xslnp.service.url") + "SLNPWSLogout");

        SLNPWSLogoutInterface x1 = (SLNPWSLogoutInterface) stub;
        Request_SLNPWSLogout req1 = new Request_SLNPWSLogout();

        req1.setSessionID(this.sessionID);

        Response_SLNPWSLogout res1 = x1.executeSLNPWSLogout(req1);

        if (res1.getOKMessage() != null) {
            this.logger.info("Session for XSLNP: " + res1.getOKMessage());
            this.sessionID = null;
        }
    }

    private Response_HitData executeSLNPOpsHitListPresent(String nameHitList, String position, String numberHits)
            throws
            RemoteException,
            org.oclcpica.xslnp.client.SLNPWSLogin.InvalidSessionException_Exception,
            org.oclcpica.xslnp.client.SLNPWSLogin.SLNPException_Exception,
            org.oclcpica.xslnp.client.SLNPOpsHitListPresent.SLNPException_Exception,
            org.oclcpica.xslnp.client.SLNPOpsHitListPresent.InvalidSessionException_Exception, InvalidSessionException_Exception, SLNPException_Exception {

        Stub stub = createSLNPOpsHitListPresentProxy();

        stub._setProperty(javax.xml.rpc.Stub.ENDPOINT_ADDRESS_PROPERTY, this.config.getProperty("ils.xslnp.service.url") + "SLNPOpsHitListPresent");
        SLNPOpsHitListPresentInterface x2 = (SLNPOpsHitListPresentInterface) stub;

        Request_SLNPOpsHitListPresent req2 = new Request_SLNPOpsHitListPresent();
        req2.setSessionID(this.sessionID);
        req2.setNameHitList(nameHitList);
        req2.setNumberHits(numberHits);
        req2.setPosition(position);
        req2.setComposition("Full");

        int wait = 100;

        Response_HitData res = this.getHitData(x2, req2, wait, 0);

        return res;
    }

    private Response_HitData getHitData(SLNPOpsHitListPresentInterface x2, Request_SLNPOpsHitListPresent req2, int wait, int tries) {

        tries++;

        Response_HitData res = null;

        this.logger.info(req2.getNameHitList() + " >> Try  no. " + tries);

        try {

            try {
                Thread.sleep(wait); //1000 milliseconds is one second.
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            Response_SLNPOpsHitListPresent res2 = x2.executeSLNPOpsHitListPresent(req2);

            Response_HitData[] res3 = res2.getResponse_HitData();

            for (int i = 0; i < res3.length; i++) {

                res = res3[i];
            }
        }
        catch (org.oclcpica.xslnp.client.SLNPOpsHitListPresent.SLNPException_Exception e13) {

            if (tries < Integer.parseInt(this.config.getProperty("ils.xslnp.SLNPOpsHitListPresent.tries"))) {

                res = this.getHitData(x2, req2, wait, tries);
            }
            else {
                this.logger.error(e13.getMessage() + " / " + req2.getNameHitList(), e13.getCause());
            }

        } catch (RemoteException e13) {
            this.logger.error(e13.getMessage(), e13.getCause());
        } catch (org.oclcpica.xslnp.client.SLNPOpsHitListPresent.InvalidSessionException_Exception e13) {
            this.logger.error(e13.getMessage(), e13.getCause());
        }

        return res;
    }

    private Stub createSLNPOpsHitListPresentProxy() {
        return (Stub) (new SLNPOpsHitListPresent_Impl().getSLNPOpsHitListPresentInterfacePort());
    }

    private Stub createSLNPSingleRenewalProxy() {
        return (Stub) (new SLNPSingleRenewal_Impl().getSLNPSingleRenewalInterfacePort());
    }


}
