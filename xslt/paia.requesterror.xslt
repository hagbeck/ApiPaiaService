<?xml version="1.0" encoding="UTF-8"?>
<!--

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

-->

<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output method="html" doctype-system="about:legacy-compat" indent="yes" encoding="UTF-8" />

    <xsl:param name="lang" select="'de'" />
    <xsl:param name="redirectURL" select="''" />
    <xsl:param name="redirect_uri_params" select="''" />
    <xsl:param name="formURL" select="''" />
    <xsl:param name="username" select="''" />

    <xsl:variable name="titel" select="'Authentifizierung und Autorisierung'" />
    <xsl:variable name="titel-en" select="'Authentication and Authorization'" />
    <xsl:variable name="anwendung" select="'doris'" />
    <xsl:variable name="anwendung-en" select="'doris'" />

    <xsl:include href="http://www.ub.tu-dortmund.de/head.xsl"/> <!-- Variable 'titel' angeben -->
    <xsl:include href="http://www.ub.tu-dortmund.de/oben.xsl"/> <!-- Variable 'anwendung' angeben -->
    <xsl:include href="http://www.ub.tu-dortmund.de/unten.xsl"/>
    <xsl:include href="http://www.ub.tu-dortmund.de/head-en.xsl"/> <!-- Variable 'titel-en' angeben -->
    <xsl:include href="http://www.ub.tu-dortmund.de/oben-en.xsl"/> <!-- Variable 'anwendung-en' angeben -->
    <xsl:include href="http://www.ub.tu-dortmund.de/unten-en.xsl"/>

    <!-- habe: <xsl:variable name="actionURL" select="concat($formURL, '/paia/auth/authorize')" />-->

    <xsl:template match="/">

        <script type="text/javascript" src="https://www.ub.tu-dortmund.de/jquery/jquery-min.js"></script>

        <html>

            <script type="application/javascript">
                var language = &quot;<xsl:value-of select="$lang" />&quot;;
            </script>

            <xsl:call-template name="ubHeader"/>
            <body  onload="document.getElementById('account').focus();">
                <xsl:call-template name="ubBody"/>

                <div id="contentwrapper">

                    <div id="brotkrumenNavi">Sie sind hier:
                        <ul>
                            <li><a href="https://www.tu-dortmund.de/"><acronym title="Technische Universität">TU</acronym> Dortmund</a> &#62; </li>

                            <li><a href="https://www.ub.tu-dortmund.de/">Universitätsbibliothek</a> &#62; </li>

                            <li>Anmeldung</li>

                        </ul>
                    </div>

                    <!-- /opac -->

                    <!-- Beginn Seiteninhalt //-->
                    <div id="content">
                        <!-- <h1><a id="inhalt" name="inhalt"></a>Auswahl eines Autorisierungsdienst</h1> -->

                        <h1><a id="inhalt" name="inhalt"></a>Anmeldung</h1>

                        <p><span class="rahmen rot">Anmeldung fehlgeschlagen. Bitte überprüfen Sie Account und Passwort.</span></p>

                        <p id="date">04.05.15</p>
                    </div>
                    <!-- Ende Seiteninhalt //-->

                    <!-- /contentwrapper -->
                </div>

                <xsl:call-template name="ubFooter"/>
            </body>
        </html>

    </xsl:template>

</xsl:stylesheet>
