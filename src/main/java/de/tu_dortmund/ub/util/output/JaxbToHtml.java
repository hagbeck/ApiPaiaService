/*
The MIT License (MIT)

Copyright (c) 2015, Hans-Georg Becker

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */

package de.tu_dortmund.ub.util.output;

import de.tu_dortmund.ub.api.paia.core.model.DocumentList;
import de.tu_dortmund.ub.api.paia.core.model.FeeList;
import de.tu_dortmund.ub.api.paia.core.model.Patron;
import de.tu_dortmund.ub.api.paia.model.RequestError;
import net.sf.saxon.s9api.*;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.transform.JDOMSource;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.util.HashMap;
import java.util.Properties;

/**
 * Created by cihabe on 06.05.2015.
 */
public class JaxbToHtml implements ObjectToHtmlTransformation {

    private Properties config = null;
    private Logger logger = null;

    @Override
    public void init(Properties properties) {

        this.config = properties;
        PropertyConfigurator.configure(this.config.getProperty("service.log4j-conf"));
        this.logger = Logger.getLogger(JaxbToHtml.class.getName());
    }

    @Override
    public Object transform(Object object) throws TransformationException {

        return this.transform(object, null);
    }

    @Override
    public Object transform(Object object, HashMap<String,String> parameters) throws TransformationException {

        String html = null;

        try {

            JAXBContext context = null;
            String xsltFile = "";

            if (object instanceof RequestError) {

                context = JAXBContext.newInstance(RequestError.class);
                xsltFile = this.config.getProperty("service.requesterror.xslt");
            }
            if (object instanceof Patron) {

                context = JAXBContext.newInstance(Patron.class);
                xsltFile = this.config.getProperty("service.endpoint.core.service.xslt");
            }
            if (object instanceof DocumentList) {

                context = JAXBContext.newInstance(DocumentList.class);
                xsltFile = this.config.getProperty("service.endpoint.core.service.xslt");
            }
            if (object instanceof FeeList) {

                context = JAXBContext.newInstance(FeeList.class);
                xsltFile = this.config.getProperty("service.endpoint.core.service.xslt");
            }
            if (object instanceof Document) {

                xsltFile = this.config.getProperty("service.endpoint.auth.login.xslt");
            }

            if (context != null) {
                Marshaller m = context.createMarshaller();
                m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);

                // Write to HttpResponse
                StringWriter stringWriter = new StringWriter();
                m.marshal(object, stringWriter);

                Document doc = new SAXBuilder().build(new StringReader(stringWriter.toString()));

                html = htmlOutputter(doc, xsltFile, parameters);
            }
            else if (object instanceof Document) {

                html = htmlOutputter((Document) object, xsltFile, parameters);
            }
        }
        catch (JAXBException | IOException | JDOMException e) {
            throw new TransformationException(e.getMessage(), e.getCause());
        }

        return html;
    }

    /**
     * This method transforms a XML document to HTML via a given XSLT stylesheet. It respects a map of additional parameters.
     *
     * @param doc
     * @param xslt
     * @param params
     * @return
     * @throws java.io.IOException
     */
    private String htmlOutputter(org.jdom2.Document doc, String xslt, HashMap<String,String> params) throws IOException {

        String result = null;

        try {

            // Init XSLT-Transformer
            Processor processor = new Processor(false);
            XsltCompiler xsltCompiler = processor.newXsltCompiler();
            XsltExecutable xsltExecutable = xsltCompiler.compile(new StreamSource(xslt));


            XdmNode source = processor.newDocumentBuilder().build(new JDOMSource( doc ));
            Serializer out = new Serializer();
            out.setOutputProperty(Serializer.Property.METHOD, "html");
            out.setOutputProperty(Serializer.Property.INDENT, "yes");

            StringWriter buffer = new StringWriter();
            out.setOutputWriter(new PrintWriter( buffer ));

            XsltTransformer trans = xsltExecutable.load();
            trans.setInitialContextNode(source);
            trans.setDestination(out);

            if (params != null) {
                for (String p : params.keySet()) {
                    trans.setParameter(new QName(p), new XdmAtomicValue(params.get(p)));
                }
            }

            trans.transform();

            result = buffer.toString();

        } catch (SaxonApiException e) {

            this.logger.error("SaxonApiException: " + e.getMessage());
        }

        return result;
    }
}
