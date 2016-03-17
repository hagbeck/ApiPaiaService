package de.tu_dortmund.ub.api.paia;

import de.tu_dortmund.ub.api.paia.interfaces.AuthorizationInterface;
import de.tu_dortmund.ub.api.paia.interfaces.LibraryManagementSystem;
import de.tu_dortmund.ub.util.impl.Lookup;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.HashMap;
import java.util.Properties;

/**
 * Created by Hans-Georg on 24.07.2015.
 */
public class PaiaPingEndpoint extends HttpServlet {

    private String conffile  = "";
    private Properties config = new Properties();
    private Logger logger = Logger.getLogger(PaiaPingEndpoint.class.getName());

    public PaiaPingEndpoint() throws IOException {

        this("conf/api-test.properties");
    }

    public PaiaPingEndpoint(String conffile) throws IOException {

        this.conffile = conffile;

        // Init properties
        try {
            InputStream inputStream = new FileInputStream(this.conffile);

            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

                try {
                    this.config.load(reader);

                } finally {
                    reader.close();
                }
            }
            finally {
                inputStream.close();
            }
        }
        catch (IOException e) {
            System.out.println("FATAL ERROR: Die Datei '" + this.conffile + "' konnte nicht geöffnet werden!");
        }

        // init logger
        PropertyConfigurator.configure(this.config.getProperty("service.log4j-conf"));

        this.logger.info("Starting 'PaiaPingEndpoint' ...");
        this.logger.info("conf-file = " + this.conffile);
        this.logger.info("log4j-conf-file = " + this.config.getProperty("service.log4j-conf"));
    }

    public void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        response.setHeader("Access-Control-Allow-Methods", config.getProperty("Access-Control-Allow-Methods"));
        response.addHeader("Access-Control-Allow-Headers", config.getProperty("Access-Control-Allow-Headers"));
        response.setHeader("Accept", config.getProperty("Accept"));
        response.setHeader("Access-Control-Allow-Origin", config.getProperty("Access-Control-Allow-Origin"));
        response.setHeader("Cache-Control", config.getProperty("Cache-Control"));

        response.getWriter().println();
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        response.setHeader("Access-Control-Allow-Origin", config.getProperty("Access-Control-Allow-Origin"));
        response.setHeader("Cache-Control", config.getProperty("Cache-Control"));

        try {

            HashMap<String,String> health = null;

            // ILS
            if (Lookup.lookupAll(LibraryManagementSystem.class).size() > 0) {

                LibraryManagementSystem libraryManagementSystem = Lookup.lookup(LibraryManagementSystem.class);
                // init ILS
                libraryManagementSystem.init(this.config);

                health = libraryManagementSystem.health(this.config);
            }
            // OAuth 2.0
            if (Lookup.lookupAll(AuthorizationInterface.class).size() > 0) {

                AuthorizationInterface authorizationInterface = Lookup.lookup(AuthorizationInterface.class);
                // init Authorization Service
                authorizationInterface.init(this.config);

                if (health == null) {

                    health = authorizationInterface.health(this.config);
                }
                else {

                    health.putAll(authorizationInterface.health(this.config));
                }
            }

            if (health.containsValue("failed")) {

                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().println("One or more dependencies unavailable!");
            }
            else {

                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().println("pong");
            }
        }
        catch (Exception e) {

            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().println("Could not check system health!");
        }
    }

}
