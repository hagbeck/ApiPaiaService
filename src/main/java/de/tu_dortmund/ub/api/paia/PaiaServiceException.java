package de.tu_dortmund.ub.api.paia;

/**
 * Created by cihabe on 05.05.2015.
 */
public class PaiaServiceException extends Exception {

    public PaiaServiceException() {
    }

    public PaiaServiceException(String message) {
        super(message);
    }

    public PaiaServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
