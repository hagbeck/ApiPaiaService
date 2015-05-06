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
    <xsl:param name="service" select="''" />

    <xsl:template match="/">

        <html>

        <head>
            <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
            <script src="/bower_components/jquery/dist/jquery.min.js"></script>
            <!-- Bootstrap -->
            <link rel="stylesheet" href="/bower_components/bootstrap/dist/css/bootstrap.css" />
            <link rel="stylesheet" href="/bower_components/bootstrap/dist/css/bootstrap-theme.css" />
            <script src="/bower_components/bootstrap/dist/js/bootstrap.js"></script>
            <!-- Markdown HTML -->
            <link rel="stylesheet" href="/css/markdown.css" type="text/css" />

            <title>api@ubdo - Application Programming Interface of University Library Dortmund</title>

        </head>

        <body prefix="schema: http://schema.org/" typeOf="schema:WebPage">

            <!--<ldp-header active="home"></ldp-header>-->

        <!-- ub-header -->
        <div class="pull-right">
            <small><a href="//www.ub.tu-dortmund.de">Startseite</a> | <a href="//www.ub.tu-dortmund.de/index.html.en">English</a> <a href="//www.ub.tu-dortmund.de/index.html.en"><img src="//www.ub.tu-dortmund.de/images/flag-english.gif"/></a> | <a href="//www.ub.tu-dortmund.de/a-z.html">A bis Z</a> | <a href="//www.ub.tu-dortmund.de/uebersicht.html">Übersicht über alle Seiten</a> | <a href="//www.ub.tu-dortmund.de/faq/index.html">FAQ</a> </small>
            <!--<span class="label label-warning">Angemeldet als Max Mustermann</span>-->
        </div>

        <div>
            <p><img src="//www.ub.tu-dortmund.de/images/tu-logo.png" alt="TU Dortmund Logo" /></p>
            <p><img src="//www.ub.tu-dortmund.de/images/ub-schriftzug.jpg" alt="UB Dortmund Logo" /></p>
        </div>

        <p></p>

        <!-- Menu Variante 2 -->
        <nav class="navbar navbar-default">
            <div class="container-fluid">
                <div class="navbar-header">
                    <button type="button" class="navbar-toggle collapsed" data-toggle="collapse" data-target="#bs-example-navbar-collapse-1">
                        <span class="sr-only">Toggle navigation</span>
                        <span class="icon-bar"></span>
                        <span class="icon-bar"></span>
                        <span class="icon-bar"></span>
                    </button>
                </div>

                <div class="collapse navbar-collapse" id="bs-example-navbar-collapse-1">
                    <ul class="nav navbar-nav">
                        <li><a href="//www.ub.tu-dortmund.de/katalog/">Katalog plus</a></li>
                        <li><a href="//www.ub.tu-dortmund.de/webOPACClient/start.do?StartPage=userAccount">Ihr Konto</a></li>
                        <li><a href="//www.ub.tu-dortmund.de/literatursuche/index.htm">Literatursuche</a></li>
                        <li><a href="//www.ub.tu-dortmund.de/nutzung/index.html">Nutzung der Bibliothek</a></li>
                        <li><a href="//www.ub.tu-dortmund.de/angebote_nach_mass/index.html">Angebote nach Maß</a></li>
                        <li class="disabled active"><a href="//www.ub.tu-dortmund.de/information/index.html">Über uns</a></li>
                    </ul>
                </div>
            </div>
        </nav>

        <!-- ldp-header -->
        <p>
            <small>
                Sie sind hier: <a href="//www.tu-dortmund.de/">TU Dortmund</a> &gt;
                <a href="//www.ub.tu-dortmund.de">Universitätsbibliothek</a> &gt;
                <a href="//www.ub.tu-dortmund.de/information/index.html">Über uns</a> &gt;
                <a href="/index.html">api@ubdo</a> &gt;
                <a href="paia.html">PaiaService</a> &gt;
                Beispiel
            </small>
        </p>

        <!-- ldp-content -->
        <div class="container-fluid">
        <div class="row">
        <div class="col-sm-2 col-md-2">
            <ul class="nav nav-pills nav-stacked">
                <li role="presentation" class="active disabled"><a href="index.html">api@ubdo</a></li>
                <li role="presentation"><a href="index.html">Über</a></li>
                <li role="presentation"><a href="daia.html">DaiaService</a></li>
                <li role="presentation" class="active"><a href="paia.html">PaiaService</a></li>
                <li role="presentation"><a href="paaa.html">PaaaService</a></li>
                <li role="presentation"><a href="source.html">Open Source</a></li>
                <li role="presentation"><a href="contact.html">Kontakt / Über uns</a></li>
            </ul>
        </div>
        <div class="col-sm-10 col-md-10">

        <div property="schema:about" resource="http://data.ub.tu-dortmund.de/open/resource/service/api">

        <div class="panel panel-default">
        <!--
        <div class="panel-heading">
            <h1 class="text-center">data@ubdo<br /><small>Datenplattform der Universitätsbibliothek Dortmund</small></h1>
        </div>
        -->
        <div class="panel-body">

        <h1><a id="inhalt" name="inhalt"></a>Ihr Konto</h1>

        <h2>
            <xsl:if test="$service = 'patron'"><xsl:value-of select="'Benutzerdaten'" /></xsl:if>
            <xsl:if test="contains($service,'items')"><xsl:value-of select="'Ausleihen, Vormerkungen, Bereitstellungen'" /></xsl:if>
            <xsl:if test="$service = 'fees'"><xsl:value-of select="'Gebühren'" /></xsl:if>
        </h2>

        <xsl:if test="$service = 'patron'">
            <xsl:for-each select="//patron/*">
                <p><strong><xsl:value-of select="concat(name(.),': ')" /></strong><xsl:value-of select="." /></p>
            </xsl:for-each>
        </xsl:if>
        <xsl:if test="contains($service,'items')">
            <xsl:for-each select="//docs/doc">
                <xsl:for-each select="./*">
                    <p><strong><xsl:value-of select="concat(name(.),': ')" /></strong><xsl:value-of select="." /></p>
                </xsl:for-each>
                <hr/>
            </xsl:for-each>
        </xsl:if>
        <xsl:if test="$service = 'fees'">
            <xsl:for-each select="//fees/fee">
                <xsl:for-each select="./*">
                    <p><strong><xsl:value-of select="concat(name(.),': ')" /></strong><xsl:value-of select="." /></p>
                </xsl:for-each>
                <hr/>
            </xsl:for-each>
        </xsl:if>

        </div>
        </div>
        </div>
        </div>
        </div>
        </div>

            <!-- ldp-footer -->
            <!--<ldp-footer license="cc-by"></ldp-footer>-->
            <div class="text-right">
                <small><a href="//www.ub.tu-dortmund.de/Ueberuns/Oeffnungszeiten.html">Öffnungszeiten</a> | <a href="//www.ub.tu-dortmund.de/Ueberuns/Anfahrt.html">Adresse</a> | <a href="//www.ub.tu-dortmund.de/information/index.html#kontakt">Kontakt</a> | <a href="//www.ub.tu-dortmund.de/kurse/index.html">Führungen, Kurse</a> | <a href="//www.ub.tu-dortmund.de/Orgaplan/bereich.htm">Bereichsbibliotheken</a> | <a href="//www.ub.tu-dortmund.de/Ueberuns/datenschutzerklaerung.html">Datenschutzerklärung</a> | <a href="//www.ub.tu-dortmund.de/impressum.html">Impressum</a> | <a href="//www.ub.tu-dortmund.de/mail.html">E-Mail</a></small><br />
                <small>
                    <a href="//creativecommons.org/licenses/by/4.0/"><img src="//i.creativecommons.org/l/by/4.0/80x15.png" alt="Creative Commons License"/></a>
                    <span property="license" lang="en"> This work is licensed under a <a property="url" href="//creativecommons.org/licenses/by/4.0/">Creative Commons Attribution 4.0 International License</a> (CC BY 4.0).</span>
                </small>
                <br />
                <small><img src="//www.w3.org/Icons/valid-xhtml-rdfa-blue.gif"/></small>
            </div>

        </body>
        </html>

    </xsl:template>

</xsl:stylesheet>
