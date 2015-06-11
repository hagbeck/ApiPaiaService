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

package de.tu_dortmund.ub.api.paia.core.ils;

import de.tu_dortmund.ub.api.paia.core.model.Patron;
import de.tu_dortmund.ub.api.paia.core.model.DocumentList;
import de.tu_dortmund.ub.api.paia.core.model.FeeList;

import java.util.Properties;

/**
 * @author Hans-Georg Becker
 * @version 0.9 (2015-06-07)
 */
public interface IntegratedLibrarySystem {

    /**
     *
     * @param properties
     */
    void init(Properties properties);

    Patron patron(String patronid, boolean extended) throws ILSException;

    DocumentList items(String patronid) throws ILSException;

    DocumentList items(String patronid, String type) throws ILSException;

    DocumentList items(String patronid, String type, String selection) throws ILSException;

    FeeList fees(String patronid) throws ILSException;

    DocumentList request(String patronid, DocumentList documentList) throws ILSException;

    DocumentList renew(String patronid, DocumentList documentList) throws ILSException;

    DocumentList cancel(String patronid, DocumentList documentList) throws ILSException;

    Patron change(String patronid, de.tu_dortmund.ub.api.paia.core.model.Patron patron) throws ILSException;

}
